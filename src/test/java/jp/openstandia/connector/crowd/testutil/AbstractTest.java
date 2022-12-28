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
package jp.openstandia.connector.crowd.testutil;

import jp.openstandia.connector.crowd.CrowdConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public abstract class AbstractTest {

    protected ConnectorFacade connector;
    protected CrowdConfiguration configuration;
    protected MockClient mockClient;

    protected CrowdConfiguration newConfiguration() {
        CrowdConfiguration conf = new CrowdConfiguration();
        conf.setBaseURL("http://localhost:8095/crowd");
        conf.setApplicationName("dummy");
        conf.setApplicationPassword(new GuardedString("dummy".toCharArray()));
        return conf;
    }

    protected ConnectorFacade newFacade(Configuration configuration) {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration impl = TestHelpers.createTestConfiguration(LocalCrowdConnector.class, configuration);
        impl.getResultsHandlerConfiguration().setEnableAttributesToGetSearchResultsHandler(false);
        impl.getResultsHandlerConfiguration().setEnableNormalizingResultsHandler(false);
        impl.getResultsHandlerConfiguration().setEnableFilteredResultsHandler(false);
        return factory.newInstance(impl);
    }

    @BeforeEach
    void before() {
        this.configuration = newConfiguration();
        this.connector = newFacade(this.configuration);
        this.mockClient = MockClient.instance();
        this.mockClient.init();
    }

    // Utilities


    protected <T> List<T> list(T... s) {
        return Arrays.stream(s).collect(Collectors.toList());
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
}
