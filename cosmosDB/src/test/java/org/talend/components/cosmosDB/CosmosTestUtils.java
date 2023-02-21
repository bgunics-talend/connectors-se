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

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.implementation.Document;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class CosmosTestUtils {

    private String databaseID;

    private String collectionID;

    CosmosClient client;

    public CosmosTestUtils(CosmosClient client, String databaseID, String collectionID) {
        this.databaseID = databaseID;
        this.collectionID = collectionID;
        this.client = client;
    }

    public void createDatabaseIfNotExists() {
        int status = client.createDatabaseIfNotExists(databaseID).getStatusCode();
        if (status != 201) {
            throw new RuntimeException("Failed to create database [" + databaseID + "] status: " + status);
        }
        /*
         * String databaseLink = String.format("/dbs/%s", databaseID);
         * // Check to verify a database with the id=FamilyDB does not exist
         * try {
         * client.readDatabase(databaseLink, null);
         * 
         * } catch (DocumentClientException de) {
         * // If the database does not exist, create a new database
         * if (de.getStatusCode() == 404) {
         * Database database = new Database();
         * database.setId(databaseID);
         * client.createDatabase(database, null);
         * } else {
         * throw de;
         * }
         * }
         */
    }

    public boolean isCollectionExist(String collectionID) {
        String collectionLink = String.format("/dbs/%s/colls/%s", databaseID, collectionID);
        int status = client.getDatabase(databaseID).getContainer(collectionID).read().getStatusCode();
        if (status == 200) {
            return true;
        } else if (status == 404) {
            log.warn("Collection [" + collectionID + "] does not exist, status: " + status);
            return false;
        } else {
            log.warn("Collection [" + collectionID + "] does not exist, status: " + status);
            throw new RuntimeException("Collection [" + collectionID + "] does not exist, status: " + status);
        }
        /*
         * try {
         * client.readCollection(collectionLink, null);
         * } catch (DocumentClientException e) {
         * if (e.getStatusCode() == 404) {
         * log.warn("Collection [" + collectionID + "] does not exist.");
         * return false;
         * } else {
         * throw e;
         * }
         * }
         * return true;
         */
    }

    public void createDocumentCollectionIfNotExists() throws IOException {
        log.info("Collection [" + collectionID + "] will be created.");
        int status =
                client.getDatabase(databaseID).createContainerIfNotExists(collectionID, "/lastName").getStatusCode();
        if (status != 201) {
            log.error("Failed to create collection [\" + collectionID + \"], status: " + status);
        } else {
            log.info("Collection [" + collectionID + "] created.");
        }
        /*
         * String databaseLink = String.format("/dbs/%s", databaseID);
         * if (!isCollectionExist(collectionID)) {
         * log.info("Collection [" + collectionID + "] will be created.");
         * DocumentCollection collectionInfo = new DocumentCollection();
         * collectionInfo.setId(collectionID);
         * RangeIndex index = new RangeIndex(DataType.String);
         * index.setPrecision(-1);
         * collectionInfo.setIndexingPolicy(new IndexingPolicy(new Index[] { index }));
         * PartitionKeyDefinition pkd = new PartitionKeyDefinition();
         * pkd.setPaths(Arrays.asList("/lastName"));
         * collectionInfo.setPartitionKey(pkd);
         * RequestOptions requestOptions = new RequestOptions();
         * requestOptions.setOfferThroughput(400);
         * client.createCollection(databaseLink, collectionInfo, requestOptions);
         * log.info("Collection [" + collectionID + "] created.");
         * }
         */
    }

    public JsonNode readDocuments(String collectionID, String documentID, String partitionKey) {
        CosmosContainer container = client.getDatabase(databaseID).getContainer(collectionID);
        CosmosItemResponse<JsonNode> resp =
                container.readItem(documentID, new PartitionKey(partitionKey), JsonNode.class);
        System.out.println("ReadDocuments status is: " + resp.getStatusCode());
        return resp.getItem();
        /*
         * String collectionLink = String.format("/dbs/%s/colls/%s/docs/%s", databaseID, collectionID, documentID);
         * RequestOptions requestOptions = new RequestOptions();
         * requestOptions.setPartitionKey(new PartitionKey(partitionKey));
         * requestOptions.setOfferThroughput(400);
         * ResourceResponse<Document> documentResourceResponse = client.readDocument(collectionLink, requestOptions);
         * return documentResourceResponse.getResource();
         */
    }

    public void deleteCollection(String collectionID) {
        int status = client.getDatabase(databaseID).getContainer(collectionID).delete().getStatusCode();
        if (status != 204) {
            log.error("There's a problem when delete collection [" + collectionID + "] , please delete it manually.");
            throw new RuntimeException("There's a problem when delete collection [" + collectionID + "]");
        }
    }

    public void deleteCollection() {
        deleteCollection(collectionID);
    }

    public void insertDocument(String json) {
        Document document = new Document(json);
        int status = client.getDatabase(databaseID).getContainer(collectionID).createItem(document).getStatusCode();
        if (status != 201)
            throw new RuntimeException("Failed to insert document, status: " + status);
    }

    public void dropDatabase() {
        int status = client.getDatabase(databaseID).delete().getStatusCode();
        if (status != 204) {
            log.error("Cannot Drop database: [" + databaseID + "] please deleted manually, status: " + status);
            this.deleteCollection();
            client.close();
            throw new RuntimeException("Cannot Drop database: [" + databaseID + "]");
        }
    }
}
