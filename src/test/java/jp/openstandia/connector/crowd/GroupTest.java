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

import com.atlassian.crowd.integration.rest.entity.GroupEntity;
import com.atlassian.crowd.integration.rest.entity.MultiValuedAttributeEntity;
import com.atlassian.crowd.integration.rest.entity.MultiValuedAttributeEntityList;
import com.atlassian.crowd.model.group.Group;
import com.atlassian.crowd.model.group.GroupType;
import com.atlassian.crowd.model.group.GroupWithAttributes;
import jp.openstandia.connector.crowd.testutil.AbstractTest;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static jp.openstandia.connector.crowd.CrowdGroupHandler.GROUP_OBJECT_CLASS;
import static org.junit.jupiter.api.Assertions.*;

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
        AtomicReference<String> targetName = new AtomicReference<>();
        AtomicReference<List<String>> targetGroups = new AtomicReference<>();
        mockClient.addGroupToGroup = ((n, g) -> {
            targetName.set(n);
            targetGroups.set(g);
        });

        // When
        Uid uid = connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Then
        assertEquals(groupName, uid.getUidValue());
        assertEquals(groupName, uid.getNameHintValue());

        GroupWithAttributes newGroup = created.get();
        assertEquals(groupName, newGroup.getName());
        assertEquals(desc, newGroup.getDescription());
        assertTrue(newGroup.isActive());

        assertEquals("foo", targetName.get());
        assertEquals(groups, targetGroups.get());
    }

    @Test
    void addGroupWithAttributes() {
        // Apply custom configuration for this test
        configuration.setGroupAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String groupName = "foo";
        String custom1 = "abc";
        List<String> custom2 = list("123", "456");

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(groupName));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("attributes.custom1", custom1));
        attrs.add(AttributeBuilder.build("attributes.custom2", custom2));

        AtomicReference<GroupWithAttributes> created = new AtomicReference<>();
        mockClient.createGroup = ((g) -> {
            created.set(g);

            return new Uid(groupName, new Name(groupName));
        });
        AtomicReference<String> targetName = new AtomicReference<>();
        AtomicReference<Map<String, Set<String>>> newAttrs = new AtomicReference<>();
        mockClient.updateGroupAttributes = ((n, a) -> {
            targetName.set(n);
            newAttrs.set(a);
        });

        // When
        Uid uid = connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Then
        assertEquals(groupName, uid.getUidValue());
        assertEquals(groupName, uid.getNameHintValue());

        GroupWithAttributes newGroup = created.get();
        assertEquals(groupName, newGroup.getName());
        assertNull(newGroup.getDescription());

        Map<String, Set<String>> newGroupAttrs = newAttrs.get();
        assertEquals(set(custom1), newGroupAttrs.get("custom1"));
        assertEquals(asSet(custom2), newGroupAttrs.get("custom2"));
    }

    @Test
    void addGroupWithInactive() {
        // Given
        String groupName = "foo";
        String desc = "This is foo group.";

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(groupName));
        attrs.add(AttributeBuilder.buildEnabled(false));
        attrs.add(AttributeBuilder.build("description", desc));

        AtomicReference<GroupWithAttributes> created = new AtomicReference<>();
        mockClient.createGroup = ((g) -> {
            created.set(g);

            return new Uid(groupName, new Name(groupName));
        });

        // When
        Uid uid = connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Then
        assertEquals(groupName, uid.getUidValue());
        assertEquals(groupName, uid.getNameHintValue());
        assertFalse(created.get().isActive());
    }

    @Test
    void addGroupButAlreadyExists() {
        // Given
        String groupName = "foo";
        String desc = "This is foo group.";

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(groupName));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("description", desc));

        mockClient.createGroup = ((g) -> {
            throw new AlreadyExistsException();
        });

        // When
        Throwable expect = null;
        try {
            Uid uid = connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());
        } catch (Throwable t) {
            expect = t;
        }

        // Then
        assertNotNull(expect);
        assertTrue(expect instanceof AlreadyExistsException);
    }

    @Test
    void updateGroup() {
        // Given
        String currentName = "hoge";

        String desc = "This is foo group.";
        List<String> groups = list("p1", "p2");

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("description", desc));
        modifications.add(AttributeDeltaBuilder.build("groups", groups, null));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getGroupByUid = ((u) -> {
            targetUid.set(u);

            GroupEntity current = GroupEntity.newMinimalInstance(currentName);
            return current;
        });
        AtomicReference<Group> updated = new AtomicReference<>();
        mockClient.updateGroup = ((g) -> {
            updated.set(g);
        });
        AtomicReference<String> targetName = new AtomicReference<>();
        AtomicReference<List<String>> targetAddGroups = new AtomicReference<>();
        mockClient.addGroupToGroup = ((n, g) -> {
            targetName.set(n);
            targetAddGroups.set(g);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(GROUP_OBJECT_CLASS, new Uid(currentName, new Name(currentName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(currentName, targetUid.get().getUidValue());

        Group updatedGroup = updated.get();
        assertEquals(desc, updatedGroup.getDescription());

        assertEquals(currentName, targetName.get());
        assertEquals(groups, targetAddGroups.get());
    }

    @Test
    void updateGroupWithInactive() {
        // Given
        String key = "foo";
        String currentName = "foo";

        boolean active = false;

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.buildEnabled(active));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getGroupByUid = ((u) -> {
            targetUid.set(u);

            GroupEntity current = new GroupEntity(currentName, null, GroupType.GROUP, true);
            return current;
        });
        AtomicReference<Group> updated = new AtomicReference<>();
        mockClient.updateGroup = ((g) -> {
            updated.set(g);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(GROUP_OBJECT_CLASS, new Uid(key, new Name(currentName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(key, targetUid.get().getUidValue());

        Group updatedGroup = updated.get();
        assertEquals(active, updatedGroup.isActive());
    }

    @Test
    void updateUserWithAttributesAdd() {
        // Apply custom configuration for this test
        configuration.setGroupAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String key = "foo";
        String currentName = "foo";
        String custom1 = "abc";
        List<String> custom2 = list("123", "456");

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("attributes.custom1", custom1));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom2", custom2, null));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getGroupByUid = ((u) -> {
            targetUid.set(u);

            GroupEntity current = GroupEntity.newMinimalInstance(currentName);
            return current;
        });
        AtomicReference<String> targetName = new AtomicReference<>();
        AtomicReference<Map<String, Set<String>>> newAttrs = new AtomicReference<>();
        mockClient.updateGroupAttributes = ((n, a) -> {
            targetName.set(n);
            newAttrs.set(a);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(GROUP_OBJECT_CLASS, new Uid(key, new Name(currentName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(key, targetUid.get().getUidValue());

        assertEquals(currentName, targetName.get());
        assertNotNull(newAttrs.get());
        Map<String, Set<String>> updatedAttrs = newAttrs.get();
        assertEquals(set(custom1), updatedAttrs.get("custom1"));
        assertEquals(asSet(custom2), updatedAttrs.get("custom2"));
    }

    @Test
    void updateGroupWithAttributesUpdate() {
        // Apply custom configuration for this test
        configuration.setGroupAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String key = "foo";
        String currentName = "foo";
        String custom1 = "abc";
        List<String> custom2Add = list("789");
        List<String> custom2Remove = list("123");

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("attributes.custom1", custom1));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom2", custom2Add, custom2Remove));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getGroupByUid = ((u) -> {
            targetUid.set(u);

            GroupEntity current = GroupEntity.newMinimalInstance(currentName);
            List<MultiValuedAttributeEntity> attrs = new ArrayList<>();
            attrs.add(new MultiValuedAttributeEntity("custom1", list("xyz")));
            attrs.add(new MultiValuedAttributeEntity("custom2", list("123", "456")));
            current.setAttributes(new MultiValuedAttributeEntityList(attrs));
            return current;
        });
        AtomicReference<String> targetName1 = new AtomicReference<>();
        AtomicReference<Map<String, Set<String>>> newAttrs = new AtomicReference<>();
        mockClient.updateGroupAttributes = ((u, a) -> {
            targetName1.set(u);
            newAttrs.set(a);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(GROUP_OBJECT_CLASS, new Uid(key, new Name(currentName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(key, targetUid.get().getUidValue());

        assertEquals(currentName, targetName1.get());
        assertNotNull(newAttrs.get());
        Map<String, Set<String>> updatedAttrs = newAttrs.get();
        assertEquals(set(custom1), updatedAttrs.get("custom1"));
        assertEquals(set("456", "789"), updatedAttrs.get("custom2"));
    }

    @Test
    void updateUserWithNoValues() {
        // Apply custom configuration for this test
        configuration.setGroupAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String key = "foo";
        String currentName = "foo";
        String desc = "This is foo group.";
        boolean active = true;
        String custom1 = "abc";
        List<String> custom2 = list("123", "789");

        Set<AttributeDelta> modifications = new HashSet<>();
        // IDM sets empty list to remove the single value
        modifications.add(AttributeDeltaBuilder.build("description", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom1", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom2", null, custom2));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getGroupByUid = ((u) -> {
            targetUid.set(u);

            GroupEntity current = new GroupEntity(currentName, desc, GroupType.GROUP, active);
            List<MultiValuedAttributeEntity> attrs = new ArrayList<>();
            attrs.add(new MultiValuedAttributeEntity("custom1", list(custom1)));
            attrs.add(new MultiValuedAttributeEntity("custom2", custom2));
            current.setAttributes(new MultiValuedAttributeEntityList(attrs));
            return current;
        });
        AtomicReference<Group> updated = new AtomicReference<>();
        mockClient.updateGroup = ((g) -> {
            updated.set(g);
        });
        AtomicReference<String> targetName = new AtomicReference<>();
        AtomicReference<Map<String, Set<String>>> newAttrs = new AtomicReference<>();
        mockClient.updateGroupAttributes = ((n, a) -> {
            targetName.set(n);
            newAttrs.set(a);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(GROUP_OBJECT_CLASS, new Uid(key, new Name(currentName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(key, targetUid.get().getUidValue());

        Group updatedGroup = updated.get();
        assertEquals(currentName, updatedGroup.getName());
        assertNull(updatedGroup.getDescription());
        assertTrue(updatedGroup.isActive());

        assertEquals(currentName, targetName.get());
        assertNotNull(newAttrs.get());

        Map<String, Set<String>> updatedAttrs = newAttrs.get();
        assertEquals(2, updatedAttrs.size());
        assertTrue(updatedAttrs.get("custom1").isEmpty(), "Expected empty set to remove the attribute, but has value");
        assertTrue(updatedAttrs.get("custom2").isEmpty(), "Expected empty set to remove the attribute, but has value");
    }

    @Test
    void updateGroupGroups() {
        // Given
        String key = "foo";
        String currentName = "foo";
        List<String> addGroups = list("group1", "group2");
        List<String> delGroups = list("group3", "group4");

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("groups", addGroups, delGroups));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getGroupByUid = ((u) -> {
            targetUid.set(u);

            GroupEntity current = GroupEntity.newMinimalInstance(currentName);
            return current;
        });
        AtomicReference<String> targetName1 = new AtomicReference<>();
        AtomicReference<List<String>> targetAddGroups = new AtomicReference<>();
        mockClient.addGroupToGroup = ((u, g) -> {
            targetName1.set(u);
            targetAddGroups.set(g);
        });
        AtomicReference<String> targetName2 = new AtomicReference<>();
        AtomicReference<List<String>> targetDelGroups = new AtomicReference<>();
        mockClient.deleteGroupFromGroup = ((u, g) -> {
            targetName2.set(u);
            targetDelGroups.set(g);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(GROUP_OBJECT_CLASS, new Uid(key, new Name(currentName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(key, targetUid.get().getUidValue());

        assertEquals(currentName, targetName1.get());
        assertEquals(addGroups, targetAddGroups.get());

        assertEquals(currentName, targetName2.get());
        assertEquals(delGroups, targetDelGroups.get());
    }

    @Test
    void updateGroupButNotFound() {
        // Given
        String key = "foo";
        String currentName = "foo";
        String desc = "This is foo group.";

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("description", desc));

        mockClient.getGroupByUid = ((u) -> {
            throw new UnknownUidException();
        });

        // When
        Throwable expect = null;
        try {
            connector.updateDelta(GROUP_OBJECT_CLASS, new Uid(key, new Name(currentName)), modifications, new OperationOptionsBuilder().build());
        } catch (Throwable t) {
            expect = t;
        }

        // Then
        assertNotNull(expect);
        assertTrue(expect instanceof UnknownUidException);
    }

    @Test
    void getGroupByUid() {
        // Given
        String key = "foo";
        String currentName = "foo";
        String desc = "This is foo group.";
        boolean active = true;
        List<String> groups = list("group1", "group2");

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getGroupByUid = ((u) -> {
            targetUid.set(u);

            GroupEntity result = new GroupEntity(currentName, desc, GroupType.GROUP, active);
            return result;
        });
        AtomicReference<String> targetName = new AtomicReference<>();
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        mockClient.getGroupsForGroup = ((n, size) -> {
            targetName.set(n);
            targetPageSize.set(size);

            return groups;
        });

        // When
        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS, new Uid(key, new Name(currentName)), defaultGetOperation());

        // Then
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(currentName, result.getName().getNameValue());
        assertEquals(desc, singleAttr(result, "description"));
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertNull(result.getAttributeByName("groups"), "Unexpected returned groups even if not requested");
        assertNull(targetName.get());
        assertNull(targetPageSize.get());
    }

    @Test
    void getGroupByUidWithAttributes() {
        // Apply custom configuration for this test
        configuration.setGroupAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String key = "foo";
        String currentName = "foo";
        String desc = "This is foo group.";
        boolean active = true;
        String custom1 = "abc";
        List<String> custom2 = list("123", "456");

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getGroupByUid = ((u) -> {
            targetUid.set(u);

            GroupEntity result = new GroupEntity(currentName, desc, GroupType.GROUP, active);
            List<MultiValuedAttributeEntity> customAttrs = new ArrayList<>();
            customAttrs.add(new MultiValuedAttributeEntity("custom1", list(custom1)));
            customAttrs.add(new MultiValuedAttributeEntity("custom2", custom2));
            result.setAttributes(new MultiValuedAttributeEntityList(customAttrs));
            return result;
        });

        // When
        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS, new Uid(key, new Name(currentName)), defaultGetOperation());

        // Then
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(currentName, result.getName().getNameValue());
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertEquals(custom1, singleAttr(result, "attributes.custom1"));
        assertEquals(custom2, multiAttr(result, "attributes.custom2"));
    }

    @Test
    void getGroupByUidWithEmpty() {
        // Apply custom configuration for this test
        configuration.setUserAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String key = "foo";
        String currentName = "foo";
        boolean active = true;

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getGroupByUid = ((u) -> {
            targetUid.set(u);

            GroupEntity result = new GroupEntity(currentName, null, GroupType.GROUP, active);
            return result;
        });

        // When
        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS, new Uid(key, new Name(currentName)), defaultGetOperation());

        // Then
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(currentName, result.getName().getNameValue());
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));

        Set<Attribute> attributes = result.getAttributes();
        assertEquals(3, attributes.size());
    }

    @Test
    void getGroupByUidWithGroupsButNoOperation() {
        // Given
        String key = "foo";
        String currentName = "foo";
        boolean active = true;
        List<String> groups = list("group1", "group2");

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getGroupByUid = ((u) -> {
            targetUid.set(u);

            GroupEntity result = new GroupEntity(currentName, null, GroupType.GROUP, active);
            return result;
        });
        AtomicReference<String> targetName = new AtomicReference<>();
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        mockClient.getGroupsForGroup = ((u, size) -> {
            targetName.set(u);
            targetPageSize.set(size);

            return groups;
        });

        // When
        // No operation options
        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS, new Uid(key, new Name(currentName)), new OperationOptionsBuilder().build());

        // Then
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(currentName, result.getName().getNameValue());
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertNull(result.getAttributeByName("groups"), "Unexpected returned groups even if not requested");
        assertNull(targetName.get());
        assertNull(targetPageSize.get());
    }

    @Test
    void getGroupByUidWithGroups() {
        // Given
        String key = "foo";
        String currentName = "foo";
        boolean active = true;
        List<String> groups = list("group1", "group2");

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getGroupByUid = ((u) -> {
            targetUid.set(u);

            GroupEntity result = new GroupEntity(currentName, null, GroupType.GROUP, active);
            return result;
        });
        AtomicReference<String> targetName = new AtomicReference<>();
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        mockClient.getGroupsForGroup = ((u, size) -> {
            targetName.set(u);
            targetPageSize.set(size);

            return groups;
        });

        // When
        // Request "groups"
        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS, new Uid(key, new Name(currentName)), defaultGetOperation("groups"));

        // Then
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(currentName, result.getName().getNameValue());
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertEquals(groups, multiAttr(result, "groups"));
        assertEquals(currentName, targetName.get());
        assertEquals(50, targetPageSize.get(), "Not default page size in the configuration");
    }

    @Test
    void getGroupByUidWithGroupsWithIgnoreGroup() {
        // Apply custom configuration for this test
        configuration.setIgnoreGroup(new String[]{"Crowd-Administrators"});
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String key = "foo";
        String currentName = "foo";
        boolean active = true;
        List<String> groups = list("group1", "crowd-administrators", "group2");

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getGroupByUid = ((u) -> {
            targetUid.set(u);

            GroupEntity result = new GroupEntity(currentName, null, GroupType.GROUP, active);
            return result;
        });
        AtomicReference<String> targetName = new AtomicReference<>();
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        mockClient.getGroupsForGroup = ((u, size) -> {
            targetName.set(u);
            targetPageSize.set(size);

            return groups;
        });

        // When
        // Request "groups"
        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS, new Uid(key, new Name(currentName)), defaultGetOperation("groups"));

        // Then
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(currentName, result.getName().getNameValue());
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertEquals(list("group1", "group2"), multiAttr(result, "groups"));
        assertEquals(currentName, targetName.get());
        assertEquals(50, targetPageSize.get(), "Not default page size in the configuration");
    }

    @Test
    void getGroupByName() {
        // Given
        String key = "foo";
        String currentName = "foo";
        String desc = "This is foo group.";
        boolean active = true;

        AtomicReference<Name> targetName = new AtomicReference<>();
        mockClient.getGroupByName = ((u) -> {
            targetName.set(u);

            GroupEntity result = new GroupEntity(currentName, desc, GroupType.GROUP, active);
            return result;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(GROUP_OBJECT_CLASS, FilterBuilder.equalTo(new Name(currentName)), handler, defaultSearchOperation());

        // Then
        assertEquals(1, results.size());
        ConnectorObject result = results.get(0);
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(currentName, result.getName().getNameValue());
        assertEquals(desc, singleAttr(result, "description"));
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertNull(result.getAttributeByName("groups"), "Unexpected returned groups even if not requested");
    }

    @Test
    void getGroupByNameWithGroupsWithPartialAttributeValues() {
        // Given
        String key = "foo";
        String currentName = "foo";
        String desc = "This is foo group.";
        boolean active = true;

        AtomicReference<Name> targetName = new AtomicReference<>();
        mockClient.getGroupByName = ((u) -> {
            targetName.set(u);

            GroupEntity result = new GroupEntity(currentName, desc, GroupType.GROUP, active);
            return result;
        });

        // When
        // Request "groups", but request partial attribute values
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(GROUP_OBJECT_CLASS, FilterBuilder.equalTo(new Name(currentName)), handler, defaultSearchOperation("groups"));

        // Then
        assertEquals(1, results.size());
        ConnectorObject result = results.get(0);
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(currentName, result.getName().getNameValue());
        assertEquals(desc, singleAttr(result, "description"));
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertTrue(isIncompleteAttribute(result.getAttributeByName("groups")));
    }

    @Test
    void getGroups() {
        // Given
        String key = "foo";
        String currentName = "foo";
        String desc = "This is foo group.";
        boolean active = true;

        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getGroups = ((h, size, offset) -> {
            targetPageSize.set(size);
            targetOffset.set(offset);

            GroupEntity result = new GroupEntity(currentName, desc, GroupType.GROUP, active);
            h.handle(result);

            return 1;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(GROUP_OBJECT_CLASS, null, handler, defaultSearchOperation());

        // Then
        assertEquals(1, results.size());
        ConnectorObject result = results.get(0);
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(currentName, result.getName().getNameValue());
        assertEquals(desc, singleAttr(result, "description"));
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertNull(result.getAttributeByName("groups"), "Unexpected returned groups even if not requested");

        assertEquals(20, targetPageSize.get(), "Not page size in the operation option");
        assertEquals(1, targetOffset.get());
    }

    @Test
    void getGroupsZero() {
        // Given
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getGroups = ((h, size, offset) -> {
            targetPageSize.set(size);
            targetOffset.set(offset);

            return 0;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(GROUP_OBJECT_CLASS, null, handler, defaultSearchOperation());

        // Then
        assertEquals(0, results.size());
        assertEquals(20, targetPageSize.get(), "Not default page size in the configuration");
        assertEquals(1, targetOffset.get());
    }

    @Test
    void getGroupsTwo() {
        // Given
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getGroups = ((h, size, offset) -> {
            targetPageSize.set(size);
            targetOffset.set(offset);

            GroupEntity result = new GroupEntity("group1", null, GroupType.GROUP, true);
            h.handle(result);

            result = new GroupEntity("group2", null, GroupType.GROUP, true);
            h.handle(result);

            return 2;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(GROUP_OBJECT_CLASS, null, handler, defaultSearchOperation());

        // Then
        assertEquals(2, results.size());

        ConnectorObject result = results.get(0);
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals("group1", result.getUid().getUidValue());
        assertEquals("group1", result.getName().getNameValue());

        result = results.get(1);
        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals("group2", result.getUid().getUidValue());
        assertEquals("group2", result.getName().getNameValue());

        assertEquals(20, targetPageSize.get(), "Not default page size in the configuration");
        assertEquals(1, targetOffset.get());
    }

    @Test
    void deleteGroup() {
        // Given
        String key = "foo";
        String currentName = "foo";

        AtomicReference<Uid> deleted = new AtomicReference<>();
        mockClient.deleteGroup = ((uid) -> {
            deleted.set(uid);
        });

        // When
        connector.delete(GROUP_OBJECT_CLASS, new Uid(key, new Name(currentName)), new OperationOptionsBuilder().build());

        // Then
        assertEquals(key, deleted.get().getUidValue());
        assertEquals(currentName, deleted.get().getNameHintValue());
    }
}
