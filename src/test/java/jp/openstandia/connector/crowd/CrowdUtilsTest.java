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

import com.atlassian.crowd.integration.rest.entity.UserEntity;
import jp.openstandia.connector.crowd.testutil.MockClient;
import jp.openstandia.connector.util.SchemaDefinition;
import jp.openstandia.connector.util.Utils;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.*;
import static org.junit.jupiter.api.Assertions.*;

class CrowdUtilsTest {

    @Test
    void shouldReturnPartialAttributeValues() {
        OperationOptions noOptions = new OperationOptionsBuilder().build();
        assertFalse(Utils.shouldAllowPartialAttributeValues(noOptions));

        OperationOptions falseOption = new OperationOptionsBuilder().setAllowPartialAttributeValues(false).build();
        assertFalse(Utils.shouldAllowPartialAttributeValues(falseOption));

        OperationOptions trueOption = new OperationOptionsBuilder().setAllowPartialAttributeValues(true).build();
        assertTrue(Utils.shouldAllowPartialAttributeValues(trueOption));
    }

    @Test
    void createFullAttributesToGet() {
        SchemaDefinition.Builder<CrowdUserModel, CrowdUserModel, UserEntity> builder = SchemaDefinition.newBuilder(CrowdUserHandler.USER_OBJECT_CLASS, CrowdUserModel.class, UserEntity.class);
        builder.addUid("key",
                SchemaDefinition.Types.STRING_CASE_IGNORE,
                null,
                (source) -> source.getExternalId(),
                null,
                NOT_CREATABLE, NOT_UPDATEABLE
        );
        builder.addName("username",
                SchemaDefinition.Types.STRING_CASE_IGNORE,
                (source, dest) -> dest.setUserName(source),
                (source) -> source.getName(),
                "name",
                REQUIRED
        );
        builder.addAsMultiple("groups",
                SchemaDefinition.Types.STRING,
                (source, dest) -> dest.setGroups(source),
                (add, dest) -> dest.addGroups(add),
                (remove, dest) -> dest.removeGroups(remove),
                (source) -> MockClient.instance().getGroupsForUser(source.getName(), 50),
                null,
                NOT_RETURNED_BY_DEFAULT
        );
        SchemaDefinition schemaDefinition = builder.build();

        OperationOptions noOptions = new OperationOptionsBuilder().build();
        Map<String, String> fullAttributesToGet = Utils.createFullAttributesToGet(schemaDefinition, noOptions);
        assertEquals(0, fullAttributesToGet.size());

        OperationOptions returnDefaultAttributes = new OperationOptionsBuilder().setReturnDefaultAttributes(true).build();
        fullAttributesToGet = Utils.createFullAttributesToGet(schemaDefinition, returnDefaultAttributes);
        assertEquals(2, fullAttributesToGet.size());
        assertTrue(fullAttributesToGet.containsKey(Uid.NAME));
        assertTrue(fullAttributesToGet.containsKey(Name.NAME));
        assertEquals("key", fullAttributesToGet.get(Uid.NAME));
        assertEquals("name", fullAttributesToGet.get(Name.NAME));

        OperationOptions returnDefaultAttributesPlus = new OperationOptionsBuilder().setReturnDefaultAttributes(true).setAttributesToGet("groups").build();
        fullAttributesToGet = Utils.createFullAttributesToGet(schemaDefinition, returnDefaultAttributesPlus);
        assertEquals(3, fullAttributesToGet.size());
        assertTrue(fullAttributesToGet.containsKey(Uid.NAME));
        assertTrue(fullAttributesToGet.containsKey(Name.NAME));
        assertTrue(fullAttributesToGet.containsKey("groups"));
        assertEquals("key", fullAttributesToGet.get(Uid.NAME));
        assertEquals("name", fullAttributesToGet.get(Name.NAME));
        assertEquals("groups", fullAttributesToGet.get("groups"));
    }
}