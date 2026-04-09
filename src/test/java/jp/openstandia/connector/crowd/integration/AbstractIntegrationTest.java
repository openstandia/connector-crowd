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
package jp.openstandia.connector.crowd.integration;

import jp.openstandia.connector.crowd.CrowdConfiguration;
import jp.openstandia.connector.crowd.CrowdConnector;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public abstract class AbstractIntegrationTest {

    private static final String APP_NAME = "midpoint-connector";
    private static final String APP_PASSWORD = "password";
    private static final String DB_NAME = "crowd_mock";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";

    protected static Network network;
    protected static PostgreSQLContainer<?> postgres;
    protected static GenericContainer<?> crowdMock;
    protected static String crowdBaseUrl;

    protected ConnectorFacade connector;
    protected CrowdConfiguration configuration;

    @BeforeAll
    static void startContainers() {
        network = Network.newNetwork();

        postgres = new PostgreSQLContainer<>("postgres:17")
                .withDatabaseName(DB_NAME)
                .withUsername(DB_USER)
                .withPassword(DB_PASSWORD)
                .withNetwork(network)
                .withNetworkAliases("postgres");
        postgres.start();

        Path dockerfilePath = Path.of(System.getProperty("user.dir"), "crowd-mock-server");

        crowdMock = new GenericContainer<>(
                new ImageFromDockerfile("crowd-mock-server", true)
                        .withDockerfile(dockerfilePath.resolve("Dockerfile")))
                .withExposedPorts(8095)
                .withNetwork(network)
                .withEnv("CROWD_MOCK_DB_HOST", "postgres")
                .withEnv("CROWD_MOCK_DB_PORT", "5432")
                .withEnv("CROWD_MOCK_DB_NAME", DB_NAME)
                .withEnv("CROWD_MOCK_DB_USER", DB_USER)
                .withEnv("CROWD_MOCK_DB_PASSWORD", DB_PASSWORD)
                .withEnv("CROWD_MOCK_AUTH_USER", APP_NAME)
                .withEnv("CROWD_MOCK_AUTH_PASSWORD", APP_PASSWORD)
                .dependsOn(postgres)
                .waitingFor(Wait.forHttp("/crowd/rest/usermanagement/1/config/cookie")
                        .withBasicCredentials(APP_NAME, APP_PASSWORD)
                        .forStatusCode(200));
        crowdMock.start();

        crowdBaseUrl = String.format("http://%s:%d/crowd",
                crowdMock.getHost(), crowdMock.getMappedPort(8095));
    }

    @AfterAll
    static void stopContainers() {
        if (crowdMock != null) crowdMock.stop();
        if (postgres != null) postgres.stop();
        if (network != null) network.close();
    }

    protected CrowdConfiguration newConfiguration() {
        CrowdConfiguration conf = new CrowdConfiguration();
        conf.setBaseURL(crowdBaseUrl);
        conf.setApplicationName(APP_NAME);
        conf.setApplicationPassword(new GuardedString(APP_PASSWORD.toCharArray()));
        return conf;
    }

    protected ConnectorFacade newFacade(Configuration configuration) {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration impl = TestHelpers.createTestConfiguration(CrowdConnector.class, configuration);
        impl.getResultsHandlerConfiguration().setEnableAttributesToGetSearchResultsHandler(false);
        impl.getResultsHandlerConfiguration().setEnableNormalizingResultsHandler(false);
        impl.getResultsHandlerConfiguration().setEnableFilteredResultsHandler(false);
        return factory.newInstance(impl);
    }

    @BeforeEach
    void setUp() throws Exception {
        // Reset mock server data
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(crowdBaseUrl + "/test/reset"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        client.send(request, HttpResponse.BodyHandlers.ofString());

        this.configuration = newConfiguration();
        this.connector = newFacade(this.configuration);
    }

    @AfterEach
    void tearDown() {
        ConnectorFacadeFactory.getInstance().dispose();
    }

    // Utilities

    @SafeVarargs
    protected final <T> List<T> list(T... s) {
        return Arrays.stream(s).collect(Collectors.toList());
    }

    @SafeVarargs
    protected final <T> Set<T> set(T... s) {
        return Arrays.stream(s).collect(Collectors.toSet());
    }

    protected <T> Set<T> asSet(Collection<T> c) {
        return new HashSet<>(c);
    }

    protected String toPlain(GuardedString gs) {
        AtomicReference<String> plain = new AtomicReference<>();
        gs.access(c -> {
            plain.set(String.valueOf(c));
        });
        return plain.get();
    }

    protected OperationOptions defaultGetOperation(String... explicit) {
        List<String> attrs = Arrays.stream(explicit).collect(Collectors.toList());
        attrs.add(OperationalAttributes.PASSWORD_NAME);
        attrs.add(OperationalAttributes.ENABLE_NAME);

        return new OperationOptionsBuilder()
                .setReturnDefaultAttributes(true)
                .setAttributesToGet(attrs)
                .setAllowPartialResults(true)
                .build();
    }

    protected OperationOptions defaultSearchOperation(String... explicit) {
        List<String> attrs = Arrays.stream(explicit).collect(Collectors.toList());
        attrs.add(OperationalAttributes.PASSWORD_NAME);
        attrs.add(OperationalAttributes.ENABLE_NAME);

        return new OperationOptionsBuilder()
                .setReturnDefaultAttributes(true)
                .setAttributesToGet(attrs)
                .setAllowPartialAttributeValues(true)
                .setPagedResultsOffset(1)
                .setPageSize(20)
                .build();
    }

    protected Object singleAttr(ConnectorObject connectorObject, String attrName) {
        Attribute attr = connectorObject.getAttributeByName(attrName);
        if (attr == null) {
            Assertions.fail(attrName + " is not contained in the connectorObject: " + connectorObject);
        }
        List<Object> value = attr.getValue();
        if (value == null || value.size() != 1) {
            Assertions.fail(attrName + " is not single value: " + value);
        }
        return value.get(0);
    }

    protected List<Object> multiAttr(ConnectorObject connectorObject, String attrName) {
        Attribute attr = connectorObject.getAttributeByName(attrName);
        if (attr == null) {
            Assertions.fail(attrName + " is not contained in the connectorObject: " + connectorObject);
        }
        List<Object> value = attr.getValue();
        if (value == null) {
            Assertions.fail(attrName + " is not multiple value: " + value);
        }
        return value;
    }

    protected boolean isIncompleteAttribute(Attribute attr) {
        if (attr == null) {
            return false;
        }
        return attr.getAttributeValueCompleteness().equals(AttributeValueCompleteness.INCOMPLETE);
    }
}
