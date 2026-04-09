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

import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchemaIT extends AbstractIntegrationTest {

    @Test
    void schema() {
        Schema schema = connector.schema();
        assertNotNull(schema);
        assertEquals(2, schema.getObjectClassInfo().size());
    }

    @Test
    void user() {
        Schema schema = connector.schema();

        ObjectClassInfo userInfo = schema.getObjectClassInfo().stream()
                .filter(o -> o.getType().equals("user"))
                .findFirst()
                .orElseThrow();
        // Uid, Name, __PASSWORD__, __ENABLE__, email, display-name, first-name, last-name,
        // created-date, updated-date, groups = 11
        assertEquals(11, userInfo.getAttributeInfo().size());
    }

    @Test
    void userWithAttributes() {
        configuration.setUserAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        Schema schema = connector.schema();

        ObjectClassInfo userInfo = schema.getObjectClassInfo().stream()
                .filter(o -> o.getType().equals("user"))
                .findFirst()
                .orElseThrow();
        // 11 + 2 custom attributes = 13
        assertEquals(13, userInfo.getAttributeInfo().size());
    }

    @Test
    void group() {
        Schema schema = connector.schema();

        ObjectClassInfo groupInfo = schema.getObjectClassInfo().stream()
                .filter(o -> o.getType().equals("group"))
                .findFirst()
                .orElseThrow();
        // Uid, Name, __ENABLE__, description, groups = 5
        assertEquals(5, groupInfo.getAttributeInfo().size());
    }
}
