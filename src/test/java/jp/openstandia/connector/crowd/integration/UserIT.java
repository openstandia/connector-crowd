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

import jp.openstandia.connector.crowd.CrowdUserHandler;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class UserIT extends AbstractIntegrationTest {

    private static final ObjectClass USER_OBJECT_CLASS = CrowdUserHandler.USER_OBJECT_CLASS;

    // --- Create ---

    @Test
    void addUser() {
        String userName = "foo";
        String email = "foo@example.com";
        String displayName = "Foo Bar";
        String firstName = "Foo";
        String lastName = "Bar";
        String password = "secret";

        // Create groups first
        connector.create(new ObjectClass("group"),
                buildGroupAttrs("group1", "Group 1", true), new OperationOptionsBuilder().build());
        connector.create(new ObjectClass("group"),
                buildGroupAttrs("group2", "Group 2", true), new OperationOptionsBuilder().build());

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.buildPassword(password.toCharArray()));
        attrs.add(AttributeBuilder.build("email", email));
        attrs.add(AttributeBuilder.build("display-name", displayName));
        attrs.add(AttributeBuilder.build("first-name", firstName));
        attrs.add(AttributeBuilder.build("last-name", lastName));
        attrs.add(AttributeBuilder.build("groups", list("group1", "group2")));

        Uid uid = connector.create(USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        assertNotNull(uid);
        assertNotNull(uid.getUidValue());
        assertEquals(userName, uid.getNameHintValue());

        // Verify by fetching the user
        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name(userName)), defaultGetOperation("groups"));

        assertEquals(userName, result.getName().getNameValue());
        assertEquals(email, singleAttr(result, "email"));
        assertEquals(displayName, singleAttr(result, "display-name"));
        assertEquals(firstName, singleAttr(result, "first-name"));
        assertEquals(lastName, singleAttr(result, "last-name"));
        assertEquals(true, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertNotNull(singleAttr(result, "created-date"));
        assertNotNull(singleAttr(result, "updated-date"));

        List<Object> groups = multiAttr(result, "groups");
        assertEquals(set("group1", "group2"), new HashSet<>(groups));
    }

    @Test
    void addUserWithAttributes() {
        configuration.setUserAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        String userName = "foo";
        String custom1 = "abc";
        List<String> custom2 = list("123", "456");

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("attributes.custom1", custom1));
        attrs.add(AttributeBuilder.build("attributes.custom2", custom2));

        Uid uid = connector.create(USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        assertNotNull(uid);
        assertEquals(userName, uid.getNameHintValue());

        // Verify custom attributes
        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name(userName)), defaultGetOperation());

        assertEquals(custom1, singleAttr(result, "attributes.custom1"));
        assertEquals(custom2, multiAttr(result, "attributes.custom2"));
    }

    @Test
    void addUserWithInactive() {
        String userName = "foo";

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(false));

        Uid uid = connector.create(USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        assertNotNull(uid);

        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name(userName)), defaultGetOperation());

        assertEquals(false, singleAttr(result, OperationalAttributes.ENABLE_NAME));
    }

    @Test
    void addUserButAlreadyExists() {
        String userName = "foo";

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.buildPassword("secret".toCharArray()));

        connector.create(USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        assertThrows(AlreadyExistsException.class, () -> {
            connector.create(USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());
        });
    }

    // --- Update ---

    @Test
    void updateUser() {
        // Create user first
        Uid uid = createTestUser("hoge", "hoge@example.com", "Hoge", "First", "Last");

        // Create groups
        connector.create(new ObjectClass("group"),
                buildGroupAttrs("group1", null, true), new OperationOptionsBuilder().build());
        connector.create(new ObjectClass("group"),
                buildGroupAttrs("group2", null, true), new OperationOptionsBuilder().build());

        String newDisplayName = "Foo Bar";
        String newFirstName = "Foo";
        String newLastName = "Bar";
        String newPassword = "newsecret";
        String newUserName = "foo";

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(Name.NAME, newUserName));
        modifications.add(AttributeDeltaBuilder.build("display-name", newDisplayName));
        modifications.add(AttributeDeltaBuilder.build("first-name", newFirstName));
        modifications.add(AttributeDeltaBuilder.build("last-name", newLastName));
        modifications.add(AttributeDeltaBuilder.buildPassword(newPassword.toCharArray()));
        modifications.add(AttributeDeltaBuilder.buildEnabled(true));
        modifications.add(AttributeDeltaBuilder.build("groups", list("group1", "group2"), null));

        Set<AttributeDelta> affected = connector.updateDelta(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("hoge")), modifications, new OperationOptionsBuilder().build());

        assertNull(affected);

        // Verify by fetching with new name
        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name(newUserName)), defaultGetOperation("groups"));

        assertEquals(newUserName, result.getName().getNameValue());
        assertEquals(newDisplayName, singleAttr(result, "display-name"));
        assertEquals(newFirstName, singleAttr(result, "first-name"));
        assertEquals(newLastName, singleAttr(result, "last-name"));
        assertEquals(true, singleAttr(result, OperationalAttributes.ENABLE_NAME));

        List<Object> groups = multiAttr(result, "groups");
        assertEquals(set("group1", "group2"), new HashSet<>(groups));
    }

    @Test
    void updateUserWithInactive() {
        Uid uid = createTestUser("foo", "foo@example.com", "Foo", "Foo", "Bar");

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.buildEnabled(false));

        connector.updateDelta(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("foo")), modifications, new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("foo")), defaultGetOperation());

        assertEquals(false, singleAttr(result, OperationalAttributes.ENABLE_NAME));
    }

    @Test
    void updateUserWithAttributesAdd() {
        configuration.setUserAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        Uid uid = createTestUser("foo", "foo@example.com", "Foo", "Foo", "Bar");

        String custom1 = "abc";
        List<String> custom2 = list("123", "456");

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("attributes.custom1", custom1));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom2", custom2, null));

        connector.updateDelta(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("foo")), modifications, new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("foo")), defaultGetOperation());

        assertEquals(custom1, singleAttr(result, "attributes.custom1"));
        assertEquals(asSet(custom2), asSet(multiAttr(result, "attributes.custom2")));
    }

    @Test
    void updateUserWithAttributesUpdate() {
        configuration.setUserAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Create user with initial custom attributes
        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name("foo"));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("attributes.custom1", "xyz"));
        attrs.add(AttributeBuilder.build("attributes.custom2", list("123", "456")));

        Uid uid = connector.create(USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Update: replace custom1, add 789 and remove 123 from custom2
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("attributes.custom1", "abc"));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom2", list("789"), list("123")));

        connector.updateDelta(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("foo")), modifications, new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("foo")), defaultGetOperation());

        assertEquals("abc", singleAttr(result, "attributes.custom1"));
        assertEquals(set("456", "789"), asSet(multiAttr(result, "attributes.custom2")));
    }

    @Test
    void updateUserWithNoValues() {
        configuration.setUserAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Create user with attributes
        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name("foo"));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("email", "foo@example.com"));
        attrs.add(AttributeBuilder.build("display-name", "Foo Bar"));
        attrs.add(AttributeBuilder.build("first-name", "Foo"));
        attrs.add(AttributeBuilder.build("last-name", "Bar"));
        attrs.add(AttributeBuilder.build("attributes.custom1", "abc"));
        attrs.add(AttributeBuilder.build("attributes.custom2", list("123", "789")));

        Uid uid = connector.create(USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Remove values
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("display-name", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("first-name", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("last-name", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom1", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom2", null, list("123", "789")));

        connector.updateDelta(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("foo")), modifications, new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("foo")), defaultGetOperation());

        assertEquals("foo@example.com", singleAttr(result, "email"));
        assertEquals(true, singleAttr(result, OperationalAttributes.ENABLE_NAME));
    }

    @Test
    void updateUserGroups() {
        // Create groups
        connector.create(new ObjectClass("group"),
                buildGroupAttrs("group1", null, true), new OperationOptionsBuilder().build());
        connector.create(new ObjectClass("group"),
                buildGroupAttrs("group2", null, true), new OperationOptionsBuilder().build());
        connector.create(new ObjectClass("group"),
                buildGroupAttrs("group3", null, true), new OperationOptionsBuilder().build());
        connector.create(new ObjectClass("group"),
                buildGroupAttrs("group4", null, true), new OperationOptionsBuilder().build());

        // Create user with group3, group4
        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name("foo"));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("groups", list("group3", "group4")));
        Uid uid = connector.create(USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Add group1, group2 and remove group3, group4
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("groups", list("group1", "group2"), list("group3", "group4")));

        connector.updateDelta(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("foo")), modifications, new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("foo")), defaultGetOperation("groups"));

        List<Object> groups = multiAttr(result, "groups");
        assertEquals(set("group1", "group2"), new HashSet<>(groups));
    }

    @Test
    void updateUserButNotFound() {
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("display-name", "Foo Bar"));

        assertThrows(UnknownUidException.class, () -> {
            connector.updateDelta(USER_OBJECT_CLASS,
                    new Uid("nonexistent-key", new Name("nonexistent")), modifications, new OperationOptionsBuilder().build());
        });
    }

    // --- Read ---

    @Test
    void getUserByUid() {
        Uid uid = createTestUser("foo", "foo@example.com", "Foo Bar", "Foo", "Bar");

        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("foo")), defaultGetOperation());

        assertEquals(USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(uid.getUidValue(), result.getUid().getUidValue());
        assertEquals("foo", result.getName().getNameValue());
        assertEquals("foo@example.com", singleAttr(result, "email"));
        assertEquals("Foo Bar", singleAttr(result, "display-name"));
        assertEquals("Foo", singleAttr(result, "first-name"));
        assertEquals("Bar", singleAttr(result, "last-name"));
        assertEquals(true, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertNotNull(singleAttr(result, "created-date"));
        assertNotNull(singleAttr(result, "updated-date"));
        assertNull(result.getAttributeByName("groups"));
    }

    @Test
    void getUserByUidWithAttributes() {
        configuration.setUserAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name("foo"));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("attributes.custom1", "abc"));
        attrs.add(AttributeBuilder.build("attributes.custom2", list("123", "456")));

        Uid uid = connector.create(USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("foo")), defaultGetOperation());

        assertEquals("abc", singleAttr(result, "attributes.custom1"));
        assertEquals(list("123", "456"), multiAttr(result, "attributes.custom2"));
    }

    @Test
    void getUserByUidWithGroups() {
        connector.create(new ObjectClass("group"),
                buildGroupAttrs("group1", null, true), new OperationOptionsBuilder().build());
        connector.create(new ObjectClass("group"),
                buildGroupAttrs("group2", null, true), new OperationOptionsBuilder().build());

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name("foo"));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.build("email", "foo@example.com"));
        attrs.add(AttributeBuilder.build("groups", list("group1", "group2")));
        Uid uid = connector.create(USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("foo")), defaultGetOperation("groups"));

        List<Object> groups = multiAttr(result, "groups");
        assertEquals(set("group1", "group2"), new HashSet<>(groups));
    }

    @Test
    void getUserByName() {
        Uid uid = createTestUser("foo", "foo@example.com", "Foo Bar", "Foo", "Bar");

        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(USER_OBJECT_CLASS, FilterBuilder.equalTo(new Name("foo")), handler, defaultSearchOperation());

        assertEquals(1, results.size());
        ConnectorObject result = results.get(0);
        assertEquals(uid.getUidValue(), result.getUid().getUidValue());
        assertEquals("foo", result.getName().getNameValue());
        assertEquals("foo@example.com", singleAttr(result, "email"));
        assertEquals("Foo Bar", singleAttr(result, "display-name"));
    }

    // --- Search ---

    @Test
    void getUsers() {
        createTestUser("foo", "foo@example.com", "Foo Bar", "Foo", "Bar");

        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(USER_OBJECT_CLASS, null, handler, defaultSearchOperation());

        assertEquals(1, results.size());
        ConnectorObject result = results.get(0);
        assertEquals("foo", result.getName().getNameValue());
        assertEquals("foo@example.com", singleAttr(result, "email"));
    }

    @Test
    void getUsersZero() {
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(USER_OBJECT_CLASS, null, handler, defaultSearchOperation());

        assertEquals(0, results.size());
    }

    @Test
    void getUsersTwo() {
        createTestUser("user1", "user1@example.com", "User 1", "User", "One");
        createTestUser("user2", "user2@example.com", "User 2", "User", "Two");

        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(USER_OBJECT_CLASS, null, handler, defaultSearchOperation());

        assertEquals(2, results.size());
    }

    // --- Delete ---

    @Test
    void deleteUser() {
        Uid uid = createTestUser("foo", "foo@example.com", "Foo", "Foo", "Bar");

        connector.delete(USER_OBJECT_CLASS, new Uid(uid.getUidValue(), new Name("foo")), new OperationOptionsBuilder().build());

        // Verify user is gone
        ConnectorObject result = connector.getObject(USER_OBJECT_CLASS,
                new Uid(uid.getUidValue(), new Name("foo")), defaultGetOperation());
        assertNull(result);
    }

    // --- Helpers ---

    private Uid createTestUser(String name, String email, String displayName, String firstName, String lastName) {
        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(name));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.buildPassword("password".toCharArray()));
        attrs.add(AttributeBuilder.build("email", email));
        attrs.add(AttributeBuilder.build("display-name", displayName));
        attrs.add(AttributeBuilder.build("first-name", firstName));
        attrs.add(AttributeBuilder.build("last-name", lastName));
        return connector.create(USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());
    }

    private Set<Attribute> buildGroupAttrs(String name, String description, boolean active) {
        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(name));
        attrs.add(AttributeBuilder.buildEnabled(active));
        if (description != null) {
            attrs.add(AttributeBuilder.build("description", description));
        }
        return attrs;
    }
}
