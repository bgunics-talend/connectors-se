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

import static org.apache.avro.Schema.Type.NULL;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.runtime.beam.spi.AvroRecordBuilderFactoryProvider;
import org.talend.sdk.component.runtime.beam.spi.record.AvroSchema;
import org.talend.sdk.component.runtime.record.RecordBuilderFactoryImpl;

class JsonToSchemaTest {

    @Test
    void inferSchema() throws IOException {
        final RecordBuilderFactory factory = new RecordBuilderFactoryImpl("test");
        final JsonObject object = this.load("heterogeneousArray2.json");
        final JsonToSchema toSchema = new JsonToSchema(factory, this::getNumberType, false);

        final Schema schema = toSchema.inferSchema(object);
        Assertions.assertNotNull(schema);
        final Schema.Entry data = this.getEntry(schema, "data");
        Assertions.assertNotNull(data);
        final Schema elementSchema = data.getElementSchema();

        final Schema.Entry dddEntry = this.getEntry(elementSchema, "ddd");
        Assertions.assertNotNull(dddEntry);
        Assertions.assertEquals(Schema.Type.STRING, dddEntry.getType());
    }

    @Test
    void inferEmptyArray() throws IOException {
        final RecordBuilderFactory factory = new RecordBuilderFactoryImpl("test");
        final JsonObject object = this.load("emptyArray.json");
        final JsonToSchema toSchema = new JsonToSchema(factory, this::getNumberType, false);

        final Schema schema = toSchema.inferSchema(object);
        Assertions.assertNotNull(schema);
        final Schema.Entry array = this.getEntry(schema, "emptyArray");
        Assertions.assertNotNull(array);
        Assertions.assertNotNull(array.getType());
    }

    @Test
    void inferEmptyArray1() throws IOException {
        final RecordBuilderFactory factory = new RecordBuilderFactoryImpl("test");
        final JsonObject object = this.load("emptyArray1.json");
        final JsonToSchema toSchema = new JsonToSchema(factory, this::getNumberType, false);

        final Schema schema = toSchema.inferSchema(object);
        Assertions.assertNotNull(schema);
        final Schema.Entry list = this.getEntry(schema, "list");
        Assertions.assertNotNull(list);
        Assertions.assertNotNull(list.getType());

        final Schema.Entry array = list.getElementSchema().getEntry("array");
        Assertions.assertNotNull(array);
        final Schema.Type arrayType = array.getElementSchema().getType();
        Assertions.assertNotNull(arrayType);
        Assertions.assertEquals(Schema.Type.BOOLEAN, arrayType);
    }

    @Test
    void inferBigSchema() throws IOException {
        final String property = System.getProperty("talend.component.beam.record.factory.impl");
        try {
            System.setProperty("talend.component.beam.record.factory.impl", "avro");
            final AvroRecordBuilderFactoryProvider provider = new AvroRecordBuilderFactoryProvider();
            final RecordBuilderFactory factory = provider.apply("test");
            final JsonObject object = this.load("vehicles.json");
            final JsonToSchema toSchema = new JsonToSchema(factory, this::getNumberType, false);
            toSchema.inferSchema(object);
            final long start = System.nanoTime();
            final Schema schema = toSchema.inferSchema(object);
            long duration = System.nanoTime() - start;
            System.out.println("guess schema duration : " + TimeUnit.NANOSECONDS.toMicros(duration) + " µs");

            final JsonToRecord toRecord = new JsonToRecord(factory);
            final long start2 = System.nanoTime();
            final Record record = toRecord.toRecord(object);
            long duration2 = System.nanoTime() - start2;
            System.out.println(
                    "guess schema + build object duration : " + TimeUnit.NANOSECONDS.toMicros(duration2) + " µs");

            final Collection<Record> vehicles = record.getArray(Record.class, "vehicles");
            final Schema elementSchema = record.getSchema().getEntry("vehicles").getElementSchema();
            org.apache.avro.Schema elementSchemaInner = unwrapUnion(((AvroSchema) elementSchema).getDelegate());
            boolean allEquals = vehicles.stream()
                    .map(Record::getSchema)
                    .map((AvroSchema.class::cast))
                    .allMatch((AvroSchema tab) -> Objects.equals(tab.getDelegate(), elementSchemaInner));
            Assertions.assertTrue(allEquals, "some are different");

        } finally {
            if (property == null) {
                System.clearProperty("talend.component.beam.record.factory.impl");
            } else {
                System.setProperty("talend.component.beam.record.factory.impl", property);
            }
        }

    }

    public static org.apache.avro.Schema unwrapUnion(final org.apache.avro.Schema schema) {
        switch (schema.getType()) {
        case UNION:
            return schema.getTypes().stream().filter(it -> it.getType() != NULL).findFirst().orElse(null);
        default:
            return schema;
        }
    }

    private JsonObject load(final String fileName) throws IOException {
        try (final InputStream inputStream =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName)) {
            return Json.createReader(inputStream).readObject();
        }
    }

    private Schema.Type getNumberType(JsonNumber number) {
        if (number.isIntegral()) {
            return Schema.Type.LONG;
        }
        return Schema.Type.DOUBLE;
    }

    private Schema.Entry getEntry(final Schema schema, final String name) {
        return schema.getEntries()
                .stream() //
                .filter((Schema.Entry e) -> name.equals(e.getName())) //
                .findFirst() //
                .orElse(null);
    }
}