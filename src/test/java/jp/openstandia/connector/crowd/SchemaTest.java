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

import jp.openstandia.connector.crowd.testutil.AbstractTest;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SchemaTest extends AbstractTest {

    @Test
    void schema() {
        Schema schema = connector.schema();

        assertNotNull(schema);
        assertEquals(2, schema.getObjectClassInfo().size());
    }

    @Test
    void user() {
        Schema schema = connector.schema();

        Optional<ObjectClassInfo> user = schema.getObjectClassInfo().stream().filter(o -> o.is("user")).findFirst();

        assertTrue(user.isPresent());

        ObjectClassInfo userSchema = user.get();
        Set<AttributeInfo> attributeInfo = userSchema.getAttributeInfo();

        assertEquals(11, attributeInfo.size());
        assertAttributeInfo(attributeInfo, Uid.NAME);
        assertAttributeInfo(attributeInfo, Name.NAME);
        assertAttributeInfo(attributeInfo, OperationalAttributes.PASSWORD_NAME);
        assertAttributeInfo(attributeInfo, OperationalAttributes.ENABLE_NAME);
        assertAttributeInfo(attributeInfo, "email");
        assertAttributeInfo(attributeInfo, "display-name");
        assertAttributeInfo(attributeInfo, "first-name");
        assertAttributeInfo(attributeInfo, "last-name");
        assertAttributeInfo(attributeInfo, "created-date");
        assertAttributeInfo(attributeInfo, "updated-date");
        assertAttributeInfo(attributeInfo, "groups", true);
    }

    @Test
    void userWithAttributes() {
        // Apply custom configuration for this test
        configuration.setUserAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        Schema schema = connector.schema();

        Optional<ObjectClassInfo> user = schema.getObjectClassInfo().stream().filter(o -> o.is("user")).findFirst();

        assertTrue(user.isPresent());

        ObjectClassInfo userSchema = user.get();
        Set<AttributeInfo> attributeInfo = userSchema.getAttributeInfo();

        assertEquals(13, attributeInfo.size());
        assertAttributeInfo(attributeInfo, "attributes.custom1");
        assertAttributeInfo(attributeInfo, "attributes.custom2", true);
    }

    @Test
    void group() {
        Schema schema = connector.schema();

        Optional<ObjectClassInfo> group = schema.getObjectClassInfo().stream().filter(o -> o.is("group")).findFirst();

        assertTrue(group.isPresent());

        ObjectClassInfo groupSchema = group.get();
        Set<AttributeInfo> attributeInfo = groupSchema.getAttributeInfo();

        assertEquals(5, attributeInfo.size());
        assertAttributeInfo(attributeInfo, Uid.NAME);
        assertAttributeInfo(attributeInfo, Name.NAME);
        assertAttributeInfo(attributeInfo, OperationalAttributes.ENABLE_NAME);
        assertAttributeInfo(attributeInfo, "description");
        assertAttributeInfo(attributeInfo, "groups", true);
    }

    protected void assertAttributeInfo(Set<AttributeInfo> info, String attrName) {
        assertAttributeInfo(info, attrName, false);
    }

    protected void assertAttributeInfo(Set<AttributeInfo> info, String attrName, boolean isMultiple) {
        Optional<AttributeInfo> attributeInfo = info.stream().filter(x -> x.is(attrName)).findFirst();
        assertTrue(attributeInfo.isPresent(), attrName);
        assertEquals(isMultiple, attributeInfo.get().isMultiValued(), "Unexpected multiValued of " + attrName);
    }
}
