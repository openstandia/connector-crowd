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
import com.atlassian.crowd.model.user.UserWithAttributes;
import jp.openstandia.connector.crowd.CrowdQueryHandler;
import jp.openstandia.connector.crowd.CrowdRESTClient;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Set;

public class MockClient extends CrowdRESTClient {

    private static final MockClient INSTANCE = new MockClient();

    public boolean closed = false;

    public void init() {
        closed = false;
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
    }

    @Override
    public Uid createUser(UserWithAttributes user, GuardedString password) throws AlreadyExistsException {
        return null;
    }

    @Override
    public UserEntity getUser(Uid uid, OperationOptions options, Set<String> fetchFieldsSet) {
        return null;
    }

    @Override
    public UserEntity getUser(Name name, OperationOptions options, Set<String> fetchFieldsSet) {
        return null;
    }

    @Override
    public int getUsers(CrowdQueryHandler<UserEntity> handler, OperationOptions options, Set<String> fetchFieldsSet, int pageSize, int pageOffset) {
        return 0;
    }
}