/*
 * Copyright (C) 2006-2021 Talend Inc. - www.talend.com
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
package org.talend.components.couchbase.service;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.talend.components.couchbase.configuration.ConfigurationConstants.DETECT_SCHEMA;
import static org.talend.components.couchbase.configuration.ConfigurationConstants.DISCOVER_SCHEMA;
import static org.talend.sdk.component.api.record.Schema.Type.ARRAY;
import static org.talend.sdk.component.api.record.Schema.Type.BOOLEAN;
import static org.talend.sdk.component.api.record.Schema.Type.DATETIME;
import static org.talend.sdk.component.api.record.Schema.Type.DOUBLE;
import static org.talend.sdk.component.api.record.Schema.Type.FLOAT;
import static org.talend.sdk.component.api.record.Schema.Type.INT;
import static org.talend.sdk.component.api.record.Schema.Type.LONG;
import static org.talend.sdk.component.api.record.Schema.Type.RECORD;
import static org.talend.sdk.component.api.record.Schema.Type.STRING;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.components.couchbase.configuration.ConnectionParameter;
import org.talend.components.couchbase.dataset.CouchbaseDataSet;
import org.talend.components.couchbase.datastore.CouchbaseDataStore;
import org.talend.components.couchbase.source.CouchbaseInput;
import org.talend.components.couchbase.source.CouchbaseInputConfiguration;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.exception.ComponentException;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.completion.SuggestionValues;
import org.talend.sdk.component.api.service.completion.Suggestions;
import org.talend.sdk.component.api.service.healthcheck.HealthCheck;
import org.talend.sdk.component.api.service.healthcheck.HealthCheckStatus;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.api.service.schema.DiscoverSchema;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.document.json.JsonArray;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment.Builder;
import com.couchbase.client.java.error.InvalidPasswordException;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Version(1)
@Slf4j
@Service
public class CouchbaseService implements Serializable {

    private static final transient Logger LOG = LoggerFactory.getLogger(CouchbaseService.class);

    private final Map<CouchbaseDataStore, ClusterHolder> clustersPool = new ConcurrentHashMap<>();

    @Service
    private I18nMessage i18n;

    @Service
    private RecordBuilderFactory builderFactory;

    public String[] resolveAddresses(String nodes) {
        String[] addresses = nodes.replaceAll(" ", "").split(",");
        for (int i = 0; i < addresses.length; i++) {
            LOG.info(i18n.bootstrapNodes(i, addresses[i]));
        }
        return addresses;
    }

    public Cluster openConnection(CouchbaseDataStore dataStore) {
        String bootStrapNodes = dataStore.getBootstrapNodes();
        String username = dataStore.getUsername();
        String password = dataStore.getPassword();

        String[] urls = resolveAddresses(bootStrapNodes);
        try {
            ClusterHolder holder = clustersPool.computeIfAbsent(dataStore, ds -> {
                Builder envBuilder = new DefaultCouchbaseEnvironment.Builder();
                if (dataStore.isUseConnectionParameters()) {
                    dataStore
                            .getConnectionParametersList()
                            .forEach(conf -> setTimeout(envBuilder, conf.getParameterName(),
                                    parseValue(conf.getParameterValue())));
                }
                CouchbaseEnvironment environment = envBuilder.build();
                Cluster cluster = CouchbaseCluster.create(environment, urls);
                cluster.authenticate(username, password);
                return new ClusterHolder(environment, cluster);
            });
            holder.use();
            Cluster cluster = holder.getCluster();
            String clusterName = cluster.clusterManager().info().raw().get("name").toString();
            LOG.debug(i18n.connectedToCluster(clusterName));
            return cluster;
        } catch (Exception e) {
            LOG.error(i18n.connectionKO());
            throw new ComponentException(e);
        }

    }

    private void setTimeout(Builder envBuilder, ConnectionParameter parameterName, long value) {
        if (parameterName == ConnectionParameter.CONNECTION_TIMEOUT) {
            envBuilder.connectTimeout(value);
        } else if (parameterName == ConnectionParameter.QUERY_TIMEOUT) {
            envBuilder.queryTimeout(value);
        } else if (parameterName == ConnectionParameter.ANALYTICS_TIMEOUT) {
            envBuilder.analyticsTimeout(value);
        } else {
            envBuilder.maxRequestLifetime(value);
        }
    }

    private long parseValue(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ComponentException(i18n.unexpectedValue(value));
        }
    }

    @HealthCheck("healthCheck")
    public HealthCheckStatus
            healthCheck(@Option("configuration.dataset.connection") final CouchbaseDataStore datastore) {
        try {
            openConnection(datastore);
            return new HealthCheckStatus(HealthCheckStatus.Status.OK, "Connection OK");
        } catch (Exception exception) {
            String message = "";
            if (exception.getCause() instanceof InvalidPasswordException) {
                message = i18n.invalidPassword();
            } else if (exception.getCause() instanceof RuntimeException
                    && exception.getCause().getCause() instanceof TimeoutException) {
                message = i18n.destinationUnreachable();
            } else {
                message = i18n.connectionKODetailed(exception.getMessage());
            }
            LOG.error(message, exception);
            return new HealthCheckStatus(HealthCheckStatus.Status.KO, message);
        } finally {
            closeConnection(datastore);
        }
    }

    @DiscoverSchema(DISCOVER_SCHEMA)
    public Schema addColumns(@Option("dataSet") final CouchbaseDataSet dataSet) {
        CouchbaseInputConfiguration configuration = new CouchbaseInputConfiguration();
        configuration.setDataSet(dataSet);
        CouchbaseInput couchbaseInput = new CouchbaseInput(configuration, this, builderFactory, i18n);
        couchbaseInput.init();
        Record record = couchbaseInput.next();
        couchbaseInput.release();

        return record.getSchema();
    }

    @Suggestions(DETECT_SCHEMA)
    public SuggestionValues listColumns(@Option("datastore") final CouchbaseDataStore datastore,
            @Option("bucket") String bucketName) {
        CouchbaseDataSet dataset = new CouchbaseDataSet();
        dataset.setDatastore(datastore);
        dataset.setBucket(bucketName);
        try {
            Schema schema = addColumns(dataset);
            return new SuggestionValues(true,
                    schema
                            .getEntries()
                            .stream()
                            .map(e -> new SuggestionValues.Item(e.getName(), e.getName()))
                            .collect(toList()));
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
        return new SuggestionValues(false, emptyList());
    }

    public Bucket openBucket(Cluster cluster, String bucketName) {
        Bucket bucket;
        try {
            bucket = cluster.openBucket(bucketName);
        } catch (Exception e) {
            LOG.error(i18n.cannotOpenBucket());
            throw new ComponentException(e);
        }
        return bucket;
    }

    public void closeBucket(Bucket bucket) {
        if (bucket != null) {
            if (Boolean.TRUE.equals(bucket.close())) {
                LOG.debug(i18n.bucketWasClosed(bucket.name()));
            } else {
                LOG.debug(i18n.cannotCloseBucket(bucket.name()));
            }
        }
    }

    public void closeConnection(CouchbaseDataStore ds) {
        ClusterHolder holder = clustersPool.get(ds);
        if (holder == null) {
            return;
        }
        int stillUsed = holder.release();
        if (stillUsed > 0) {
            return;
        }
        clustersPool.remove(ds);
        Cluster cluster = holder.getCluster();
        CouchbaseEnvironment environment = holder.getEnv();
        if (cluster != null) {
            if (Boolean.TRUE.equals(cluster.disconnect())) {
                log.debug(i18n.clusterWasClosed());
            } else {
                log.debug(i18n.cannotCloseCluster());
            }
        }
        if (environment != null) {
            if (environment.shutdown()) {
                log.debug(i18n.couchbaseEnvWasClosed());
            } else {
                log.debug(i18n.cannotCloseCouchbaseEnv());
            }
        }
    }

    public Schema getSchema(JsonObject jsonObject, Set<String> jsonKeys) {
        Schema.Builder schemaBuilder = builderFactory.newSchemaBuilder(RECORD);

        if (jsonKeys == null || jsonKeys.isEmpty()) {
            jsonKeys = jsonObject.getNames();
        }

        for (String key : jsonKeys) {
            // receive value from JSON
            Object value = jsonObject.get(key);

            if (value == null) {
                LOG.warn(i18n.cannotGuessWhenDataIsNull());
                continue;
            }

            // With this value we can define type
            Schema.Type type = defineValueType(value);

            // We can add to schema builder entry
            Schema.Entry.Builder entryBuilder = builderFactory.newEntryBuilder();
            entryBuilder.withNullable(true).withName(key).withType(type);

            if (type == RECORD) {
                entryBuilder.withElementSchema(getSchema((JsonObject) value, null));
            } else if (type == ARRAY) {
                entryBuilder.withElementSchema(defineSchemaForArray((JsonArray) value));
            }
            Schema.Entry currentEntry = entryBuilder.build();
            schemaBuilder.withEntry(currentEntry);
        }
        return schemaBuilder.build();
    }

    private Schema defineSchemaForArray(JsonArray jsonArray) {
        Object firstValueInArray = jsonArray.get(0);
        Schema.Builder schemaBuilder = builderFactory.newSchemaBuilder(RECORD);
        if (firstValueInArray == null) {
            throw new ComponentException("First value of Array is null. Can't define type of values in array");
        }
        Schema.Type type = defineValueType(firstValueInArray);
        schemaBuilder.withType(type);
        if (type == RECORD) {
            schemaBuilder
                    .withEntry(builderFactory
                            .newEntryBuilder()
                            .withElementSchema(getSchema((JsonObject) firstValueInArray, null))
                            .build());
        } else if (type == ARRAY) {
            schemaBuilder
                    .withEntry(builderFactory
                            .newEntryBuilder()
                            .withElementSchema(defineSchemaForArray((JsonArray) firstValueInArray))
                            .build());
        }
        return schemaBuilder.withType(type).build();
    }

    private Schema.Type defineValueType(Object value) {
        if (value instanceof String) {
            return STRING;
        } else if (value instanceof Boolean) {
            return BOOLEAN;
        } else if (value instanceof Date) {
            return DATETIME;
        } else if (value instanceof Double) {
            return DOUBLE;
        } else if (value instanceof Integer) {
            return INT;
        } else if (value instanceof Long) {
            return LONG;
        } else if (value instanceof Byte[]) {
            throw new ComponentException("BYTES is unsupported");
        } else if (value instanceof JsonArray) {
            return STRING;
        } else if (value instanceof JsonObject) {
            return STRING;
        } else if (value instanceof Float) {
            return FLOAT;
        } else {
            return STRING;
        }
    }

    public static class ClusterHolder {

        @Getter
        private final CouchbaseEnvironment env;

        @Getter
        private final Cluster cluster;

        private final AtomicInteger usages = new AtomicInteger();

        public ClusterHolder(final CouchbaseEnvironment env, final Cluster cluster) {
            this.env = env;
            this.cluster = cluster;
        }

        public void use() {
            usages.incrementAndGet();
        }

        public int release() {
            return usages.decrementAndGet();
        }
    }
}