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
package org.talend.components.adlsgen2.runtime.output;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.talend.components.adlsgen2.dataset.AdlsGen2DataSet;
import org.talend.components.adlsgen2.output.OutputConfiguration;
import org.talend.components.adlsgen2.service.AdlsGen2Service;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.runtime.record.RecordBuilderFactoryImpl;

class JsonBlobWriterTest {

    private Schema.Entry fArray;

    private Schema innerRecord;

    /**
     * Test https://jira.talendforge.org/browse/TDI-47462
     * test azure output work well when converting record in json
     * and record contains field of type "Array of records"
     */
    @Test
    void arrayOfRecordTest() {
        final OutputConfiguration config = new OutputConfiguration();
        AdlsGen2DataSet dataSet = new AdlsGen2DataSet();
        config.setDataSet(dataSet);
        dataSet.setBlobPath("the/path");
        config.setBlobNameTemplate("result");
        final RecordBuilderFactory factory = new RecordBuilderFactoryImpl("test");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        final AdlsGen2Service service = new AdlsGen2Service() {

            @Override
            public boolean blobExists(AdlsGen2DataSet dataSet, String blobName) {
                return false;
            }

            @Override
            public void pathUpdate(AdlsGen2DataSet dataSet, byte[] content, long position) {
                out.write(content, 0, content.length);
            }

            @Override
            public boolean pathCreate(AdlsGen2DataSet dataSet) {
                return true;
            }

            @Override
            public void flushBlob(AdlsGen2DataSet dataSet, long position) {
            }
        };
        final JsonBlobWriter writer = new JsonBlobWriter(config,
                factory,
                Json.createBuilderFactory(Collections.emptyMap()),
                service);

        writer.newBatch();

        final Schema schema = this.buildSchema(factory);
        for (int i = 1; i <= 3; i++) {
            final Record record = this.buildRecord(schema, factory, i);
            writer.writeRecord(record);
        }
        writer.flush();
        System.out.println(out.toString());
        final JsonArray array = Json.createReader(new ByteArrayInputStream(out.toByteArray()))
                .readArray();
        final JsonObject innerObject = array.getJsonObject(0).getJsonArray("farray").getJsonObject(0);
        Assertions.assertEquals("value Inner_1_1", innerObject.getString("inner"));
    }

    private Record buildRecord(final Schema schema,
            final RecordBuilderFactory factory,
            int index) {
        return factory.newRecordBuilder(schema)
                .withString("f1", "value_" + index)
                .withArray(this.fArray,
                        Arrays.asList(
                                factory.newRecordBuilder(this.innerRecord)
                                        .withString("inner", "value Inner_" + index + "_1")
                                        .build(),
                                factory.newRecordBuilder(this.innerRecord)
                                        .withString("inner", "value Inner_" + index + "_2")
                                        .build()))
                .build();
    }

    private Schema buildSchema(final RecordBuilderFactory factory) {
        final Schema.Entry inner = factory.newEntryBuilder()
                .withType(Schema.Type.STRING)
                .withName("inner")
                .build();
        this.innerRecord = factory.newSchemaBuilder(Schema.Type.RECORD)
                .withEntry(inner)
                .build();
        this.fArray = factory.newEntryBuilder()
                .withType(Schema.Type.ARRAY)
                .withName("farray")
                .withElementSchema(innerRecord)
                .build();
        final Schema.Entry f1 = factory.newEntryBuilder()
                .withType(Schema.Type.STRING)
                .withName("f1")
                .build();
        return factory.newSchemaBuilder(Schema.Type.RECORD)
                .withEntry(f1)
                .withEntry(fArray)
                .build();
    }
}