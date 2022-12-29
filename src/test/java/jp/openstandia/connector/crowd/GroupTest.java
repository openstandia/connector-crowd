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
import com.atlassian.crowd.integration.rest.entity.UserEntity;
import com.atlassian.crowd.model.group.Group;
import com.atlassian.crowd.model.group.GroupWithAttributes;
import com.atlassian.crowd.model.user.User;
import jp.openstandia.connector.crowd.testutil.AbstractTest;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static jp.openstandia.connector.util.Utils.toZoneDateTime;
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
        Uid uid = connector.create(CrowdGroupHandler.GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

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
            Uid uid = connector.create(CrowdGroupHandler.GROUP_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());
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
        String currentGroupName = "hoge";

        String desc = "This is foo group.";
        List<String> groups = list("p1", "p2");

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("description", desc));
        modifications.add(AttributeDeltaBuilder.build("groups", groups, null));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getGroupByUid = ((u) -> {
            targetUid.set(u);

            GroupEntity current = GroupEntity.newMinimalInstance(currentGroupName);
            return current;
        });
        AtomicReference<Group> updated = new AtomicReference<>();
        mockClient.updateGroup = ((g) -> {
            updated.set(g);
        });
        AtomicReference<String> targetGroupName = new AtomicReference<>();
        AtomicReference<List<String>> targetAddGroups = new AtomicReference<>();
        mockClient.addGroupToGroup = ((n, g) -> {
            targetGroupName.set(n);
            targetAddGroups.set(g);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(CrowdGroupHandler.GROUP_OBJECT_CLASS, new Uid(currentGroupName, new Name(currentGroupName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(currentGroupName, targetUid.get().getUidValue());

        Group updatedGroup = updated.get();
        assertEquals(desc, updatedGroup.getDescription());

        assertEquals(currentGroupName, targetGroupName.get());
        assertEquals(groups, targetAddGroups.get());
    }

    @Test
    void updateUserWithInactive() {
        // Given
        String currentUserName = "foo";

        String key = "12345:abc";
        boolean active = false;

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.buildEnabled(active));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getUserByUid = ((u) -> {
            targetUid.set(u);

            UserEntity current = new UserEntity(currentUserName, null, null, null, null, null, true, key, null, null, false);
            return current;
        });
        AtomicReference<User> updated = new AtomicReference<>();
        mockClient.updateUser = ((user) -> {
            updated.set(user);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(currentUserName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(key, targetUid.get().getUidValue());

        User updatedUser = updated.get();
        assertEquals(active, updatedUser.isActive());
    }

    @Test
    void updateUserWithAttributesAdd() {
        // Apply custom configuration for this test
        configuration.setUserAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String currentUserName = "foo";

        String key = "12345:abc";
        String custom1 = "abc";
        List<String> custom2 = list("123", "456");

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("attributes.custom1", custom1));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom2", custom2, null));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getUserByUid = ((u) -> {
            targetUid.set(u);

            UserEntity current = UserEntity.newMinimalInstance(currentUserName);
            return current;
        });
        AtomicReference<String> targetUserName1 = new AtomicReference<>();
        AtomicReference<Map<String, Set<String>>> newAttrs = new AtomicReference<>();
        mockClient.updateUserAttributes = ((u, a) -> {
            targetUserName1.set(u);
            newAttrs.set(a);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(currentUserName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(key, targetUid.get().getUidValue());

        assertEquals(currentUserName, targetUserName1.get());
        assertNotNull(newAttrs.get());
        Map<String, Set<String>> updatedAttrs = newAttrs.get();
        assertEquals(set(custom1), updatedAttrs.get("custom1"));
        assertEquals(asSet(custom2), updatedAttrs.get("custom2"));
    }

    @Test
    void updateUserWithAttributesUpdate() {
        // Apply custom configuration for this test
        configuration.setUserAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String currentUserName = "foo";

        String key = "12345:abc";
        String custom1 = "abc";
        List<String> custom2Add = list("789");
        List<String> custom2Remove = list("123");

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("attributes.custom1", custom1));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom2", custom2Add, custom2Remove));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getUserByUid = ((u) -> {
            targetUid.set(u);

            UserEntity current = UserEntity.newMinimalInstance(currentUserName);
            List<MultiValuedAttributeEntity> attrs = new ArrayList<>();
            attrs.add(new MultiValuedAttributeEntity("custom1", list("xyz")));
            attrs.add(new MultiValuedAttributeEntity("custom2", list("123", "456")));
            current.setAttributes(new MultiValuedAttributeEntityList(attrs));
            return current;
        });
        AtomicReference<String> targetUserName1 = new AtomicReference<>();
        AtomicReference<Map<String, Set<String>>> newAttrs = new AtomicReference<>();
        mockClient.updateUserAttributes = ((u, a) -> {
            targetUserName1.set(u);
            newAttrs.set(a);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(currentUserName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(key, targetUid.get().getUidValue());

        assertEquals(currentUserName, targetUserName1.get());
        assertNotNull(newAttrs.get());
        Map<String, Set<String>> updatedAttrs = newAttrs.get();
        assertEquals(set(custom1), updatedAttrs.get("custom1"));
        assertEquals(set("456", "789"), updatedAttrs.get("custom2"));
    }

    @Test
    void updateUserWithNoValues() {
        // Apply custom configuration for this test
        configuration.setUserAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String currentUserName = "foo";

        String key = "12345:abc";
        String userName = "foo";
        String email = "foo@example.com";
        String displayName = "Foo Bar";
        String firstName = "Foo";
        String lastName = "Bar";
        boolean active = true;
        Date createdDate = Date.from(Instant.now());
        Date updatedDate = Date.from(Instant.now());
        String custom1 = "abc";
        List<String> custom2 = list("123", "789");

        Set<AttributeDelta> modifications = new HashSet<>();
        // IDM sets empty list to remove the single value
        modifications.add(AttributeDeltaBuilder.build("display-name", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("first-name", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("last-name", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom1", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("attributes.custom2", null, custom2));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getUserByUid = ((u) -> {
            targetUid.set(u);

            UserEntity current = new UserEntity(userName, firstName, lastName, displayName, email, null, active, key, createdDate, updatedDate, false);
            List<MultiValuedAttributeEntity> attrs = new ArrayList<>();
            attrs.add(new MultiValuedAttributeEntity("custom1", list(custom1)));
            attrs.add(new MultiValuedAttributeEntity("custom2", custom2));
            current.setAttributes(new MultiValuedAttributeEntityList(attrs));
            return current;
        });
        AtomicReference<User> updated = new AtomicReference<>();
        mockClient.updateUser = ((user) -> {
            updated.set(user);
        });
        AtomicReference<String> targetUserName1 = new AtomicReference<>();
        AtomicReference<Map<String, Set<String>>> newAttrs = new AtomicReference<>();
        mockClient.updateUserAttributes = ((u, a) -> {
            targetUserName1.set(u);
            newAttrs.set(a);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(currentUserName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(key, targetUid.get().getUidValue());

        User updatedUser = updated.get();
        assertEquals(userName, updatedUser.getName());
        assertEquals(email, updatedUser.getEmailAddress());
        assertNull(updatedUser.getDisplayName());
        assertNull(updatedUser.getFirstName());
        assertNull(updatedUser.getLastName());
        assertTrue(updatedUser.isActive());

        assertEquals(currentUserName, targetUserName1.get());
        assertNotNull(newAttrs.get());

        Map<String, Set<String>> updatedAttrs = newAttrs.get();
        assertEquals(2, updatedAttrs.size());
        assertTrue(updatedAttrs.get("custom1").isEmpty(), "Expected empty set to remove the attribute, but has value");
        assertTrue(updatedAttrs.get("custom2").isEmpty(), "Expected empty set to remove the attribute, but has value");
    }

    @Test
    void updateUserGroups() {
        // Given
        String currentUserName = "foo";

        String key = "12345:abc";
        List<String> addGroups = list("group1", "group2");
        List<String> delGroups = list("group3", "group4");

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("groups", addGroups, delGroups));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getUserByUid = ((u) -> {
            targetUid.set(u);

            UserEntity current = UserEntity.newMinimalInstance(currentUserName);
            return current;
        });
        AtomicReference<String> targetUserName2 = new AtomicReference<>();
        AtomicReference<List<String>> targetAddGroups = new AtomicReference<>();
        mockClient.addUserToGroup = ((u, g) -> {
            targetUserName2.set(u);
            targetAddGroups.set(g);
        });
        AtomicReference<String> targetUserName3 = new AtomicReference<>();
        AtomicReference<List<String>> targetDelGroups = new AtomicReference<>();
        mockClient.deleteUserFromGroup = ((u, g) -> {
            targetUserName3.set(u);
            targetDelGroups.set(g);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(currentUserName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(key, targetUid.get().getUidValue());

        assertEquals(currentUserName, targetUserName2.get());
        assertEquals(addGroups, targetAddGroups.get());

        assertEquals(currentUserName, targetUserName3.get());
        assertEquals(delGroups, targetDelGroups.get());
    }

    @Test
    void updateUserButNotFound() {
        // Given
        String currentUserName = "foo";

        String key = "12345:abc";
        String displayName = "Foo Bar";

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("display-name", displayName));

        mockClient.getUserByUid = ((u) -> {
            throw new UnknownUidException();
        });

        // When
        Throwable expect = null;
        try {
            connector.updateDelta(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(currentUserName)), modifications, new OperationOptionsBuilder().build());
        } catch (Throwable t) {
            expect = t;
        }

        // Then
        assertNotNull(expect);
        assertTrue(expect instanceof UnknownUidException);
    }

    @Test
    void getUserByUid() {
        // Given
        String key = "12345:abc";
        String userName = "foo";
        String email = "foo@example.com";
        String displayName = "Foo Bar";
        String firstName = "Foo";
        String lastName = "Bar";
        boolean active = true;
        Date createdDate = Date.from(Instant.now());
        Date updatedDate = Date.from(Instant.now());
        List<String> groups = list("group1", "group2");

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(false));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getUserByUid = ((u) -> {
            targetUid.set(u);

            UserEntity result = new UserEntity(userName, firstName, lastName, displayName, email, null, active, key, createdDate, updatedDate, false);
            return result;
        });
        AtomicReference<String> targetUserName = new AtomicReference<>();
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        mockClient.getGroupsForUser = ((u, size) -> {
            targetUserName.set(u);
            targetPageSize.set(size);

            return groups;
        });

        // When
        ConnectorObject result = connector.getObject(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(userName)), defaultGetOperation());

        // Then
        assertEquals(CrowdUserHandler.USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(userName, result.getName().getNameValue());
        assertEquals(email, singleAttr(result, "email"));
        assertEquals(displayName, singleAttr(result, "display-name"));
        assertEquals(firstName, singleAttr(result, "first-name"));
        assertEquals(lastName, singleAttr(result, "last-name"));
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertEquals(toZoneDateTime(createdDate), singleAttr(result, "created-date"));
        assertEquals(toZoneDateTime(updatedDate), singleAttr(result, "updated-date"));
        assertNull(result.getAttributeByName("groups"), "Unexpected returned groups even if not requested");
        assertNull(targetUserName.get());
        assertNull(targetPageSize.get());
    }

    @Test
    void getUserByUidWithAttributes() {
        // Apply custom configuration for this test
        configuration.setUserAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String key = "12345:abc";
        String userName = "foo";
        String custom1 = "abc";
        boolean active = true;
        List<String> custom2 = list("123", "456");

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(false));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getUserByUid = ((u) -> {
            targetUid.set(u);

            UserEntity result = new UserEntity(userName, null, null, null, null, null, active, key, null, null, false);
            List<MultiValuedAttributeEntity> customAttrs = new ArrayList<>();
            customAttrs.add(new MultiValuedAttributeEntity("custom1", list(custom1)));
            customAttrs.add(new MultiValuedAttributeEntity("custom2", custom2));
            result.setAttributes(new MultiValuedAttributeEntityList(customAttrs));
            return result;
        });

        // When
        ConnectorObject result = connector.getObject(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(userName)), defaultGetOperation());

        // Then
        assertEquals(CrowdUserHandler.USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(userName, result.getName().getNameValue());
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertEquals(custom1, singleAttr(result, "attributes.custom1"));
        assertEquals(custom2, multiAttr(result, "attributes.custom2"));
    }

    @Test
    void getUserByUidWithEmpty() {
        // Apply custom configuration for this test
        configuration.setUserAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String key = "12345:abc";
        String userName = "foo";
        boolean active = true;

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(false));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getUserByUid = ((u) -> {
            targetUid.set(u);

            UserEntity result = new UserEntity(userName, null, null, null, null, null, active, key, null, null, false);
            return result;
        });

        // When
        ConnectorObject result = connector.getObject(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(userName)), defaultGetOperation());

        // Then
        assertEquals(CrowdUserHandler.USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(userName, result.getName().getNameValue());
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));

        Set<Attribute> attributes = result.getAttributes();
        assertEquals(3, attributes.size());
    }

    @Test
    void getUserByUidWithGroupsButNoOperation() {
        // Given
        String key = "12345:abc";
        String userName = "foo";
        String email = "foo@example.com";
        String displayName = "Foo Bar";
        String firstName = "Foo";
        String lastName = "Bar";
        boolean active = true;
        Date createdDate = Date.from(Instant.now());
        Date updatedDate = Date.from(Instant.now());
        List<String> groups = list("group1", "group2");

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(false));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getUserByUid = ((u) -> {
            targetUid.set(u);

            UserEntity result = new UserEntity(userName, firstName, lastName, displayName, email, null, active, key, createdDate, updatedDate, false);
            return result;
        });
        AtomicReference<String> targetUserName = new AtomicReference<>();
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        mockClient.getGroupsForUser = ((u, size) -> {
            targetUserName.set(u);
            targetPageSize.set(size);

            return groups;
        });

        // When
        // No operation options
        ConnectorObject result = connector.getObject(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(userName)), new OperationOptionsBuilder().build());

        // Then
        assertEquals(CrowdUserHandler.USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(userName, result.getName().getNameValue());
        assertEquals(email, singleAttr(result, "email"));
        assertEquals(displayName, singleAttr(result, "display-name"));
        assertEquals(firstName, singleAttr(result, "first-name"));
        assertEquals(lastName, singleAttr(result, "last-name"));
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertEquals(toZoneDateTime(createdDate), singleAttr(result, "created-date"));
        assertEquals(toZoneDateTime(updatedDate), singleAttr(result, "updated-date"));
        assertNull(result.getAttributeByName("groups"), "Unexpected returned groups even if not requested");
        assertNull(targetUserName.get());
        assertNull(targetPageSize.get());
    }

    @Test
    void getUserByUidWithGroups() {
        // Given
        String key = "12345:abc";
        String userName = "foo";
        String email = "foo@example.com";
        String displayName = "Foo Bar";
        String firstName = "Foo";
        String lastName = "Bar";
        boolean active = true;
        Date createdDate = Date.from(Instant.now());
        Date updatedDate = Date.from(Instant.now());
        List<String> groups = list("group1", "group2");

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(false));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getUserByUid = ((u) -> {
            targetUid.set(u);

            UserEntity result = new UserEntity(userName, firstName, lastName, displayName, email, null, active, key, createdDate, updatedDate, false);
            return result;
        });
        AtomicReference<String> targetUserName = new AtomicReference<>();
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        mockClient.getGroupsForUser = ((u, size) -> {
            targetUserName.set(u);
            targetPageSize.set(size);

            return groups;
        });

        // When
        // Request "groups"
        ConnectorObject result = connector.getObject(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(userName)), defaultGetOperation("groups"));

        // Then
        assertEquals(CrowdUserHandler.USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(userName, result.getName().getNameValue());
        assertEquals(email, singleAttr(result, "email"));
        assertEquals(displayName, singleAttr(result, "display-name"));
        assertEquals(firstName, singleAttr(result, "first-name"));
        assertEquals(lastName, singleAttr(result, "last-name"));
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertEquals(toZoneDateTime(createdDate), singleAttr(result, "created-date"));
        assertEquals(toZoneDateTime(updatedDate), singleAttr(result, "updated-date"));
        assertEquals(groups, multiAttr(result, "groups"));
        assertEquals(userName, targetUserName.get());
        assertEquals(50, targetPageSize.get(), "Not default page size in the configuration");
    }

    @Test
    void getUserByName() {
        // Given
        String key = "12345:abc";
        String userName = "foo";
        String email = "foo@example.com";
        String displayName = "Foo Bar";
        String firstName = "Foo";
        String lastName = "Bar";
        boolean active = true;
        Date createdDate = Date.from(Instant.now());
        Date updatedDate = Date.from(Instant.now());

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(false));

        AtomicReference<Name> targetName = new AtomicReference<>();
        mockClient.getUserByName = ((u) -> {
            targetName.set(u);

            UserEntity result = new UserEntity(userName, firstName, lastName, displayName, email, null, active, key, createdDate, updatedDate, false);
            return result;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(CrowdUserHandler.USER_OBJECT_CLASS, FilterBuilder.equalTo(new Name(userName)), handler, defaultSearchOperation());

        // Then
        assertEquals(1, results.size());
        ConnectorObject result = results.get(0);
        assertEquals(CrowdUserHandler.USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(userName, result.getName().getNameValue());
        assertEquals(email, singleAttr(result, "email"));
        assertEquals(displayName, singleAttr(result, "display-name"));
        assertEquals(firstName, singleAttr(result, "first-name"));
        assertEquals(lastName, singleAttr(result, "last-name"));
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertEquals(toZoneDateTime(createdDate), singleAttr(result, "created-date"));
        assertEquals(toZoneDateTime(updatedDate), singleAttr(result, "updated-date"));
        assertNull(result.getAttributeByName("groups"), "Unexpected returned groups even if not requested");
    }

    @Test
    void getUserByNameWithGroups() {
        // Given
        String key = "12345:abc";
        String userName = "foo";
        String email = "foo@example.com";
        String displayName = "Foo Bar";
        String firstName = "Foo";
        String lastName = "Bar";
        boolean active = true;
        Date createdDate = Date.from(Instant.now());
        Date updatedDate = Date.from(Instant.now());
        List<String> groups = list("group1", "group2");

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(false));

        AtomicReference<Name> targetName = new AtomicReference<>();
        mockClient.getUserByName = ((u) -> {
            targetName.set(u);

            UserEntity result = new UserEntity(userName, firstName, lastName, displayName, email, null, active, key, createdDate, updatedDate, false);
            return result;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(CrowdUserHandler.USER_OBJECT_CLASS, FilterBuilder.equalTo(new Name(userName)), handler, defaultSearchOperation("groups"));

        // Then
        assertEquals(1, results.size());
        ConnectorObject result = results.get(0);
        assertEquals(CrowdUserHandler.USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(userName, result.getName().getNameValue());
        assertEquals(email, singleAttr(result, "email"));
        assertEquals(displayName, singleAttr(result, "display-name"));
        assertEquals(firstName, singleAttr(result, "first-name"));
        assertEquals(lastName, singleAttr(result, "last-name"));
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertEquals(toZoneDateTime(createdDate), singleAttr(result, "created-date"));
        assertEquals(toZoneDateTime(updatedDate), singleAttr(result, "updated-date"));
        assertTrue(isIncompleteAttribute(result.getAttributeByName("groups")));
    }

    @Test
    void getUsers() {
        // Given
        String key = "12345:abc";
        String userName = "foo";
        String email = "foo@example.com";
        String displayName = "Foo Bar";
        String firstName = "Foo";
        String lastName = "Bar";
        boolean active = true;
        Date createdDate = Date.from(Instant.now());
        Date updatedDate = Date.from(Instant.now());

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(false));

        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getUsers = ((h, size, offset) -> {
            targetPageSize.set(size);
            targetOffset.set(offset);

            UserEntity result = new UserEntity(userName, firstName, lastName, displayName, email, null, active, key, createdDate, updatedDate, false);
            h.handle(result);

            return 1;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(CrowdUserHandler.USER_OBJECT_CLASS, null, handler, defaultSearchOperation());

        // Then
        assertEquals(1, results.size());
        ConnectorObject result = results.get(0);
        assertEquals(CrowdUserHandler.USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(userName, result.getName().getNameValue());
        assertEquals(email, singleAttr(result, "email"));
        assertEquals(displayName, singleAttr(result, "display-name"));
        assertEquals(firstName, singleAttr(result, "first-name"));
        assertEquals(lastName, singleAttr(result, "last-name"));
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertEquals(toZoneDateTime(createdDate), singleAttr(result, "created-date"));
        assertEquals(toZoneDateTime(updatedDate), singleAttr(result, "updated-date"));
        assertNull(result.getAttributeByName("groups"), "Unexpected returned groups even if not requested");

        assertEquals(20, targetPageSize.get(), "Not page size in the operation option");
        assertEquals(1, targetOffset.get());
    }

    @Test
    void getUsersZero() {
        // Given
        String userName = "foo";

        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getUsers = ((h, size, offset) -> {
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
        connector.search(CrowdUserHandler.USER_OBJECT_CLASS, null, handler, defaultSearchOperation());

        // Then
        assertEquals(0, results.size());
        assertEquals(20, targetPageSize.get(), "Not default page size in the configuration");
        assertEquals(1, targetOffset.get());
    }

    @Test
    void getUsersTwo() {
        // Given
        AtomicReference<Integer> targetPageSize = new AtomicReference<>();
        AtomicReference<Integer> targetOffset = new AtomicReference<>();
        mockClient.getUsers = ((h, size, offset) -> {
            targetPageSize.set(size);
            targetOffset.set(offset);

            UserEntity result = new UserEntity("user1", null, null, null, null, null, true, "12345:abc", Date.from(Instant.now()), Date.from(Instant.now()), false);
            h.handle(result);

            result = new UserEntity("user2", null, null, null, null, null, true, "12345:efg", Date.from(Instant.now()), Date.from(Instant.now()), false);
            h.handle(result);

            return 2;
        });

        // When
        List<ConnectorObject> results = new ArrayList<>();
        ResultsHandler handler = connectorObject -> {
            results.add(connectorObject);
            return true;
        };
        connector.search(CrowdUserHandler.USER_OBJECT_CLASS, null, handler, defaultSearchOperation());

        // Then
        assertEquals(2, results.size());

        ConnectorObject result = results.get(0);
        assertEquals(CrowdUserHandler.USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals("12345:abc", result.getUid().getUidValue());
        assertEquals("user1", result.getName().getNameValue());

        result = results.get(1);
        assertEquals(CrowdUserHandler.USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals("12345:efg", result.getUid().getUidValue());
        assertEquals("user2", result.getName().getNameValue());

        assertEquals(20, targetPageSize.get(), "Not default page size in the configuration");
        assertEquals(1, targetOffset.get());
    }

    @Test
    void deleteUser() {
        // Given
        String key = "12345:abc";
        String userName = "foo";

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(false));

        AtomicReference<Uid> deleted = new AtomicReference<>();
        mockClient.deleteUser = ((uid) -> {
            deleted.set(uid);
        });

        // When
        connector.delete(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(userName)), new OperationOptionsBuilder().build());

        // Then
        assertEquals(key, deleted.get().getUidValue());
        assertEquals(userName, deleted.get().getNameHintValue());
    }
}
