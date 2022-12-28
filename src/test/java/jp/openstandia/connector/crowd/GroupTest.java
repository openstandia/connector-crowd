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

import com.atlassian.crowd.model.group.GroupWithAttributes;
import jp.openstandia.connector.crowd.testutil.AbstractTest;
import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupTest extends AbstractTest {

    @Test
    void addGroup() {
        // Given
        String groupName = "foo";
        String desc = "This is foo group.";
        List<String> groups = list("p1", "p2");

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(groupName));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("description", desc));
        attrs.add(AttributeBuilder.build("groups", groups));

        AtomicReference<GroupWithAttributes> created = new AtomicReference<>();
        mockClient.createGroup = ((g) -> {
            created.set(g);

            return new Uid(groupName, new Name(groupName));
        });
        AtomicReference<String> targetGroupName = new AtomicReference<>();
        AtomicReference<List<String>> targetGroups = new AtomicReference<>();
        mockClient.addGroupToGroup = ((n, g) -> {
            targetGroupName.set(n);
            targetGroups.set(g);
        });

        // When
        Uid uid = connector.create(CrowdGroupHandler.GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Then
        assertEquals(groupName, uid.getUidValue());
        assertEquals(groupName, uid.getNameHintValue());

        GroupWithAttributes newGroup = created.get();
        assertEquals(groupName, newGroup.getName());
        assertEquals(desc, newGroup.getDescription());
        assertTrue(newGroup.isActive());

        assertEquals("foo", targetGroupName.get());
        assertEquals(groups, targetGroups.get());
    }
}
