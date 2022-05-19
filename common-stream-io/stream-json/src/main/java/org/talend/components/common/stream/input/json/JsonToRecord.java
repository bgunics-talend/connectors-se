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
package org.talend.components.common.stream.input.json;

import java.nio.charset.Charset;
import java.time.ZonedDateTime;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonToRecord {

    private final RecordBuilderFactory factory;

    private final NumberOption numberOption;

    private final boolean emptyRecordAsString;

    private final Schema givenSchema;

    public JsonToRecord(final RecordBuilderFactory factory) {
        this(factory, false, null, false);
    }

    public JsonToRecord(final RecordBuilderFactory factory, final boolean forceNumberAsDouble) {
        this(factory, forceNumberAsDouble, null, false);
    }

    public JsonToRecord(final RecordBuilderFactory factory,
            final boolean forceNumberAsDouble,
            final Schema schema,
            final boolean emptyRecordAsString) {
        this.factory = factory;
        if (forceNumberAsDouble) {
            this.numberOption = NumberOption.FORCE_DOUBLE_TYPE;
        } else {
            this.numberOption = NumberOption.INFER_TYPE;
        }
        this.givenSchema = schema;
        this.emptyRecordAsString = emptyRecordAsString;
    }

    /**
     * @deprecated : @use toRecord with same parameters.
     */
    @Deprecated
    public Record toRecordWithFixedSchema(final JsonObject object,
            final Schema schema,
            final boolean emptyRecordAsString) {
        return this.toRecord(object, schema, emptyRecordAsString);
    }

    /**
     * This method is a workaround to fit in dynamic schema into fixed schema
     * which the only way we have to make it work in TPD。
     *
     * @param object: The source jsonObject
     * @param schema : The fixed schema which will be apply into jsonObject.
     * @param emptyRecordAsString
     * Whether convert empty record to a String type "{}", as TPD does not allowed empty record {}
     */
    public Record toRecord(final JsonObject object,
            final Schema schema,
            final boolean emptyRecordAsString) {

        final JsonToRecord toRecord = new JsonToRecord(this.factory,
                this.numberOption == NumberOption.FORCE_DOUBLE_TYPE, schema, emptyRecordAsString);
        return toRecord.toRecord(object);
    }

    public Record toRecord(final JsonObject object,
            final Schema schema) {
        final JsonToRecord toRecord = new JsonToRecord(this.factory,
                this.numberOption == NumberOption.FORCE_DOUBLE_TYPE, schema, this.emptyRecordAsString);
        return toRecord.toRecord(object);
    }

    /**
     * Convert json object to record (with guessing schema).
     *
     * @param json : json data.
     * @return data in record format.
     */
    public Record toRecord(final JsonObject json) {
        if (json == null) {
            return null;
        }
        if (emptyRecordAsString && json.size() == 0) {
            return null;
        }
        final Schema schema;
        if (this.givenSchema != null) {
            schema = this.givenSchema;
        } else {
            final JsonToSchema schemaBuilder =
                    new JsonToSchema(this.factory, this.numberOption::getNumberType, emptyRecordAsString);
            schema = schemaBuilder.inferSchema(json);
        }
        return convertJsonObjectToRecord(schema, json);
    }

    private Record convertJsonObjectToRecord(final Schema schema, final JsonObject json) {
        final Record.Builder builder = this.factory.newRecordBuilder(schema);
        schema.getAllEntries().forEach((Schema.Entry entry) -> this.integrateEntryToRecord(entry, builder, json));

        return builder.build();
    }

    private String getElementName(final Schema.Entry entry) {
        // not use entry.getName() here as "$oid" will be correct to "oid"
        // comment store "$oid", so use comment here
        return entry.getComment() != null ? entry.getComment() : entry.getOriginalFieldName();
    }

    private void integrateEntryToRecord(final Schema.Entry entry, final Record.Builder builder, final JsonObject json) {
        if ((!json.containsKey(getElementName(entry)) || json.isNull(getElementName(entry)))
                && (!this.emptyRecordAsString)) {
            return;
        }
        final String jsonName = this.getElementName(entry);
        switch (entry.getType()) {
        case RECORD: {
            final JsonObject jsonObject = json.getJsonObject(jsonName);
            final Record record = convertJsonObjectToRecord(entry.getElementSchema(), jsonObject);
            builder.withRecord(entry, record);
        }
            break;
        case ARRAY:
            final List<?> objects = (!json.containsKey(jsonName) || json.isNull(jsonName)) ? null
                    : convertJsonArray(null, entry, json.getJsonArray(jsonName));
            if (objects != null) {
                builder.withArray(entry, objects);
            } else if (this.emptyRecordAsString) {
                builder.withArray(entry, Collections.emptyList());
            }
            break;
        case STRING: {
            final JsonValue jsonValue = json.get(jsonName);
            if (jsonValue != null && jsonValue.getValueType() == JsonValue.ValueType.STRING) {
                builder.withString(entry, ((JsonString) jsonValue).getString());
            } else if ((jsonValue == null || jsonValue.getValueType() == JsonValue.ValueType.OBJECT)
                    && this.emptyRecordAsString) {
                builder.withString(entry, "{}");
            }
        }
            break;
        case INT: {
            int value = json.getInt(jsonName);
            builder.withInt(entry, value);
        }
            break;
        case LONG: {
            long value = json.getJsonNumber(jsonName).longValue();
            builder.withLong(entry, value);
        }
            break;
        case FLOAT:
        case DOUBLE: {
            double value = json.getJsonNumber(jsonName).doubleValue();
            builder.withDouble(entry, value);
        }
            break;
        case BOOLEAN: {
            boolean value = json.getBoolean(jsonName);
            builder.withBoolean(entry, value);
        }
            break;
        case BYTES: {
            final String value = json.getString(jsonName);
            builder.withBytes(entry, value.getBytes(Charset.defaultCharset()));
        }
            break;
        case DATETIME: {
            final String value = json.getString(getElementName(entry));
            builder.withDateTime(entry, ZonedDateTime.parse(value));
        }
            break;
        default:
            log.warn("Unexpected TCK Type " + entry.getType());
        }
    }

    /**
     * Extract list of record format element from json array.
     *
     * @param schema : schema of json array element.
     * @param json : json array.
     * @return list of value.
     */
    private List<? extends Object> convertJsonArray(final Schema schema, final Schema.Entry entry,
            final JsonArray json) {

        final List<? extends Object> result;
        final Schema elementSchema = entry != null ? entry.getElementSchema() : schema.getElementSchema();
        switch (elementSchema.getType()) {
        case RECORD:
            result = json.stream()
                    .map((JsonValue v) -> convertJsonObjectToRecord(elementSchema, v.asJsonObject()))
                    .collect(Collectors.toList());
            break;
        case ARRAY:
            result = json.stream()
                    .map((JsonValue v) -> convertJsonArray(elementSchema, null, v.asJsonArray()))
                    .collect(Collectors.toList());

            break;
        case STRING:
            result = json.stream().map(this::forceToString).collect(Collectors.toList());
            break;
        case LONG:
            result = json.stream().map(JsonNumber.class::cast).map(JsonNumber::longValue).collect(Collectors.toList());
            break;
        case INT:
            result = json.stream().map(JsonNumber.class::cast).map(JsonNumber::intValue).collect(Collectors.toList());
            break;
        case DOUBLE:
            result = json.stream()
                    .map(JsonNumber.class::cast)
                    .map(JsonNumber::doubleValue)
                    .collect(Collectors.toList());
            break;
        case BOOLEAN:
            result = json.stream().map(JsonValue.TRUE::equals).collect(Collectors.toList());
            break;
        default: {
            result = null;
        }
        }

        return result;
    }

    private String forceToString(final JsonValue value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonString) {
            return ((JsonString) value).getString();
        }
        return String.valueOf(value);
    }

    private enum NumberOption {

        FORCE_DOUBLE_TYPE {

            public Schema.Type getNumberType(JsonNumber number) {
                return Schema.Type.DOUBLE;
            }
        },
        INFER_TYPE {

            public Schema.Type getNumberType(JsonNumber number) {
                if (number.isIntegral()) {
                    return Schema.Type.LONG;
                }
                return Schema.Type.DOUBLE;
            }
        };

        public abstract Schema.Type getNumberType(JsonNumber number);
    }

}
