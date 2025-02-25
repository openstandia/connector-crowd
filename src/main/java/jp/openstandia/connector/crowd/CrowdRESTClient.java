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

import com.atlassian.crowd.embedded.api.PasswordCredential;
import com.atlassian.crowd.exception.*;
import com.atlassian.crowd.integration.rest.entity.GroupEntity;
import com.atlassian.crowd.integration.rest.entity.UserEntity;
import com.atlassian.crowd.integration.rest.service.ExceptionUtil;
import com.atlassian.crowd.integration.rest.service.RestExecutorWrapper;
import com.atlassian.crowd.model.group.Group;
import com.atlassian.crowd.model.group.GroupWithAttributes;
import com.atlassian.crowd.model.user.User;
import com.atlassian.crowd.model.user.UserWithAttributes;
import com.atlassian.crowd.search.query.entity.restriction.NullRestriction;
import com.atlassian.crowd.service.client.CrowdClient;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.*;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class CrowdRESTClient {
    private static final Log LOG = Log.getLog(CrowdRESTClient.class);

    private final String instanceName;
    private final CrowdConfiguration configuration;
    private final CrowdClient crowdClient;
    private final RestExecutorWrapper executor;

    public CrowdRESTClient(String instanceName, CrowdConfiguration configuration, CrowdClient httpClient) {
        this.instanceName = instanceName;
        this.configuration = configuration;
        this.crowdClient = httpClient;
        this.executor = new RestExecutorWrapper(httpClient);
    }

    public void test() {
        try {
            this.crowdClient.getCookieConfiguration();

            LOG.info("[{0}] Crowd connector's connection test is OK", instanceName);

        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public void close() {
        this.crowdClient.shutdown();
    }

    protected ConnectorException handleException(Exception e) {
        if (e instanceof CrowdException) {
            return handleException((CrowdException) e);
        }
        if (e instanceof ApplicationPermissionException) {
            return handleException((ApplicationPermissionException) e);
        }
        return new ConnectorIOException(e);
    }

    protected ConnectorException handleException(CrowdException e) {
        int statusCode = ExceptionUtil.getStatusCode(e);

        if (e instanceof InvalidAuthenticationException) {
            return new ConnectionFailedException(e);
        }
        if (e instanceof InvalidUserException) {
            return new AlreadyExistsException(e);
        }
        if (e instanceof InvalidGroupException) {
            return new AlreadyExistsException(e);
        }
        if (e instanceof ObjectNotFoundException) {
            return new UnknownUidException(e);
        }
        if (statusCode == 400) {
            return new InvalidAttributeValueException(e);
        }
        return new ConnectorIOException(e);
    }

    protected ConnectorException handleException(ApplicationPermissionException e) {
        return new PermissionDeniedException(e);
    }

    // User

    public Uid createUser(UserWithAttributes user, GuardedString password) throws AlreadyExistsException {
        try {
            UserWithAttributes result = this.crowdClient.addUser(user, toPasswordCredential(password));

            // Use "key" as UID
            return new Uid(result.getExternalId(), new Name(result.getName()));

        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private PasswordCredential toPasswordCredential(GuardedString password) {
        if (password == null) {
            return null;
        }

        final PasswordCredential[] cred = new PasswordCredential[1];
        password.access(c -> {
            cred[0] = new PasswordCredential(String.valueOf(c));
        });
        return cred[0];
    }

    public UserEntity getUser(Uid uid, OperationOptions options, Set<String> fetchFieldsSet) throws UnknownUidException {
        try {
            UserEntity user = (UserEntity) this.executor.getUserByKeyWithAttributes(uid.getUidValue());
            return user;

        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public UserEntity getUser(Name name, OperationOptions options, Set<String> fetchFieldsSet) throws UnknownUidException {
        try {
            UserEntity user = (UserEntity) this.crowdClient.getUserWithAttributes(name.getNameValue());
            return user;

        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public void updateUser(User update) {
        try {
            this.crowdClient.updateUser(update);

        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public void updateUserAttributes(String userName, Map<String, Set<String>> attributes) {
        try {
            // We don't use removeUserAttributes to reduce API calling.
            // Instead of it, we pass empty list to remove the attribute.
            this.crowdClient.storeUserAttributes(userName, attributes);

        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public void updatePassword(String userName, GuardedString password) {
        password.access(c -> {
            try {
                this.crowdClient.updateUserCredential(userName, String.valueOf(c));
            } catch (Exception e) {
                throw handleException(e);
            }
        });
    }

    public void updateGroupAttributes(String groupName, Map<String, Set<String>> attributes) {
        try {
            // We don't use removeGroupAttributes to reduce API calling.
            // Instead of it, we pass empty list to remove the attribute.
            this.crowdClient.storeGroupAttributes(groupName, attributes);

        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public void renameUser(String userName, String newUserName) {
        try {
            this.crowdClient.renameUser(userName, newUserName);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public void deleteUser(Uid uid) {
        try {
            String userName = resolveUserName(uid);

            this.crowdClient.removeUser(userName);

        } catch (Exception e) {
            throw handleException(e);
        }
    }

    protected String resolveUserName(Uid uid) {
        if (uid.getNameHint() != null) {
            return uid.getNameHintValue();
        } else {
            User user = getUser(uid, null, null);
            return user.getName();
        }
    }

    public int getUsers(CrowdQueryHandler<UserWithAttributes> handler, OperationOptions options, Set<String> fetchFieldsSet, int pageSize, int pageOffset) {
        // ConnId starts from 1, 0 means no offset (requested all data)
        if (pageOffset < 1) {
            return getAll(handler, pageSize, (start, size) -> {
                try {
                    List<UserWithAttributes> users = this.crowdClient.searchUsersWithAttributes(NullRestriction.INSTANCE, start, size);
                    return users;
                } catch (Exception e) {
                    throw handleException(e);
                }
            });
        }

        // Pagination
        // Crowd starts from 0
        int start = pageOffset - 1;
        int count = 0;

        try {
            List<UserWithAttributes> users = this.crowdClient.searchUsersWithAttributes(NullRestriction.INSTANCE, start, pageSize);

            for (UserWithAttributes user : users) {
                count++;
                if (!handler.handle(user)) {
                    return count;
                }
            }
        } catch (Exception e) {
            throw handleException(e);
        }

        return count;
    }

    protected <T> int getAll(CrowdQueryHandler<T> handler, int pageSize, BiFunction<Integer, Integer, List<T>> apiCall) {
        // Crowd starts from 0
        int start = 0;
        int count = 0;
        try {
            while (true) {
                List<T> results = apiCall.apply(start, pageSize);

                if (results.size() == 0) {
                    // End of the page
                    return count;
                }

                for (T result : results) {
                    count++;
                    if (!handler.handle(result)) {
                        return count;
                    }
                }

                // search next page
                start += pageSize;
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    // User-Group
    public void addUserToGroup(String userName, List<String> groups) throws AlreadyExistsException {
        try {
            for (String group : groups) {
                this.crowdClient.addUserToGroup(userName, group);
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public void deleteUserFromGroup(String userName, List<String> groups) throws AlreadyExistsException {
        try {
            for (String group : groups) {
                this.crowdClient.removeUserFromGroup(userName, group);
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public Stream<String> getGroupsForUser(String userName, int pageSize) {
        // Crowd starts from 0
        int start = 0;
        List<String> results = new ArrayList<>();
        try {
            while (true) {
                List<String> groups = this.crowdClient.getNamesOfGroupsForUser(userName, start, pageSize);

                if (groups.isEmpty()) {
                    // End of the page
                    return results.stream();
                }

                results.addAll(groups);

                // search next page
                start += pageSize;
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    // Group-Group
    public void addGroupToGroup(String groupName, List<String> groups) throws AlreadyExistsException {
        try {
            for (String group : groups) {
                this.crowdClient.addGroupToGroup(groupName, group);
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public void deleteGroupFromGroup(String groupName, List<String> groups) throws AlreadyExistsException {
        try {
            for (String group : groups) {
                this.crowdClient.removeGroupFromGroup(groupName, group);
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public Stream<String> getGroupsForGroup(String groupName, int pageSize) {
        // Crowd starts from 0
        int start = 0;
        List<String> results = new ArrayList<>();
        try {
            while (true) {
                List<String> groups = this.crowdClient.getNamesOfParentGroupsForGroup(groupName, start, pageSize);

                if (groups.isEmpty()) {
                    // End of the page
                    return results.stream();
                }

                results.addAll(groups);

                // search next page
                start += pageSize;
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    // Group

    public Uid createGroup(GroupWithAttributes group) throws AlreadyExistsException {
        try {
            this.crowdClient.addGroup(group);

            return new Uid(group.getName(), new Name(group.getName()));

        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public void updateGroup(Group update) {
        try {
            this.crowdClient.updateGroup(update);

        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public GroupEntity getGroup(Uid uid, OperationOptions options, Set<String> fetchFieldsSet) {
        try {
            GroupEntity group = (GroupEntity) this.crowdClient.getGroupWithAttributes(uid.getUidValue());
            return group;

        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public GroupEntity getGroup(Name name, OperationOptions options, Set<String> fetchFieldsSet) {
        try {
            GroupEntity group = (GroupEntity) this.crowdClient.getGroupWithAttributes(name.getNameValue());
            return group;

        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public int getGroups(CrowdQueryHandler<GroupWithAttributes> handler, OperationOptions options, Set<String> fetchFieldsSet, int pageSize, int pageOffset) {
        // ConnId starts from 1, 0 means no offset (requested all data)
        if (pageOffset < 1) {
            return getAll(handler, pageSize, (start, size) -> {
                try {
                    List<GroupWithAttributes> groups = this.crowdClient.searchGroupsWithAttributes(NullRestriction.INSTANCE, start, size);
                    return groups;
                } catch (Exception e) {
                    throw handleException(e);
                }
            });
        }

        // Pagination
        // Crowd starts from 0
        int start = pageOffset - 1;
        int count = 0;

        try {
            List<GroupWithAttributes> groups = this.crowdClient.searchGroupsWithAttributes(NullRestriction.INSTANCE, start, pageSize);

            for (GroupWithAttributes group : groups) {
                count++;
                if (!handler.handle(group)) {
                    return count;
                }
            }
        } catch (Exception e) {
            throw handleException(e);
        }

        return count;
    }

    public void deleteGroup(Uid uid) {
        try {
            this.crowdClient.removeGroup(uid.getUidValue());

        } catch (Exception e) {
            throw handleException(e);
        }
    }
}
