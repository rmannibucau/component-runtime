/**
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.sdk.component.runtime.beam.spi.record;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.talend.sdk.component.runtime.beam.avro.AvroSchemas.sanitizeConnectionName;
import static org.talend.sdk.component.runtime.beam.avro.AvroSchemas.unwrapUnion;
import static org.talend.sdk.component.runtime.beam.spi.record.SchemaIdGenerator.generateRecordName;

import java.nio.ByteBuffer;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.json.bind.annotation.JsonbTransient;

import org.apache.avro.generic.GenericArray;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.util.Utf8;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.runtime.beam.spi.record.avro.AvroRecordBuilder;
import org.talend.sdk.component.runtime.manager.service.api.Unwrappable;
import org.talend.sdk.component.runtime.record.RecordConverters;

public class AvroRecord implements Record, AvroPropertyMapper, Unwrappable {

    private static final RecordConverters RECORD_CONVERTERS = new RecordConverters();

    @JsonbTransient
    private final IndexedRecord delegate;

    @JsonbTransient
    private final AvroSchema schema;

    public AvroRecord(final IndexedRecord record) {
        this.schema = new AvroSchema(record.getSchema());
        this.delegate = record;
    }

    public AvroRecord(final Record record) {
        final List<Schema.Entry> entries = record.getSchema().getEntries();
        final List<org.apache.avro.Schema.Field> fields =
                entries.stream().map(entry -> AvroRecordBuilder.INSTANCE.toField(entry, null)).collect(toList());
        final org.apache.avro.Schema avroSchema =
                org.apache.avro.Schema.createRecord(generateRecordName(fields), null, null, false);
        avroSchema.setFields(fields);
        this.schema = new AvroSchema(avroSchema);
        this.delegate = new GenericData.Record(avroSchema);
        entries
                .forEach(entry -> ofNullable(record.get(Object.class, sanitizeConnectionName(entry.getName())))
                        .ifPresent(v -> {
                            Object avroValue = directMapping(v);
                            if (Collection.class.isInstance(avroValue)) {
                                avroValue = Collection.class
                                        .cast(avroValue)
                                        .stream()
                                        .map(this::directMapping)
                                        .collect(toList());
                            }
                            if (avroValue != null) {
                                final org.apache.avro.Schema.Field field =
                                        avroSchema.getField(sanitizeConnectionName(entry.getName()));
                                this.delegate.put(field.pos(), avroValue);
                            }
                        }));
    }

    private Object directMapping(final Object value) {
        if (Record.class.isInstance(value)) {
            return Unwrappable.class.cast(value).unwrap(IndexedRecord.class);
        }
        if (ZonedDateTime.class.isInstance(value)) {
            return ZonedDateTime.class.cast(value).toInstant().toEpochMilli();
        }
        if (Date.class.isInstance(value)) {
            return Date.class.cast(value).getTime();
        }
        if (byte[].class.isInstance(value)) {
            return ByteBuffer.wrap(byte[].class.cast(value));
        }
        return value;
    }

    @Override
    public Schema getSchema() {
        return schema;
    }

    @Override
    public <T> T get(final Class<T> expectedType, final String name) {
        if (expectedType == Collection.class) {
            return expectedType.cast(getArray(Object.class, name));
        }
        return doGet(expectedType, name);
    }

    @Override
    public <T> Collection<T> getArray(final Class<T> type, final String name) {
        final Collection<?> collection = doGet(Collection.class, name);
        if (collection == null) {
            return null;
        }
        final org.apache.avro.Schema elementType =
                unwrapUnion(delegate.getSchema().getField(name).schema()).getElementType();
        return doMapCollection(type, collection, elementType);
    }

    @Override
    public <T> T unwrap(final Class<T> type) {
        if (IndexedRecord.class.isAssignableFrom(type)) {
            return type.cast(delegate);
        }
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AvroRecord that = AvroRecord.class.cast(o);
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }

    private <T> Collection<T> doMapCollection(final Class<T> type, final Collection<?> collection,
            final org.apache.avro.Schema elementType) {
        return ofNullable(collection)
                .map(c -> c.stream().map(item -> doMap(type, elementType, item)).collect(toList()))
                .orElse(null);
    }

    private <T> T doGet(final Class<T> expectedType, final String name) {
        final org.apache.avro.Schema.Field field = delegate.getSchema().getField(sanitizeConnectionName(name));
        if (field == null) {
            return null;
        }
        final Object value = delegate.get(field.pos());
        final org.apache.avro.Schema schema = field.schema();
        return doMap(expectedType, unwrapUnion(schema), value);
    }

    private <T> T doMap(final Class<T> expectedType, final org.apache.avro.Schema fieldSchema, final Object value) {
        if (Boolean.parseBoolean(readProp(fieldSchema, Schema.Type.DATETIME.name())) && Long.class.isInstance(value)
                && expectedType != Long.class) {
            return RECORD_CONVERTERS.coerce(expectedType, value, fieldSchema.getName());
        }
        if (IndexedRecord.class.isInstance(value) && (Record.class == expectedType || Object.class == expectedType)) {
            return expectedType.cast(new AvroRecord(IndexedRecord.class.cast(value)));
        }
        if (GenericArray.class.isInstance(value) && !GenericArray.class.isAssignableFrom(expectedType)) {
            final Class<?> itemType = expectedType == Collection.class ? Object.class : expectedType;
            return expectedType
                    .cast(doMapCollection(itemType, Collection.class.cast(value), fieldSchema.getElementType()));
        }
        if (ByteBuffer.class.isInstance(value) && byte[].class == expectedType) {
            return expectedType.cast(ByteBuffer.class.cast(value).array());
        }
        if (!expectedType.isInstance(value)) {
            if (Utf8.class.isInstance(value) && String.class == expectedType) {
                return expectedType.cast(value.toString());
            }
            return RECORD_CONVERTERS.coerce(expectedType, value, fieldSchema.getName());
        }
        if (Utf8.class.isInstance(value) && Object.class == expectedType) {
            return expectedType.cast(value.toString());
        }
        return expectedType.cast(value);
    }

    @Override
    public String toString() {
        return "AvroRecord{delegate=" + delegate + '}';
    }
}
