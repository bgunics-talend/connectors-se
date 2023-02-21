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
package org.talend.components.cosmosDB.output;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.implementation.Document;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import org.apache.commons.lang3.StringUtils;
import org.talend.sdk.component.api.record.Record;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OutputParserFactory {

    final String databaseName;

    final String collectionName;

    final CosmosDBOutputConfiguration configuration;

    CosmosClient client;

    public OutputParserFactory(final CosmosDBOutputConfiguration configuration, CosmosClient client) {
        this.configuration = configuration;
        this.client = client;
        databaseName = configuration.getDataset().getDatastore().getDatabaseID();
        collectionName = configuration.getDataset().getCollectionID();
    }

    public IOutputParser getOutputParser() {
        switch (configuration.getDataAction()) {
        case INSERT:
            return new Insert();
        case DELETE:
            return new Delete();
        case UPDATE:
            return new Update();
        case UPSERT:
            return new Upsert();
        default:
            return null;
        }
    }

    public String getJsonString(Record record) {
        String delegate = record.toString();
        log.debug("delegate: " + delegate);
        if (delegate.startsWith("AvroRecord")) {
            // To avoid import dependence of AvroRecord
            return delegate.substring(20, delegate.length() - 1);
        } else if ("*".equals(record.getSchema().getEntries().get(0).getOriginalFieldName())) {
            delegate = record.getString(record.getSchema().getEntries().get(0).getName());
        }
        return delegate;
    }

    interface IOutputParser {

        void output(Record record);
    }

    class Insert implements IOutputParser {

        boolean disAbleautoID = !configuration.isAutoIDGeneration();

        @Override
        public void output(Record record) {
            String jsonString = getJsonString(record);
            try {
                Document document = new Document(jsonString);
                // TODO handle AutoID checkbox
                client.getDatabase(databaseName)
                        .getContainer(collectionName)
                        .createItem(document, new CosmosItemRequestOptions());
                // client.createDocument(collectionLink, document, new RequestOptions(), disAbleautoID);
            } catch (Exception e) { // TODO
                throw new IllegalArgumentException(e);
            }
        }
    }

    class Delete implements IOutputParser {

        String partitionKey;

        Delete() {
            String partitionKeyForDelete = configuration.getPartitionKeyForDelete();
            if (StringUtils.isNotEmpty(partitionKeyForDelete)) {
                partitionKey = partitionKeyForDelete.startsWith("/") ? partitionKeyForDelete.substring(1)
                        : partitionKeyForDelete;
            }
        }

        @Override
        public void output(Record record) {
            String id = record.getString("id");
            try {
                client.getDatabase(databaseName)
                        .getContainer(collectionName)
                        .deleteItem(id,
                                getPartitionKey(record), null);
            } catch (Exception e) { //
                throw new IllegalArgumentException(e);
            }
        }

        public PartitionKey getPartitionKey(Record record) {
            if (StringUtils.isNotEmpty(partitionKey)) {
                // TODO support complex partition key
                return new PartitionKey(record.get(Object.class, partitionKey));
                // partitionKey1.setPartitionKey(new PartitionKey(record.get(Object.class, partitionKey)));
            }
            return PartitionKey.NONE;
        }
    }

    class Update implements IOutputParser {

        @Override
        public void output(Record record) {
            String id = record.getString("id");
            final String documentLink = String.format("/dbs/%s/colls/%s/docs/%s", databaseName, collectionName, id);
            String jsonString = getJsonString(record);
            try {
                client.getDatabase(databaseName)
                        .getContainer(collectionName)
                        .replaceItem(new Document(jsonString), id, null, null);
                // client.replaceDocument(documentLink, new Document(jsonString), new RequestOptions());
            } catch (Exception e) { // TODO
                throw new IllegalArgumentException(e);
            }
        }
    }

    class Upsert implements IOutputParser {

        boolean disAbleautoID = !configuration.isAutoIDGeneration();

        @Override
        public void output(Record record) {
            String jsonString = getJsonString(record);
            try {
                client.getDatabase(databaseName).getContainer(collectionName).upsertItem(new Document(jsonString));
                // client.upsertDocument(collectionLink, new Document(jsonString), new RequestOptions(), disAbleautoID);
            } catch (Exception e) { // TODO
                throw new IllegalArgumentException(e);
            }
        }
    }
}
