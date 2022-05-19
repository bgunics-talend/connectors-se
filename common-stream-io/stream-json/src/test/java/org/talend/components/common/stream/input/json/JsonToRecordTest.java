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

import java.io.StringReader;

import java.util.Collection;
import java.util.Iterator;

import javax.json.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.talend.components.common.stream.output.json.RecordToJson;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.Schema.Entry;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.runtime.record.RecordBuilderFactoryImpl;

class JsonToRecordTest {

    final JsonObject jsonObject = Json
            .createObjectBuilder()
            .add("Hello", "World")
            .add("array", Json.createArrayBuilder().add("First"))
            .add("arrayOfObject",
                    Json
                            .createArrayBuilder()
                            .add(Json.createObjectBuilder().add("f1", "v1"))
                            .add(Json.createObjectBuilder().add("f1", "v2").add("f2", "v2f2").addNull("f3")))
            .add("arrayOfArray",
                    Json
                            .createArrayBuilder()
                            .add(Json.createArrayBuilder().add(20.0).add(30.0).add(40.0))
                            .add(Json.createArrayBuilder().add(11.0).add(12.0).add(13.0)))
            .add("subRecord", Json.createObjectBuilder().add("field_1", "val1").add("field_2", "val2"))
            .build();

    private JsonToRecord toRecord;

    private JsonToRecord toRecordDoubleOption;

    @BeforeAll
    static void initLog() {
        System.setProperty("org.slf4j.simpleLogger.log.org.talend.components.common.stream", "debug");
    }

    @BeforeEach
    void start() {
        start(false);
    }

    void start(final boolean forceDouble) {
        final RecordBuilderFactory recordBuilderFactory = new RecordBuilderFactoryImpl("test");
        this.toRecord = new JsonToRecord(recordBuilderFactory, forceDouble);
        toRecordDoubleOption = new JsonToRecord(recordBuilderFactory, true);
    }

    @Test
    void toRecord() {
        final Record record = toRecord.toRecord(this.jsonObject);
        Assertions.assertNotNull(record);

        final RecordToJson toJson = new RecordToJson();
        final JsonObject jsonResult = toJson.fromRecord(record);
        Assertions.assertNotNull(jsonResult);

        // object equals except for 'null' value
        Assertions.assertEquals(this.jsonObject.getString("Hello"), jsonResult.getString("Hello"));
        Assertions.assertEquals(this.jsonObject.getJsonArray("array"), jsonResult.getJsonArray("array"));
        Assertions.assertEquals(this.jsonObject.getJsonArray("arrayOfArray"), jsonResult.getJsonArray("arrayOfArray"));
        Assertions.assertEquals(this.jsonObject.getJsonObject("subRecord"), jsonResult.getJsonObject("subRecord"));

        final JsonArray array = this.jsonObject.getJsonArray("arrayOfObject");
        final JsonArray resultArray = jsonResult.getJsonArray("arrayOfObject");

        Assertions
                .assertEquals(array.get(0).asJsonObject().getString("f1"),
                        resultArray.get(0).asJsonObject().getString("f1"));
        Assertions
                .assertEquals(array.get(1).asJsonObject().getString("f1"),
                        resultArray.get(1).asJsonObject().getString("f1"));
        Assertions
                .assertEquals(array.get(1).asJsonObject().getString("f2"),
                        resultArray.get(1).asJsonObject().getString("f2"));
    }

    @Test
    void toRecordWithDollarChar() {
        JsonObject jsonWithDollarChar = getJsonObject(
                "{\"_id\": {\"$oid\": \"5e66158f6eddd6049f309ddb\"}, \"date\": {\"$date\": 1543622400000}, \"item\": \"Cake - Chocolate\", \"quantity\": 2.0, \"amount\": {\"$numberDecimal\": \"60\"}}");
        final Record record = toRecord.toRecord(jsonWithDollarChar);
        Assertions.assertNotNull(record);
        Assertions.assertNotNull(record.getRecord("_id").getString("oid"));
    }

    @Test
    void toRecordWithHyphen() {
        JsonObject jsonWithDollarChar = getJsonObject("{\"_id\": {\"Content-Type\" : \"text/plain\"}}");
        final Record record = toRecord.toRecord(jsonWithDollarChar);
        Assertions.assertNotNull(record);
        Assertions.assertNotNull("text/plain", record.getRecord("_id").getString("Content_Type"));
    }

