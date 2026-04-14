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

import jp.openstandia.connector.crowd.CrowdGroupHandler;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GroupIT extends AbstractIntegrationTest {

    private static final ObjectClass GROUP_OBJECT_CLASS = CrowdGroupHandler.GROUP_OBJECT_CLASS;

    // --- Create ---

    @Test
    void addGroup() {
        String groupName = "foo";
        String desc = "This is foo group.";

        // Create parent groups first
        createTestGroup("p1", null);
        createTestGroup("p2", null);

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(groupName));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("description", desc));
        attrs.add(AttributeBuilder.build("groups", list("p1", "p2")));

        Uid uid = connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        assertNotNull(uid);
        assertEquals(groupName, uid.getUidValue());
        assertEquals(groupName, uid.getNameHintValue());

        // Verify
        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid(groupName, new Name(groupName)), defaultGetOperation("groups"));

        assertEquals(groupName, result.getName().getNameValue());
        assertEquals(desc, singleAttr(result, "description"));
        assertEquals(true, singleAttr(result, OperationalAttributes.ENABLE_NAME));

        List<Object> groups = multiAttr(result, "groups");
        assertEquals(set("p1", "p2"), new HashSet<>(groups));
    }

    @Test
    void addGroupWithAttributes() {
        configuration.setGroupAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        String groupName = "foo";
        String custom1 = "abc";
        List<String> custom2 = list("123", "456");

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(groupName));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("attributes.custom1", custom1));
        attrs.add(AttributeBuilder.build("attributes.custom2", custom2));

        Uid uid = connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        assertNotNull(uid);
        assertEquals(groupName, uid.getUidValue());

        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid(groupName, new Name(groupName)), defaultGetOperation());

        assertEquals(custom1, singleAttr(result, "attributes.custom1"));
        assertEquals(custom2, multiAttr(result, "attributes.custom2"));
    }

    @Test
    void addGroupWithInactive() {
        String groupName = "foo";

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(groupName));
        attrs.add(AttributeBuilder.buildEnabled(false));
        attrs.add(AttributeBuilder.build("description", "desc"));

        Uid uid = connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        assertNotNull(uid);

        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid(groupName, new Name(groupName)), defaultGetOperation());

        assertEquals(false, singleAttr(result, OperationalAttributes.ENABLE_NAME));
    }

    @Test
    void addGroupButAlreadyExists() {
        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name("foo"));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("description", "desc"));

        connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        assertThrows(AlreadyExistsException.class, () -> {
            connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());
        });
    }

    // --- Update ---

    @Test
    void updateGroup() {
        createTestGroup("foo", "old desc");
        createTestGroup("p1", null);
        createTestGroup("p2", null);

        String newDesc = "This is foo group.";

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("description", newDesc));
        modifications.add(AttributeDeltaBuilder.build("groups", list("p1", "p2"), null));

        Set<AttributeDelta> affected = connector.updateDelta(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), modifications, new OperationOptionsBuilder().build());

        assertNull(affected);

        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), defaultGetOperation("groups"));

        assertEquals(newDesc, singleAttr(result, "description"));
        List<Object> groups = multiAttr(result, "groups");
        assertEquals(set("p1", "p2"), new HashSet<>(groups));
    }

    @Test
    void updateGroupWithInactive() {
        createTestGroup("foo", "desc");

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.buildEnabled(false));

        connector.updateDelta(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), modifications, new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), defaultGetOperation());

        assertEquals(false, singleAttr(result, OperationalAttributes.ENABLE_NAME));
    }

    @Test
    void updateGroupWithAttributesAdd() {
        configuration.setGroupAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        createTestGroup("foo", null);

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("attributes.custom1", "abc"));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom2", list("123", "456"), null));

        connector.updateDelta(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), modifications, new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), defaultGetOperation());

        assertEquals("abc", singleAttr(result, "attributes.custom1"));
        assertEquals(set("123", "456"), asSet(multiAttr(result, "attributes.custom2")));
    }

    @Test
    void updateGroupWithAttributesUpdate() {
        configuration.setGroupAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Create with initial attributes
        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name("foo"));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("attributes.custom1", "xyz"));
        attrs.add(AttributeBuilder.build("attributes.custom2", list("123", "456")));
        connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Update: replace custom1, add 789 remove 123 from custom2
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("attributes.custom1", "abc"));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom2", list("789"), list("123")));

        connector.updateDelta(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), modifications, new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), defaultGetOperation());

        assertEquals("abc", singleAttr(result, "attributes.custom1"));
        assertEquals(set("456", "789"), asSet(multiAttr(result, "attributes.custom2")));
    }

    @Test
    void updateGroupWithNoValues() {
        configuration.setGroupAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Create with attributes
        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name("foo"));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("description", "desc"));
        attrs.add(AttributeBuilder.build("attributes.custom1", "abc"));
        attrs.add(AttributeBuilder.build("attributes.custom2", list("123", "789")));
        connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Remove values
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("description", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom1", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom2", null, list("123", "789")));

        connector.updateDelta(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), modifications, new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), defaultGetOperation());

        assertEquals(true, singleAttr(result, OperationalAttributes.ENABLE_NAME));
    }

    @Test
    void updateGroupGroups() {
        createTestGroup("foo", null);
        createTestGroup("group1", null);
        createTestGroup("group2", null);
        createTestGroup("group3", null);
        createTestGroup("group4", null);

        // Add foo as child of group2, group3, group4
        Set<AttributeDelta> init = new HashSet<>();
        init.add(AttributeDeltaBuilder.build("groups", list("group2", "group3", "group4"), null));
        connector.updateDelta(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), init, new OperationOptionsBuilder().build());

        // Add group1, remove group3, group4 (group2 should remain unchanged)
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("groups", list("group1"), list("group3", "group4")));

        connector.updateDelta(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), modifications, new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), defaultGetOperation("groups"));

        List<Object> groups = multiAttr(result, "groups");
        assertEquals(set("group1", "group2"), new HashSet<>(groups));
    }

    @Test
    void updateGroupButNotFound() {
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("description", "desc"));

        assertThrows(UnknownUidException.class, () -> {
            connector.updateDelta(GROUP_OBJECT_CLASS,
                    new Uid("nonexistent", new Name("nonexistent")), modifications, new OperationOptionsBuilder().build());
        });
    }

    // --- Read ---

    @Test
    void getGroupByUid() {
        createTestGroup("foo", "This is foo group.");

        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), defaultGetOperation());

        assertEquals(GROUP_OBJECT_CLASS, result.getObjectClass());
        assertEquals("foo", result.getUid().getUidValue());
        assertEquals("foo", result.getName().getNameValue());
        assertEquals("This is foo group.", singleAttr(result, "description"));
        assertEquals(true, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertNull(result.getAttributeByName("groups"));
    }

    @Test
    void getGroupByUidWithAttributes() {
        configuration.setGroupAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name("foo"));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("description", "desc"));
        attrs.add(AttributeBuilder.build("attributes.custom1", "abc"));
        attrs.add(AttributeBuilder.build("attributes.custom2", list("123", "456")));
        connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), defaultGetOperation());

        assertEquals("abc", singleAttr(result, "attributes.custom1"));
        assertEquals(list("123", "456"), multiAttr(result, "attributes.custom2"));
    }

    @Test
    void getGroupByUidWithGroups() {
        createTestGroup("parent1", null);
        createTestGroup("parent2", null);

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name("foo"));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("groups", list("parent1", "parent2")));
        connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), defaultGetOperation("groups"));

        List<Object> groups = multiAttr(result, "groups");
        assertEquals(set("parent1", "parent2"), new HashSet<>(groups));
    }

    @Test
    void getGroupByName() {
        createTestGroup("foo", "This is foo group.");

        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(GROUP_OBJECT_CLASS, FilterBuilder.equalTo(new Name("foo")), handler, defaultSearchOperation());

        assertEquals(1, results.size());
        ConnectorObject result = results.get(0);
        assertEquals("foo", result.getUid().getUidValue());
        assertEquals("foo", result.getName().getNameValue());
        assertEquals("This is foo group.", singleAttr(result, "description"));
    }

    // --- Search ---

    @Test
    void getGroups() {
        createTestGroup("foo", "desc");

        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(GROUP_OBJECT_CLASS, null, handler, defaultSearchOperation());

        assertEquals(1, results.size());
        ConnectorObject result = results.get(0);
        assertEquals("foo", result.getUid().getUidValue());
        assertEquals("foo", result.getName().getNameValue());
    }

    @Test
    void getGroupsZero() {
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(GROUP_OBJECT_CLASS, null, handler, defaultSearchOperation());

        assertEquals(0, results.size());
    }

    @Test
    void getGroupsTwo() {
        createTestGroup("group1", null);
        createTestGroup("group2", null);

        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(GROUP_OBJECT_CLASS, null, handler, defaultSearchOperation());

        assertEquals(2, results.size());
    }

    // --- Delete ---

    @Test
    void deleteGroup() {
        createTestGroup("foo", "desc");

        connector.delete(GROUP_OBJECT_CLASS, new Uid("foo", new Name("foo")), new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid("foo", new Name("foo")), defaultGetOperation());
        assertNull(result);
    }

    // --- Lifecycle ---

    @Test
    void groupLifecycle() {
        // Create
        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name("lifecycle-group"));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("description", "initial desc"));
        Uid uid = connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        assertNotNull(uid);
        assertEquals("lifecycle-group", uid.getUidValue());

        // Get by UID
        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("lifecycle-group")), defaultGetOperation());
        assertEquals("initial desc", singleAttr(result, "description"));

        // Update
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("description", "updated desc"));
        modifications.add(AttributeDeltaBuilder.buildEnabled(false));

        connector.updateDelta(GROUP_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("lifecycle-group")), modifications, new OperationOptionsBuilder().build());

        result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("lifecycle-group")), defaultGetOperation());
        assertEquals("updated desc", singleAttr(result, "description"));
        assertEquals(false, singleAttr(result, OperationalAttributes.ENABLE_NAME));

        // Delete
        connector.delete(GROUP_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("lifecycle-group")), new OperationOptionsBuilder().build());

        result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("lifecycle-group")), defaultGetOperation());
        assertNull(result);
    }

    // --- Case insensitive ---

    @Test
    void getGroupByUidCaseInsensitive() {
        // Create group with mixed case
        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name("MyGroup"));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("description", "mixed case group"));
        Uid uid = connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Get by lowercase UID (MidPoint normalizes to lowercase due to STRING_CASE_IGNORE)
        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid("mygroup", new Name("mygroup")), defaultGetOperation());

        assertNotNull(result, "Should find group with case-insensitive lookup");
        assertEquals("mixed case group", singleAttr(result, "description"));
    }

    @Test
    void updateGroupCaseInsensitive() {
        // Create group with mixed case
        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name("TestGroup"));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("description", "original"));
        connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Update using lowercase UID
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("description", "updated"));

        connector.updateDelta(GROUP_OBJECT_CLASS,
                new Uid("testgroup", new Name("testgroup")), modifications, new OperationOptionsBuilder().build());

        // Verify update succeeded
        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid("testgroup", new Name("testgroup")), defaultGetOperation());
        assertNotNull(result);
        assertEquals("updated", singleAttr(result, "description"));
    }

    @Test
    void deleteGroupCaseInsensitive() {
        // Create group with mixed case
        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name("DeleteMe"));
        attrs.add(AttributeBuilder.buildEnabled(true));
        connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Delete using lowercase UID
        connector.delete(GROUP_OBJECT_CLASS,
                new Uid("deleteme", new Name("deleteme")), new OperationOptionsBuilder().build());

        // Verify deleted
        ConnectorObject result = connector.getObject(GROUP_OBJECT_CLASS,
                new Uid("deleteme", new Name("deleteme")), defaultGetOperation());
        assertNull(result);
    }

    // --- Helpers ---

    private Uid createTestGroup(String name, String description) {
        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(name));
        attrs.add(AttributeBuilder.buildEnabled(true));
        if (description != null) {
            attrs.add(AttributeBuilder.build("description", description));
        }
        return connector.create(GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());
    }
}
