/*
 *  Copyright Nomura Research Institute, Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jp.openstandia.connector.crowd;

import com.atlassian.crowd.integration.rest.service.factory.RestCrowdClientFactory;
import com.atlassian.crowd.service.client.AuthenticationMethod;
import com.atlassian.crowd.service.client.ClientProperties;
import com.atlassian.crowd.service.client.CrowdClient;
import com.atlassian.crowd.service.client.ImmutableClientProperties;
import jp.openstandia.connector.util.ObjectHandler;
import jp.openstandia.connector.util.SchemaDefinition;
import jp.openstandia.connector.util.Utils;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.*;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.*;
import org.identityconnectors.framework.spi.operations.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ConnectorClass(configurationClass = CrowdConfiguration.class, displayNameKey = "Crowd Connector")
public class CrowdConnector implements PoolableConnector, CreateOp, UpdateDeltaOp, DeleteOp, SchemaOp, TestOp, SearchOp<CrowdFilter>, InstanceNameAware {

    private static final Log LOG = Log.getLog(CrowdConnector.class);

    protected CrowdConfiguration configuration;
    protected CrowdRESTClient client;

    private CrowdSchema cachedSchema;
    private String instanceName;

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void init(Configuration configuration) {
        this.configuration = (CrowdConfiguration) configuration;

        try {
            authenticateResource();
        } catch (RuntimeException e) {
            throw processRuntimeException(e);
        }

        LOG.ok("Connector {0} successfully initialized", getClass().getName());
    }

    protected void authenticateResource() {
        ImmutableClientProperties.Builder builder = ImmutableClientProperties.builder();
        builder.setBaseURL(configuration.getBaseURL())
                .setAuthenticationMethod(AuthenticationMethod.BASIC_AUTH)
                // This connector instance only uses 1 http connection
                .setHttpMaxConnections("1")
                .setHttpTimeout(Integer.toString(configuration.getConnectionTimeoutInSeconds()))
                .setSocketTimeout(Integer.toString(configuration.getSocketTimeoutInMilliseconds()))
                .setApplicationName(configuration.getApplicationName());

        configuration.getApplicationPassword().access(a -> {
            builder.setApplicationPassword(String.valueOf(a));
        });

        if (StringUtil.isNotBlank(configuration.getHttpProxyHost())) {
            builder.setHttpProxyHost(configuration.getHttpProxyHost());
            builder.setHttpProxyPort(Integer.toString(configuration.getHttpProxyPort()));

            if (StringUtil.isNotBlank(configuration.getHttpProxyUser())) {
                builder.setHttpProxyUsername(configuration.getHttpProxyUser());

                configuration.getHttpProxyPassword().access(a -> {
                    builder.setHttpProxyPassword(String.valueOf(a));
                });
            }
        }

        ClientProperties policy = builder.build();
        CrowdClient crowdClient = new RestCrowdClientFactory().newInstance(policy);

        client = new CrowdRESTClient(instanceName, configuration, crowdClient);

        // Verify we can access the Crowd API
        client.test();
    }

    @Override
    public Schema schema() {
        try {
            cachedSchema = new CrowdSchema(configuration, client);
            return cachedSchema.schema;

        } catch (RuntimeException e) {
            throw processRuntimeException(e);
        }
    }

    private ObjectHandler getSchemaHandler(ObjectClass objectClass) {
        if (objectClass == null) {
            throw new InvalidAttributeValueException("ObjectClass value not provided");
        }

        // Load schema map if it's not loaded yet
        if (cachedSchema == null) {
            schema();
        }

        ObjectHandler handler = cachedSchema.getSchemaHandler(objectClass);

        if (handler == null) {
            throw new InvalidAttributeValueException("Unsupported object class " + objectClass);
        }

        return handler;
    }

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> createAttributes, OperationOptions options) {
        if (createAttributes == null || createAttributes.isEmpty()) {
            throw new InvalidAttributeValueException("Attributes not provided or empty");
        }

        try {
            return getSchemaHandler(objectClass).create(createAttributes);

        } catch (RuntimeException e) {
            throw processRuntimeException(e);
        }
    }

    @Override
    public Set<AttributeDelta> updateDelta(ObjectClass objectClass, Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        if (uid == null) {
            throw new InvalidAttributeValueException("uid not provided");
        }

        try {
            return getSchemaHandler(objectClass).updateDelta(uid, modifications, options);

        } catch (UnknownUidException e) {
            LOG.warn("Not found object when updating. objectClass: {0}, uid: {1}", objectClass, uid);
            throw processRuntimeException(e);

        } catch (RuntimeException e) {
            throw processRuntimeException(e);
        }
    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        if (uid == null) {
            throw new InvalidAttributeValueException("uid not provided");
        }

        try {
            getSchemaHandler(objectClass).delete(uid, options);

        } catch (UnknownUidException e) {
            LOG.warn("Not found object when deleting. objectClass: {0}, uid: {1}", objectClass, uid);
            throw processRuntimeException(e);

        } catch (RuntimeException e) {
            throw processRuntimeException(e);
        }
    }

    @Override
    public FilterTranslator<CrowdFilter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        return new CrowdFilterTranslator(objectClass, options);
    }

    @Override
    public void executeQuery(ObjectClass objectClass, CrowdFilter filter, ResultsHandler resultsHandler, OperationOptions options) {
        ObjectHandler schemaHandler = getSchemaHandler(objectClass);
        SchemaDefinition schema = schemaHandler.getSchema();

        int pageSize = Utils.resolvePageSize(options, configuration.getDefaultQueryPageSize());
        int pageOffset = Utils.resolvePageOffset(options);

        // Create full attributesToGet by RETURN_DEFAULT_ATTRIBUTES + ATTRIBUTES_TO_GET
        Map<String, String> attributesToGet = Utils.createFullAttributesToGet(schema, options);
        Set<String> returnAttributesSet = attributesToGet.keySet();
        // Collect actual resource fields for fetching (We can them for filtering attributes if the resource supports it)
        Set<String> fetchFieldSet = attributesToGet.values().stream().collect(Collectors.toSet());

        boolean allowPartialAttributeValues = Utils.shouldAllowPartialAttributeValues(options);

        int total = 0;

        if (filter != null) {
            if (filter.isByUid()) {
                total = schemaHandler.getByUid((Uid) filter.attributeValue, resultsHandler, options,
                        returnAttributesSet, fetchFieldSet,
                        allowPartialAttributeValues, pageSize, pageOffset);
            } else if (filter.isByName()) {
                total = schemaHandler.getByName((Name) filter.attributeValue, resultsHandler, options,
                        returnAttributesSet, fetchFieldSet,
                        allowPartialAttributeValues, pageSize, pageOffset);
            }
            // No result
        } else {
            total = schemaHandler.getAll(resultsHandler, options,
                    returnAttributesSet, fetchFieldSet,
                    allowPartialAttributeValues, pageSize, pageOffset);
        }

        if (resultsHandler instanceof SearchResultsHandler &&
                pageOffset > 0) {

            int remaining = total - (pageSize * pageOffset);

            SearchResultsHandler searchResultsHandler = (SearchResultsHandler) resultsHandler;
            SearchResult searchResult = new SearchResult(null, remaining);
            searchResultsHandler.handleResult(searchResult);
        }
    }

    @Override
    public void test() {
        try {
            dispose();
            authenticateResource();
        } catch (RuntimeException e) {
            throw processRuntimeException(e);
        }
    }

    @Override
    public void dispose() {
        client.close();
        this.client = null;
        this.cachedSchema = null;
    }

    @Override
    public void checkAlive() {
        // Do nothing
    }

    @Override
    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    protected ConnectorException processRuntimeException(RuntimeException e) {
        if (e instanceof ConnectorException) {
            // Write error log because IDM might not write full stack trace
            // It's hard to debug the error
            if (e instanceof AlreadyExistsException) {
                LOG.warn(e, "Detected the object already exists");
            } else {
                LOG.error(e, "Detected Crowd connector error");
            }
            return (ConnectorException) e;
        }

        LOG.error(e, "Detected Crowd connector unexpected error");

        return new ConnectorIOException(e);
    }
}
