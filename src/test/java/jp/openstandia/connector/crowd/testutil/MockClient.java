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
package jp.openstandia.connector.crowd.testutil;

import com.atlassian.crowd.integration.rest.entity.UserEntity;
import com.atlassian.crowd.model.group.GroupWithAttributes;
import com.atlassian.crowd.model.user.User;
import com.atlassian.crowd.model.user.UserWithAttributes;
import jp.openstandia.connector.crowd.CrowdQueryHandler;
import jp.openstandia.connector.crowd.CrowdRESTClient;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class MockClient extends CrowdRESTClient {

    private static MockClient INSTANCE = new MockClient();

    public MockBiFunction<UserWithAttributes, GuardedString, Uid> createUser;
    public MockConsumer<User> updateUser;
    public MockBiConsumer<String, Map<String, Set<String>>> updateUserAttributes;
    public MockBiConsumer<String, GuardedString> updatePassword;
    public MockBiConsumer<String, List<String>> addUserToGroup;
    public MockBiConsumer<String, List<String>> deleteUserFromGroup;
    public MockBiConsumer<String, String> renameUser;
    public MockFunction<Uid, UserEntity> getUserByUid;
    public MockFunction<Name, UserEntity> getUserByName;
    public MockTripleFunction<CrowdQueryHandler<UserEntity>, Integer, Integer, Integer> getUsers;
    public MockBiFunction<String, Integer, List<String>> getGroupsForUser;
    public MockConsumer<Uid> deleteUser;
    public MockFunction<GroupWithAttributes, Uid> createGroup;
    public MockBiConsumer<String, List<String>> addGroupToGroup;

    public boolean closed = false;

    public void init() {
        INSTANCE = new MockClient();
    }

    private MockClient() {
        super("mock", null, null);
    }

    public static MockClient instance() {
        return INSTANCE;
    }

    @Override
    public void test() {
    }

    @Override
    public void close() {
        closed = true;
    }

    // User

    @Override
    public Uid createUser(UserWithAttributes user, GuardedString password) throws AlreadyExistsException {
        return createUser.apply(user, password);
    }

    @Override
    public void updateUser(User update) {
        updateUser.accept(update);
    }

    @Override
    public void updateUserAttributes(String userName, Map<String, Set<String>> attributes) {
        updateUserAttributes.accept(userName, attributes);
    }

    @Override
    public void updatePassword(String userName, GuardedString password) {
        updatePassword.accept(userName, password);
    }

    @Override
    public void addUserToGroup(String userName, List<String> groups) throws AlreadyExistsException {
        addUserToGroup.accept(userName, groups);
    }

    @Override
    public void deleteUserFromGroup(String userName, List<String> groups) throws AlreadyExistsException {
        deleteUserFromGroup.accept(userName, groups);
    }

    @Override
    public void renameUser(String userName, String newUserName) {
        renameUser.accept(userName, newUserName);
    }

    @Override
    public UserEntity getUser(Uid uid, OperationOptions options, Set<String> fetchFieldsSet) throws UnknownUidException {
        return getUserByUid.apply(uid);
    }

    @Override
    public UserEntity getUser(Name name, OperationOptions options, Set<String> fetchFieldsSet) throws UnknownUidException {
        return getUserByName.apply(name);
    }

    @Override
    public int getUsers(CrowdQueryHandler<UserEntity> handler, OperationOptions options, Set<String> fetchFieldsSet, int pageSize, int pageOffset) {
        return getUsers.apply(handler, pageSize, pageOffset);
    }

    @Override
    public List<String> getGroupsForUser(String userName, int pageSize) {
        return getGroupsForUser.apply(userName, pageSize);
    }

    @Override
    public void deleteUser(Uid uid) {
        deleteUser.accept(uid);
    }

    // Group

    @Override
    public Uid createGroup(GroupWithAttributes group) throws AlreadyExistsException {
        return createGroup.apply(group);
    }

    @Override
    public void addGroupToGroup(String groupName, List<String> groups) throws AlreadyExistsException {
        addGroupToGroup.accept(groupName, groups);
    }

    @FunctionalInterface
    public interface MockFunction<T, R> {
        R apply(T t);
    }

    @FunctionalInterface
    public interface MockBiFunction<T, U, R> {
        R apply(T t, U u);
    }

    @FunctionalInterface
    public interface MockTripleFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }

    @FunctionalInterface
    public interface MockConsumer<T> {
        void accept(T t);
    }

    @FunctionalInterface
    public interface MockBiConsumer<T, U> {
        void accept(T t, U u);
    }
}
