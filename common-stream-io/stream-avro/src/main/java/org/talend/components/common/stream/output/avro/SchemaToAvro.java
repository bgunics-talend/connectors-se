/*
 * Copyright (C) 2006-2022 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.talend.components.common.stream.output.avro;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.avro.LogicalTypes;
import org.apache.avro.SchemaBuilder;
import org.talend.sdk.component.api.record.Schema;

import static org.talend.components.common.stream.Constants.*;

public class SchemaToAvro {

    private static final String ERROR_UNDEFINED_TYPE = "Undefined type %s.";

    private static final String RECORD_NAME = "talend_";

    private final String currentRecordNamespace;

    public SchemaToAvro(String currentRecordNamespace) {
        this.currentRecordNamespace = currentRecordNamespace;
    }

    /**
     * Infer an Avro Schema from a Record Schema
     *
     * @param schema the Record schema
     * @return an Avro Schema
     */
    public org.apache.avro.Schema fromRecordSchema(final String schemaName, final Schema schema) {
        final List<org.apache.avro.Schema.Field> fields = new ArrayList<>();
        for (Schema.Entry e : schema.getEntries()) {
            final String name = e.getName();

            org.apache.avro.Schema builder = null;
            if (isDecimalType(e.getProps())) {
                builder = createDecimalSchema(name, e.getProp(STUDIO_LENGTH), e.getProp(STUDIO_PRECISION));
            } else if (Schema.Type.ARRAY.equals(e.getType())
                    && isDecimalType(e.getElementSchema().getProps())) {
                Schema elementSchema = e.getElementSchema();
                builder = org.apache.avro.Schema.createArray(createDecimalSchema(null,
                        elementSchema.getProp(STUDIO_LENGTH), elementSchema.getProp(STUDIO_PRECISION)));
            } else {
                builder = this.extractSchema(name, e.getType(), e.getElementSchema());
            }

            org.apache.avro.Schema unionWithNull;
            if (!e.isNullable()) {
                unionWithNull = builder;
            } else {
                unionWithNull = SchemaBuilder.unionOf().type(builder).and().nullType().endUnion();
            }
            org.apache.avro.Schema.Field field =
                    new org.apache.avro.Schema.Field(name, unionWithNull, e.getComment(), (Object) e.getDefaultValue());
            fields.add(field);
        }
        final String realName = schemaName == null ? this.buildSchemaId(schema) : schemaName;
        return org.apache.avro.Schema
                .createRecord(realName, "", currentRecordNamespace, false, fields);
    }

    private org.apache.avro.Schema createDecimalSchema(String name, String lengthStr, String precisionStr) {
        org.apache.avro.Schema builder = org.apache.avro.Schema.createFixed(name, null, null, 16);
        int length = 0;
        if (lengthStr != null && !lengthStr.isEmpty()) {
            length = Integer.parseInt(lengthStr);
        }
        if (precisionStr != null && !precisionStr.isEmpty()) {
            int scale = Integer.parseInt(precisionStr);
            LogicalTypes.decimal(length, scale).addToSchema(builder);
        } else {
            LogicalTypes.decimal(length).addToSchema(builder);
        }
        return builder;
    }

    private boolean isDecimalType(Map<String, String> props) {
        return props != null && props.get(STUDIO_TYPE) != null && BIGDECIMAL.equals(props.get(STUDIO_TYPE));
    }

    private org.apache.avro.Schema extractSchema(
            final String schemaName,
            final Schema.Type type,
            final Schema elementSchema) {
        final org.apache.avro.Schema extractedSchema;
        switch (type) {
        case RECORD:
            extractedSchema = fromRecordSchema(schemaName, elementSchema);
            break;
        case ARRAY:
            final org.apache.avro.Schema arrayType;
            if (elementSchema.getType() == Schema.Type.ARRAY) {
                final Schema subSchema = elementSchema.getElementSchema();
                final org.apache.avro.Schema subType = this.extractSchema(null, subSchema.getType(), subSchema);
                arrayType = org.apache.avro.Schema.createArray(subType);
            } else {
                arrayType = this.extractSchema(null, elementSchema.getType(), elementSchema);
            }
            final org.apache.avro.Schema nullableArrayType =
                    SchemaBuilder.unionOf().type(arrayType).and().nullType().endUnion();
            extractedSchema = org.apache.avro.Schema.createArray(nullableArrayType);
            break;
        case STRING:
        case BYTES:
        case INT:
        case LONG:
        case FLOAT:
        case DOUBLE:
        case BOOLEAN:
            final org.apache.avro.Schema.Type avroType = this.translateToAvroType(type);
            extractedSchema = org.apache.avro.Schema.create(avroType);
            break;
        case DATETIME:
            extractedSchema = buildDateTimeSchema();
            break;
        default:
            throw new IllegalStateException(String.format(ERROR_UNDEFINED_TYPE, type.name()));
        }

        return extractedSchema;
    }

    private static org.apache.avro.Schema buildDateTimeSchema() {
        org.apache.avro.Schema dateSchema = org.apache.avro.Schema.create(org.apache.avro.Schema.Type.LONG);
        LogicalTypes.timestampMillis().addToSchema(dateSchema);
        return dateSchema;
    }

    /**
     * Build an id that is same for equivalent schema independently of implementation.
     *
     * @param schema : schema.
     * @return id
     */
    private String buildSchemaId(Schema schema) {
        final List<String> fields = schema
                .getEntries()
                .stream()
                .map((Schema.Entry e) -> e.getName() + "_" + e.getType() + e.isNullable())
                .collect(Collectors.toList());
        return (RECORD_NAME + fields.hashCode()).replace('-', '1');
    }

    private org.apache.avro.Schema.Type translateToAvroType(final Schema.Type type) {
        switch (type) {
        case RECORD:
            return org.apache.avro.Schema.Type.RECORD;
        case ARRAY:
            return org.apache.avro.Schema.Type.ARRAY;
        case STRING:
            return org.apache.avro.Schema.Type.STRING;
        case BYTES:
            return org.apache.avro.Schema.Type.BYTES;
        case INT:
            return org.apache.avro.Schema.Type.INT;
        case LONG:
        case DATETIME:
            return org.apache.avro.Schema.Type.LONG;
        case FLOAT:
            return org.apache.avro.Schema.Type.FLOAT;
        case DOUBLE:
            return org.apache.avro.Schema.Type.DOUBLE;
        case BOOLEAN:
            return org.apache.avro.Schema.Type.BOOLEAN;
        }
        throw new IllegalStateException(String.format(ERROR_UNDEFINED_TYPE, type.name()));
    }
}
