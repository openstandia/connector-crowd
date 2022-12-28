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

import com.atlassian.crowd.model.user.UserWithAttributes;
import jp.openstandia.connector.crowd.testutil.AbstractTest;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        String newPassword = toPlain(createdPassword.get());
        assertEquals(password, newPassword);

        assertEquals(userName, targetUserName.get());
        assertEquals(groups, targetGroups.get());
    }
}