    @Test
    void numberToRecord() {
        String source = "{\n \"aNumber\" : 7,\n \"aaa\" : [1, 2, 3]\n}";
        JsonObject json = getJsonObject(source);
        final Record record = toRecord.toRecord(json);

        String source2 = "{\n \"aNumber\" : 7,\n \"aaa\" : [1, 2, 3.1]\n}";
        JsonObject json2 = getJsonObject(source2);
        final Record record2 = toRecord.toRecord(json2);
        final Entry aaaEntry2 = findEntry(record2.getSchema(), "aaa");
        Assertions.assertNotNull(aaaEntry2);
        Assertions.assertEquals(Schema.Type.ARRAY, aaaEntry2.getType());
        Assertions.assertEquals(Schema.Type.DOUBLE, aaaEntry2.getElementSchema().getType());

        Assertions.assertNotNull(record);
        final Entry aaaEntry = findEntry(record.getSchema(), "aaa");
        Assertions.assertNotNull(aaaEntry);

        Assertions.assertEquals(Schema.Type.ARRAY, aaaEntry.getType());
        Assertions.assertEquals(Schema.Type.LONG, aaaEntry.getElementSchema().getType());

        final Entry aNumberEntry = findEntry(record.getSchema(), "aNumber");
        Assertions.assertEquals(Schema.Type.LONG, aNumberEntry.getType());

        RecordToJson toJson = new RecordToJson();
        final JsonObject jsonObject = toJson.fromRecord(record);
        Assertions.assertNotNull(jsonObject);

        final Record recordDouble = toRecordDoubleOption.toRecord(json);
        Assertions.assertNotNull(recordDouble);
        final Entry aaaEntryDouble = findEntry(recordDouble.getSchema(), "aaa");
        Assertions.assertNotNull(aaaEntry);

        Assertions.assertEquals(Schema.Type.ARRAY, aaaEntryDouble.getType());
        Assertions.assertEquals(Schema.Type.DOUBLE, aaaEntryDouble.getElementSchema().getType());

        final Entry aNumberEntryDouble = findEntry(recordDouble.getSchema(), "aNumber");
        Assertions.assertEquals(Schema.Type.DOUBLE, aNumberEntryDouble.getType());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void fieldAreNullable(final boolean forceDouble) {
        start(forceDouble);

        String source =
                "{\"a_string\" : \"string1\", \"a_long\" : 123, \"a_double\" : 123.123, \"a_boolean\" : true, \"an_object\" : {\"att_a\" : \"aaa\", \"att_b\" : \"bbb\"}, \"an_array\" : [\"aaa\", \"bbb\", \"ccc\"]}";
        JsonObject json = getJsonObject(source);
        final Record record = toRecord.toRecord(json);

        record.getSchema().getEntries().stream().forEach(e -> {
            Assertions.assertTrue(e.isNullable(), e.getName() + " of type " + e.getType() + " should be nullable.");
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void fieldAreNullable2(final boolean forceDouble) {
        start(forceDouble);

        String source =
                "{\"a_string\" : \"string1\", \"a_null\" : null, \"b_null\" : {},\"a_long\" : 123, \"a_double\" : 123.123, \"a_boolean\" : true, \"an_object\" : {\"att_a\" : \"aaa\", \"att_b\" : \"bbb\"}, \"an_array\" : [\"aaa\", \"bbb\", \"ccc\"]}";
        JsonObject json = getJsonObject(source);
        final Record record = toRecord.toRecord(json, null, true);
        Assertions.assertNotNull(record.getSchema().getEntry("a_null"));
        Assertions.assertNotNull(record.getSchema().getEntry("b_null"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void emptyRecordAsNullTest(final boolean cases) {
        start(cases);

        String source = "{\n" + "            \"url\": \"https://talend7247.zendesk.com/api/v2/tickets/11.json\",\n"
                + "            \"id\": 11,\n" + "            \"external_id\": null,\n" + "            \"via\": {\n"
                + "                \"channel\": \"web\",\n" + "                \"source\": {\n"
                + "                    \"from\": {},\n" + "                    \"to\": {},\n"
                + "                    \"rel\": null\n" + "                }\n" + "            },\n"
                + "            \"created_at\": \"2021-06-30T06:49:36Z\"\n" + "\t\t\t}";
        JsonObject json = getJsonObject(source);
        final Record record = toRecord.toRecord(json, null, cases);
        if (cases) {
            Assertions
                    .assertEquals("{\"from\":\"{}\",\"to\":\"{}\"}",
                            record.getRecord("via").getRecord("source").toString());
        } else {
            Assertions.assertEquals("{\"from\":{},\"to\":{}}", record.getRecord("via").getRecord("source").toString());
        }
        record.getSchema().getEntries().stream().forEach(e -> {
            Assertions.assertTrue(e.isNullable(), e.getName() + " of type " + e.getType() + " should be nullable.");
        });
    }

    @Test
    void arrayOfRecord() {
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add("arrayField", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("f1", "v1"))
                        .add(Json.createObjectBuilder().add("f1", "v2").add("f2", "f2Value2")))
                .build();
        final Record record = this.toRecord.toRecord(jsonObject);
        final Collection<Record> records = record.getArray(Record.class, "arrayField");
        Assertions.assertEquals(2, records.size());
        final Iterator<Record> recordIterator = records.iterator();
        final Record record1 = recordIterator.next();
        final Record record2 = recordIterator.next();
        Assertions.assertEquals("f2Value2", record2.getString("f2"));
        Assertions.assertEquals(record1.getSchema(), record2.getSchema());

        final Schema arrayElementSchema = record.getSchema().getEntry("arrayField").getElementSchema();
        Assertions.assertEquals(arrayElementSchema, record2.getSchema());
    }

    private Entry findEntry(Schema schema, String entryName) {
        return schema.getEntries().stream().filter((Entry e) -> entryName.equals(e.getName())).findFirst().orElse(null);
    }

    private JsonObject getJsonObject(String content) {
        try (JsonReader reader = Json.createReader(new StringReader(content))) {
            return reader.readObject();
        }
    }

    @Test
    void toRecord_TDI46518() {
        final String source = "{ \"array\": [{\"sub\":[] },{ \"sub\":[{\"field\": 102 }] }] }";
        JsonObject json = getJsonObject(source);
        final Record record = this.toRecord.toRecord(json);
        final Entry arrayField = record.getSchema().getEntry("array");
        Assertions.assertNotNull(arrayField);
        Assertions.assertEquals(Schema.Type.ARRAY, arrayField.getType());

        final Schema elementSchema = arrayField.getElementSchema();
        Assertions.assertNotNull(elementSchema);
        Assertions.assertEquals(Schema.Type.RECORD, elementSchema.getType());

        final Entry subField = elementSchema.getEntry("sub");
        Assertions.assertEquals(Schema.Type.ARRAY, subField.getType());
        final Schema subElementSchema = subField.getElementSchema();
        Assertions.assertEquals(Schema.Type.RECORD, subElementSchema.getType());
    }

    @Test
    void withEmptyArray() {
        final RecordBuilderFactory factory = new RecordBuilderFactoryImpl("test");
        final Entry e1 = factory.newEntryBuilder()
                .withType(Schema.Type.ARRAY)
                .withName("e1")
                .withNullable(true)
                .withElementSchema(factory.newSchemaBuilder(Schema.Type.INT).build())
                .build();

        final Entry e2 = factory.newEntryBuilder()
                .withType(Schema.Type.ARRAY)
                .withName("e2")
                .withNullable(true)
                .withElementSchema(factory.newSchemaBuilder(Schema.Type.RECORD)
                        .withEntry(factory.newEntryBuilder().withType(Schema.Type.STRING).withName("f1").build())
                        .build())
                .build();
        final Schema schema = factory.newSchemaBuilder(Schema.Type.RECORD)
                .withEntry(e1)
                .withEntry(e2)
                .build();
        final JsonToRecord jsonToRecord = new JsonToRecord(factory, true, schema, true);

        final JsonObject jsonObject1 = Json.createObjectBuilder()
                .add("e1", Json.createArrayBuilder())
                .add("e2", Json.createArrayBuilder())
                .build();
        final Record record1 = jsonToRecord.toRecord(jsonObject1);
        final Collection<Integer> record1e1 = record1.getArray(Integer.class, "e1");
        Assertions.assertTrue(record1e1.isEmpty());

        final Collection<Record> record1e2 = record1.getArray(Record.class, "e2");
        Assertions.assertTrue(record1e2.isEmpty());

        final JsonObject jsonObject2 = Json.createObjectBuilder()
                .add("e1", Json.createArrayBuilder().add(1).add(2).add(3))
                .add("e2", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("f1", "hello")))
                .build();
        final Record record2 = jsonToRecord.toRecord(jsonObject2, schema, true);
        final Collection<Integer> record2e1 = record2.getArray(Integer.class, "e1");
        Assertions.assertEquals(3, record2e1.size());
        Assertions.assertTrue(record2e1.contains(1));

        final Collection<Record> record2e2 = record2.getArray(Record.class, "e2");
        Assertions.assertEquals(1, record2e2.size());
        final Record e2Record = record2e2.iterator().next();
        Assertions.assertEquals("hello", e2Record.getString("f1"));

        final JsonObject jsonObject3 = Json.createObjectBuilder()
                .addNull("e1")
                .build();
        final Record record3 = jsonToRecord.toRecord(jsonObject3, schema);
        final Collection<Integer> record3e1 = record3.getArray(Integer.class, "e1");
        Assertions.assertTrue(record3e1.isEmpty());

        final Collection<Record> record3e2 = record3.getArray(Record.class, "e2");
        Assertions.assertTrue(record3e2.isEmpty());
    }
}