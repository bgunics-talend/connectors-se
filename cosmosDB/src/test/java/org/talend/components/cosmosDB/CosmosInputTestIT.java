/*
 * Copyright (C) 2006-2023 Talend Inc. - www.talend.com
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
package org.talend.components.cosmosDB;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.talend.components.cosmosDB.input.CosmosDBInput;
import org.talend.components.cosmosDB.input.CosmosDBInputConfiguration;
import org.talend.components.cosmosDB.input.SchemaInfo;
import org.talend.sdk.component.api.record.Record;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CosmosInputTestIT extends CosmosDbTestBase {

    CosmosDBInputConfiguration config;

    @Before
    public void prepare() {
        super.prepare();
        config = new CosmosDBInputConfiguration();
    }

    @Test
    public void QueryTest() {
        dataSet.setUseQuery(true);
        dataSet
                .setQuery(
                        "SELECT {\"Name\":f.id, \"City\":f.address.city} AS Family\n" + " FROM " + collectionID + " f\n"
                                + " WHERE f.address.city = f.address.state");
        config.setDataset(dataSet);
        CosmosDBInput input = new CosmosDBInput(config, service, recordBuilderFactory);
        input.init();
        Record next = input.next();
        System.out.println(next);
        input.release();
        Assertions.assertEquals("Wakefield.7", next.getRecord("Family").getString("Name"));

    }

    @Test
    public void withoutQueryTest() {
        dataSet.setUseQuery(false);
        dataSet
                .setQuery("SELECT {\"Name\":f.id, \"City\":f.address.city} AS Family   FROM " + collectionID
                        + " f  WHERE f.address.county = \"Manhattan\"");
        config.setDataset(dataSet);
        CosmosDBInput input = new CosmosDBInput(config, service, recordBuilderFactory);
        input.init();
        Record next = input.next();
        System.out.println(next.toString());
        input.release();
        Assertions.assertNotNull(next);
    }

    @Test
    public void QueryTestToRaw() {
        dataSet.setUseQuery(true);
        dataSet
                .setQuery(
                        "SELECT {\"Name\":f.id, \"City\":f.address.city} AS Family\n" + " FROM " + collectionID + " f\n"
                                + " WHERE f.address.city = f.address.state");
        config.setDataset(dataSet);
        SchemaInfo column = new SchemaInfo();
        column.setOriginalDbColumnName("*");
        column.setLabel("doc");
        List<SchemaInfo> schema = new ArrayList<SchemaInfo>();
        schema.add(column);
        config.setStudioSchema(schema);
        CosmosDBInput input = new CosmosDBInput(config, service, recordBuilderFactory);
        input.init();
        Record next = input.next();
        log.info(next.toString());
        input.release();

        JsonReader reader = Json.createReader(new StringReader(next.getString("doc")));
        JsonObject jsonObject = reader.readObject();

        Assertions.assertEquals("Wakefield.7", jsonObject.getJsonObject("Family").getString("Name"));

    }

}
