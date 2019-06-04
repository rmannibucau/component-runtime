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
import static org.talend.sdk.component.runtime.beam.avro.AvroSchemas.sanitizeConnectionName;

import org.talend.sdk.component.api.record.Schema;

public class Avro19Builder implements AvroRecordBuilder {

    @Override
    public org.apache.avro.Schema.Field toField(final Schema.Entry entry, final org.apache.avro.Schema schema) {
        if (schema == null) {
            return new org.apache.avro.Schema.Field(entry.getName(), toSchema(entry), entry.getComment(),
                    entry.getDefaultValue());
        }
        return new org.apache.avro.Schema.Field(sanitizeConnectionName(entry.getName()),
                entry.isNullable() && schema.getType() != org.apache.avro.Schema.Type.UNION
                        ? org.apache.avro.Schema.createUnion(asList(NULL_SCHEMA, schema))
                        : schema,
                entry.getComment(), entry.getDefaultValue());
    }

    @Override
    public Object toDefault(final org.apache.avro.Schema.Field field) {
        return field.defaultVal();
    }
}
