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
package org.talend.sdk.component.runtime.beam.spi.record.avro;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.apache.avro.Schema.Type.NULL;
import static org.apache.avro.Schema.Type.UNION;

import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.runtime.beam.spi.record.AvroSchemaBuilder;
import org.talend.sdk.component.runtime.manager.service.api.Unwrappable;

class Loader {

    static AvroRecordBuilder load() {
        try {
            if (ofNullable(AvroRecordBuilder.class.getClassLoader())
                    .orElseGet(ClassLoader::getSystemClassLoader)
                    .loadClass("org.apache.avro.Schema$Field")
                    .getDeclaredField("defaultValue")
                    .getType()
                    .getName()
                    .startsWith("org.codehaus.jackson.")) {
                return new Avro18Builder();
            }
        } catch (final NoSuchFieldException | ClassNotFoundException e) {
            // no-op
        }
        return new Avro19Builder();
    }
}

// enables to support avro 1.8 and 1.9
// /!\ not an API, an internal for avro portability!
public interface AvroRecordBuilder {

    org.apache.avro.Schema NULL_SCHEMA = org.apache.avro.Schema.create(org.apache.avro.Schema.Type.NULL);

    AvroRecordBuilder INSTANCE = Loader.load();

    org.apache.avro.Schema.Field toField(Schema.Entry entry, org.apache.avro.Schema schema);

    Object toDefault(org.apache.avro.Schema.Field field);

    default org.apache.avro.Schema toSchema(final Schema.Entry entry) {
        final org.apache.avro.Schema schema = doToSchema(entry);
        if (entry.isNullable() && schema.getType() != UNION) {
            return org.apache.avro.Schema.createUnion(asList(NULL_SCHEMA, schema));
        }
        if (!entry.isNullable() && schema.getType() == UNION) {
            return org.apache.avro.Schema
                    .createUnion(schema.getTypes().stream().filter(it -> it.getType() != NULL).collect(toList()));
        }
        return schema;
    }

    default org.apache.avro.Schema doToSchema(final Schema.Entry entry) {
        final Schema.Builder builder = new AvroSchemaBuilder().withType(entry.getType());
        switch (entry.getType()) {
        case ARRAY:
            ofNullable(entry.getElementSchema()).ifPresent(builder::withElementSchema);
            break;
        case RECORD:
            ofNullable(entry.getElementSchema()).ifPresent(s -> s.getEntries().forEach(builder::withEntry));
            break;
        default:
            // no-op
        }
        return Unwrappable.class.cast(builder.build()).unwrap(org.apache.avro.Schema.class);
    }
}
