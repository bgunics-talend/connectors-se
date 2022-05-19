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
package org.talend.components.common.stream.input.avro;

import static org.talend.components.common.stream.Constants.BIGDECIMAL;

import org.apache.avro.LogicalTypes;
import org.talend.components.common.stream.AvroHelper;
import org.talend.components.common.stream.Constants;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AvroToSchema {

    private final RecordBuilderFactory recordBuilderFactory;

    public Schema inferSchema(final org.apache.avro.Schema avroSchema) {
        final Schema.Builder builder = recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD);
        avroSchema.getFields().stream().map(this::inferAvroField).forEach(builder::withEntry);
        return builder.build();
    }

    private Schema.Entry inferAvroField(org.apache.avro.Schema.Field field) {
        final Schema.Entry.Builder builder = recordBuilderFactory.newEntryBuilder();
        builder.withName(field.name());
        org.apache.avro.Schema.Type type = AvroHelper.getFieldType(field);
        String logicalType = AvroHelper.getLogicalType(field);
        // handle NULLable field
        builder.withNullable(this.isNullable(field.schema()));
        switch (type) {
        case RECORD: {
            builder.withType(Schema.Type.RECORD);
            //
            final Schema.Builder subBuilder = recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD);
            org.apache.avro.Schema extractedSchema = AvroHelper.nonNullableType(field.schema());
            extractedSchema.getFields().stream().map(this::inferAvroField).forEach(subBuilder::withEntry);
            builder.withElementSchema(subBuilder.build());
        }
            break;
        case ENUM:
        case ARRAY:
            builder.withType(Schema.Type.ARRAY);
            org.apache.avro.Schema extractedSchema = AvroHelper
                    .nonNullableType(AvroHelper.nonNullableType(field.schema()).getElementType());
            final Schema innerSchema = this.inferInnerSchema(extractedSchema);
            builder.withElementSchema(innerSchema);

            break;
        case INT:
        case LONG:
            if (Constants.AVRO_LOGICAL_TYPE_DATE.equals(logicalType)
                    || Constants.AVRO_LOGICAL_TYPE_TIME_MILLIS.equals(logicalType)
                    || Constants.AVRO_LOGICAL_TYPE_TIMESTAMP_MILLIS.equals(logicalType)) {
                builder.withType(Schema.Type.DATETIME);
                break;
            }
        case STRING:
        case FLOAT:
        case DOUBLE:
        case BOOLEAN:
        case NULL:
            builder.withType(translateToRecordType(type));
            break;
        case BYTES:
        case FIXED:
            if (Constants.AVRO_LOGICAL_TYPE_DECIMAL.equals(logicalType)) {
                LogicalTypes.Decimal decimalType =
                        ((LogicalTypes.Decimal) AvroHelper.nonNullableType(field.schema()).getLogicalType());
                builder.withType(Schema.Type.STRING)
                        .withProp(Constants.STUDIO_TYPE, BIGDECIMAL)
                        .withProp(Constants.STUDIO_LENGTH, String.valueOf(decimalType.getPrecision()))
                        .withProp(Constants.STUDIO_PRECISION, String.valueOf(decimalType.getScale()));
            } else {
                builder.withType(Schema.Type.BYTES);
            }
            break;
        default:
            break;
        }
        return builder.build();
    }

    private boolean isNullable(final org.apache.avro.Schema schema) {
        boolean isNullable = org.apache.avro.Schema.Type.NULL.equals(schema.getType());
        if (!isNullable && org.apache.avro.Schema.Type.UNION.equals(schema.getType())) {
            isNullable = schema.getTypes().stream().anyMatch(this::isNullable);
        }
        return isNullable;
    }

    private Schema inferInnerSchema(final org.apache.avro.Schema schema) {
        final Schema.Type toType = this.translateToRecordType(schema.getType());
        final Schema.Builder schemaBuilder = recordBuilderFactory.newSchemaBuilder(toType);
        if (toType == Schema.Type.RECORD) {
            schema.getFields()
                    .stream() //
                    .map(this::inferAvroField) //
                    .forEach(schemaBuilder::withEntry);
        } else if (toType == Schema.Type.ARRAY) {
            final org.apache.avro.Schema elementType = schema.getElementType();
            final Schema innerSchema = this.inferInnerSchema(AvroHelper.nonNullableType(elementType));
            schemaBuilder.withElementSchema(innerSchema);
        }

        return schemaBuilder.build();
    }

    protected Schema.Type translateToRecordType(org.apache.avro.Schema.Type type) {
        switch (type) {
        case RECORD:
            return Schema.Type.RECORD;
        case ARRAY:
            return Schema.Type.ARRAY;
        case STRING:
            return Schema.Type.STRING;
        case BYTES:
        case FIXED:
            return Schema.Type.BYTES;
        case INT:
            return Schema.Type.INT;
        case LONG:
            return Schema.Type.LONG;
        case FLOAT:
            return Schema.Type.FLOAT;
        case DOUBLE:
            return Schema.Type.DOUBLE;
        case BOOLEAN:
            return Schema.Type.BOOLEAN;
        default:
            throw new IllegalStateException(String.format(Constants.ERROR_UNDEFINED_TYPE, type.name()));
        }
    }
}
