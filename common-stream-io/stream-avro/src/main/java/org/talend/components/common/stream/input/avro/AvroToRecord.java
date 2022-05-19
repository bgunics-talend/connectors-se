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

import static java.util.stream.Collectors.toList;
import static org.apache.avro.Schema.Type.FIXED;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.avro.LogicalTypes;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.generic.GenericRecord;
import org.talend.components.common.stream.AvroHelper;
import org.talend.components.common.stream.Constants;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.Schema.Entry;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

public class AvroToRecord {

    private final RecordBuilderFactory recordBuilderFactory;

    private Schema recordSchema;

    /** keep a cached Avro schema in case next record different from precedent */
    private org.apache.avro.Schema cachedAvroSchema;

    public AvroToRecord(RecordBuilderFactory recordBuilderFactory) {
        this.recordBuilderFactory = recordBuilderFactory;
    }

    public Schema inferSchema(final GenericRecord record) {
        return new AvroToSchema(this.recordBuilderFactory).inferSchema(record.getSchema());
    }

    public Record toRecord(final GenericRecord record) {
        if (record == null) {
            return null;
        }
        if (recordSchema == null || !(Objects.equals(record.getSchema(), this.cachedAvroSchema))) {
            this.cachedAvroSchema = record.getSchema();
            this.recordSchema = this.inferSchema(record);
        }
        return avroToRecord(record,
                record.getSchema().getFields(),
                recordBuilderFactory.newRecordBuilder(recordSchema),
                this.recordSchema);
    }

    private Record avroToRecord(final GenericRecord genericRecord, final List<org.apache.avro.Schema.Field> fields,
            final Record.Builder recordBuilder, final Schema tckSchema) {

        if (genericRecord == null) {
            return null;
        }
        for (org.apache.avro.Schema.Field field : fields) {
            final Object value = genericRecord.get(field.name());
            final Entry entry = tckSchema.getEntry(field.name());
            if (AvroToRecord.isArray(field.schema()) && value instanceof Collection) {
                final Collection<?> objects =
                        buildArrayField(field.schema(), (Collection<?>) value, entry.getElementSchema());
                recordBuilder.withArray(entry, objects);
            } else if (!entry.isNullable() || value != null) {
                buildField(field, value, recordBuilder, entry);
            }

        }
        final Record record = recordBuilder.build();
        return record;
    }

    private static boolean isArray(final org.apache.avro.Schema schema) {
        boolean isArray = org.apache.avro.Schema.Type.ARRAY == schema.getType();
        if (!isArray && org.apache.avro.Schema.Type.UNION == schema.getType()) {
            isArray = schema.getTypes()
                    .stream()
                    .map(org.apache.avro.Schema::getType)
                    .anyMatch(org.apache.avro.Schema.Type.ARRAY::equals);
        }
        return isArray;
    }

    private Collection<?> buildArrayField(final org.apache.avro.Schema schema, final Collection<?> value,
            final Schema elementSchema) {
        final org.apache.avro.Schema arraySchema = AvroHelper.nonNullableType(schema);
        final org.apache.avro.Schema arrayInnerType = AvroHelper.nonNullableType(arraySchema.getElementType());

        final Collection<?> objectArray;
        switch (arrayInnerType.getType()) {
        case RECORD:
            objectArray = ((Collection<GenericRecord>) value).stream()
                    .map(record -> avroToRecord(record,
                            arrayInnerType.getFields(), recordBuilderFactory.newRecordBuilder(elementSchema),
                            elementSchema))
                    .collect(Collectors.toList());
            break;
        case ARRAY:
            objectArray = value.stream().map(Collection.class::cast).map((Collection array) -> {
                final Collection<?> objects = buildArrayField(arrayInnerType, array, elementSchema.getElementSchema());
                return objects;
            }).collect(Collectors.toList());

            break;
        case STRING:
            objectArray = value.stream().map(Object::toString).collect(Collectors.toList());
            break;
        case BYTES:
            objectArray = ((Collection<ByteBuffer>) value).stream().map(ByteBuffer::array).collect(toList());
            break;
        case INT:
            objectArray = ((Collection<Integer>) value).stream().collect(toList());
            break;
        case FLOAT:
            objectArray = ((Collection<Float>) value).stream().collect(toList());
            break;
        case DOUBLE:
            objectArray = ((Collection<Double>) value).stream().collect(toList());
            break;
        case BOOLEAN:
            objectArray = ((Collection<Boolean>) value).stream().collect(toList());
            break;
        case LONG:
            objectArray = ((Collection<Long>) value).stream().collect(toList());
            break;
        case FIXED:
            objectArray = value.stream().map(obj -> {
                if (Constants.AVRO_LOGICAL_TYPE_DECIMAL.equals(arrayInnerType.getLogicalType().getName())) {
                    return new BigDecimal(new BigInteger(((GenericFixed) obj).bytes()),
                            ((LogicalTypes.Decimal) AvroHelper.nonNullableType(arrayInnerType).getLogicalType())
                                    .getScale());
                } else {
                    return ((java.nio.ByteBuffer) obj).array();
                }
            }).collect(Collectors.toList());
            break;
        default:
            throw new IllegalStateException(
                    String.format(Constants.ERROR_UNDEFINED_TYPE, arrayInnerType.getType().name()));
        }

        return objectArray;
    }

