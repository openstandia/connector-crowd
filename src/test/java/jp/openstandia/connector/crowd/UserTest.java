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
import com.atlassian.crowd.model.user.User;
import com.atlassian.crowd.model.user.UserWithAttributes;
import jp.openstandia.connector.crowd.testutil.AbstractTest;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UserTest extends AbstractTest {

    @Test
    void addUser() {
        // Given
        String key = "12345:abc";
        String userName = "foo";
        String email = "foo@example.com";
        String displayName = "Foo Bar";
        String firstName = "Foo";
        String lastName = "Bar";
        String password = "secret";
        List<String> groups = list("group1", "group2");

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(true));
        attrs.add(AttributeBuilder.buildPassword(password.toCharArray()));
        attrs.add(AttributeBuilder.build("email", email));
        attrs.add(AttributeBuilder.build("display-name", displayName));
        attrs.add(AttributeBuilder.build("first-name", firstName));
        attrs.add(AttributeBuilder.build("last-name", lastName));
        attrs.add(AttributeBuilder.build("groups", groups));

        AtomicReference<UserWithAttributes> created = new AtomicReference<>();
        AtomicReference<GuardedString> createdPassword = new AtomicReference<>();
        mockClient.createUser = ((user, cred) -> {
            created.set(user);
            createdPassword.set(cred);

            return new Uid(key, new Name(userName));
        });
        AtomicReference<String> targetUserName = new AtomicReference<>();
        AtomicReference<List<String>> targetGroups = new AtomicReference<>();
        mockClient.addUserToGroup = ((u, g) -> {
            targetUserName.set(u);
            targetGroups.set(g);
        });

        // When
        Uid uid = connector.create(CrowdUserHandler.USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Then
        assertEquals(key, uid.getUidValue());
        assertEquals(userName, uid.getNameHintValue());

        UserWithAttributes newUser = created.get();
        assertEquals(userName, newUser.getName());
        assertEquals(email, newUser.getEmailAddress());
        assertEquals(displayName, newUser.getDisplayName());
        assertEquals(firstName, newUser.getFirstName());
        assertEquals(lastName, newUser.getLastName());
        assertTrue(newUser.isActive());

        assertEquals(password, toPlain(createdPassword.get()));

        assertEquals(userName, targetUserName.get());
        assertEquals(groups, targetGroups.get());
    }

    @Test
    void addUserWithAttributes() {
        // Apply custom configuration for this test
        configuration.setUserAttributesSchema(new String[]{"custom1$string", "custom2$stringArray"});
        ConnectorFacade connector = newFacade(configuration);

        // Given
        String key = "12345:abc";
        String userName = "foo";
        String custom1 = "abc";
        List<String> custom2 = list("123", "456");

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.build("attributes.custom1", custom1));
        attrs.add(AttributeBuilder.build("attributes.custom2", custom2));

        AtomicReference<UserWithAttributes> created = new AtomicReference<>();
        mockClient.createUser = ((user, cred) -> {
            created.set(user);

            return new Uid(key, new Name(userName));
        });

        // When
        Uid uid = connector.create(CrowdUserHandler.USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Then
        assertEquals(key, uid.getUidValue());
        assertEquals(userName, uid.getNameHintValue());

        UserWithAttributes newUser = created.get();
        assertEquals(userName, newUser.getName());
        assertEquals(custom1, newUser.getValue("custom1"));
        assertEquals(asSet(custom2), asSet(newUser.getValues("custom2")));
    }

    @Test
    void addUserWithInactive() {
        // Given
        String key = "12345:abc";
        String userName = "foo";

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(new Name(userName));
        attrs.add(AttributeBuilder.buildEnabled(false));

        AtomicReference<UserWithAttributes> created = new AtomicReference<>();
        mockClient.createUser = ((user, cred) -> {
            created.set(user);

            return new Uid(key, new Name(userName));
        });

        // When
        Uid uid = connector.create(CrowdUserHandler.USER_OBJECT_CLASS, attrs, new OperationOptionsBuilder().build());

        // Then
        assertEquals(key, uid.getUidValue());
        assertEquals(userName, uid.getNameHintValue());
        assertFalse(created.get().isActive());
    }

    @Test
    void updateUser() {
        // Given
        String currentUserName = "hoge";

        String key = "12345:abc";
        String userName = "foo";
        String email = "foo@example.com";
        String displayName = "Foo Bar";
        String firstName = "Foo";
        String lastName = "Bar";
        String password = "secret";
        List<String> groups = list("group1", "group2");

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(Name.NAME, userName));
        // Currently, changing email is not allowed using REST API.
        // https://jira.atlassian.com/browse/CWD-5792
        // modifications.add(AttributeDeltaBuilder.build("email", "bob@example.com"));
        modifications.add(AttributeDeltaBuilder.build("display-name", displayName));
        modifications.add(AttributeDeltaBuilder.build("first-name", firstName));
        modifications.add(AttributeDeltaBuilder.build("last-name", lastName));
        modifications.add(AttributeDeltaBuilder.buildPassword(password.toCharArray()));
        modifications.add(AttributeDeltaBuilder.buildEnabled(true));
        modifications.add(AttributeDeltaBuilder.build("groups", groups, null));

        AtomicReference<Uid> targetUid = new AtomicReference<>();
        mockClient.getUserByUid = ((u) -> {
            targetUid.set(u);

            UserEntity current = UserEntity.newMinimalInstance(currentUserName);
            return current;
        });
        AtomicReference<User> updated = new AtomicReference<>();
        mockClient.updateUser = ((user) -> {
            updated.set(user);
        });
        AtomicReference<String> targetUserName1 = new AtomicReference<>();
        AtomicReference<GuardedString> newPassword = new AtomicReference<>();
        mockClient.updatePassword = ((u, p) -> {
            targetUserName1.set(u);
            newPassword.set(p);
        });
        AtomicReference<String> targetUserName2 = new AtomicReference<>();
        AtomicReference<List<String>> targetAddGroups = new AtomicReference<>();
        mockClient.addUserToGroup = ((u, g) -> {
            targetUserName2.set(u);
            targetAddGroups.set(g);
        });
        AtomicReference<String> targetUserName3 = new AtomicReference<>();
        AtomicReference<String> newtUserName = new AtomicReference<>();
        mockClient.renameUser = ((u, n) -> {
            targetUserName3.set(u);
            newtUserName.set(n);
        });

        // When
        Set<AttributeDelta> affected = connector.updateDelta(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(userName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(key, targetUid.get().getUidValue());

        User updatedUser = updated.get();
        assertEquals(displayName, updatedUser.getDisplayName());
        assertEquals(firstName, updatedUser.getFirstName());
        assertEquals(lastName, updatedUser.getLastName());
        assertTrue(updatedUser.isActive());

        assertEquals(currentUserName, targetUserName1.get());
        assertEquals(password, toPlain(newPassword.get()));

        assertEquals(currentUserName, targetUserName2.get());
        assertEquals(groups, targetAddGroups.get());

        assertEquals(currentUserName, targetUserName3.get());
        assertEquals(userName, newtUserName.get());
    }

    @Test
    void updateUserGroups() {
        // Given
        String currentUserName = "hoge";

        String key = "12345:abc";
        String userName = "foo";
        List<String> addGroups = list("group1", "group2");
        List<String> delGroups = list("group3", "group4");

        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(Name.NAME, userName));
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
        Set<AttributeDelta> affected = connector.updateDelta(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(userName)), modifications, new OperationOptionsBuilder().build());

        // Then
        assertNull(affected);

        assertEquals(key, targetUid.get().getUidValue());

        assertEquals(currentUserName, targetUserName2.get());
        assertEquals(addGroups, targetAddGroups.get());

        assertEquals(currentUserName, targetUserName3.get());
        assertEquals(delGroups, targetDelGroups.get());
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
        assertNull(result.getAttributeByName("groups"), "Unexpected returned groups even if not requested");
        assertNull(targetUserName.get());
        assertNull(targetPageSize.get());

        // Given
        targetUserName.set(null);
        targetPageSize.set(null);

        // When
        // Request "groups"
        result = connector.getObject(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(userName)), defaultGetOperation("groups"));

        // Then
        assertEquals(CrowdUserHandler.USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(userName, result.getName().getNameValue());
        assertEquals(email, singleAttr(result, "email"));
        assertEquals(displayName, singleAttr(result, "display-name"));
        assertEquals(firstName, singleAttr(result, "first-name"));
        assertEquals(lastName, singleAttr(result, "last-name"));
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertEquals(groups, multiAttr(result, "groups"));
        assertEquals(userName, targetUserName.get());
        assertEquals(50, targetPageSize.get(), "Not default page size in the configuration");

        // Given
        targetUserName.set(null);
        targetPageSize.set(null);

        // When
        // No operation options
        result = connector.getObject(CrowdUserHandler.USER_OBJECT_CLASS, new Uid(key, new Name(userName)), new OperationOptionsBuilder().build());

        // Then
        assertEquals(CrowdUserHandler.USER_OBJECT_CLASS, result.getObjectClass());
        assertEquals(key, result.getUid().getUidValue());
        assertEquals(userName, result.getName().getNameValue());
        assertEquals(email, singleAttr(result, "email"));
        assertEquals(displayName, singleAttr(result, "display-name"));
        assertEquals(firstName, singleAttr(result, "first-name"));
        assertEquals(lastName, singleAttr(result, "last-name"));
        assertEquals(active, singleAttr(result, OperationalAttributes.ENABLE_NAME));
        assertNull(result.getAttributeByName("groups"), "Unexpected returned groups even if not requested");
        assertNull(targetUserName.get());
        assertNull(targetPageSize.get());
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