    protected void buildField(final org.apache.avro.Schema.Field field,
            final Object value,
            final Record.Builder recordBuilder,
            final Entry entry) {
        if (value == null) {
            return;
        }
        String logicalType = AvroHelper.getLogicalType(field);
        org.apache.avro.Schema.Type fieldType = AvroHelper.getFieldType(field);
        switch (fieldType) {
        case RECORD: {
            final Schema schema = entry.getElementSchema();
            final Record.Builder builder = recordBuilderFactory.newRecordBuilder(schema);
            recordBuilder.withRecord(entry,
                    avroToRecord((GenericRecord) value, ((GenericRecord) value).getSchema().getFields(), builder,
                            schema));
        }
            break;
        case ARRAY:
            if (value instanceof Collection<?>) {
                final Schema schema = entry.getElementSchema();
                final Record.Builder builder = recordBuilderFactory.newRecordBuilder(schema);
                final Collection<?> objects = buildArrayField(field.schema(), (Collection<?>) value, schema);
                builder.withArray(entry, objects);
            }
            break;
        case STRING:
            recordBuilder.withString(entry, value.toString());
            break;
        case BYTES:
        case FIXED:
            byte[] bytes = null;
            if (FIXED.equals(fieldType)) {
                bytes = ((GenericFixed) value).bytes();
            } else {
                bytes = ((java.nio.ByteBuffer) value).array();
            }
            if (Constants.AVRO_LOGICAL_TYPE_DECIMAL.equals(logicalType)) {
                BigDecimal decimal = new BigDecimal(new BigInteger(bytes),
                        ((LogicalTypes.Decimal) AvroHelper.nonNullableType(field.schema()).getLogicalType())
                                .getScale());
                recordBuilder.withString(entry, decimal.toPlainString());
            } else {
                recordBuilder.withBytes(entry, bytes);
            }
            break;
        case INT:
            int ivalue = (Integer) value;
            if (Constants.AVRO_LOGICAL_TYPE_DATE.equals(logicalType)
                    || Constants.AVRO_LOGICAL_TYPE_TIME_MILLIS.equals(logicalType)) {
                recordBuilder.withDateTime(entry,
                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(ivalue), ZoneOffset.UTC));
            } else {
                recordBuilder.withInt(entry, ivalue);
            }
            break;
        case FLOAT:
            recordBuilder.withFloat(entry, (Float) value);
            break;
        case DOUBLE:
            recordBuilder.withDouble(entry, (Double) value);
            break;
        case BOOLEAN:
            recordBuilder.withBoolean(entry, (Boolean) value);
            break;
        case LONG:
            long lvalue = (Long) value;
            if (Constants.AVRO_LOGICAL_TYPE_TIMESTAMP_MILLIS.equals(logicalType)) {
                recordBuilder.withDateTime(entry,
                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(lvalue), ZoneOffset.UTC));
            } else {
                recordBuilder.withLong(entry, lvalue);
            }
            break;
        default:
            throw new IllegalStateException(String.format(Constants.ERROR_UNDEFINED_TYPE, entry.getType().name()));
        }
    }
}
