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
import jp.openstandia.connector.util.ObjectHandler;
import jp.openstandia.connector.util.SchemaDefinition;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static jp.openstandia.connector.util.Utils.toZoneDateTime;
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.*;

public class CrowdUserHandler implements ObjectHandler {

    public static final ObjectClass USER_OBJECT_CLASS = new ObjectClass("user");

    private static final Log LOGGER = Log.getLog(CrowdUserHandler.class);
    private static final Set<String> SUPPORTED_TYPES = Arrays.asList("string", "stringarray").stream().collect(Collectors.toSet());

    private final CrowdConfiguration configuration;
    private final CrowdRESTClient client;
    private final SchemaDefinition schema;

    public CrowdUserHandler(CrowdConfiguration configuration, CrowdRESTClient client,
                            SchemaDefinition schema) {
        this.configuration = configuration;
        this.client = client;
        this.schema = schema;
    }

    public static SchemaDefinition.Builder createSchema(CrowdConfiguration configuration, CrowdRESTClient client) {
        SchemaDefinition.Builder<CrowdUserModel, CrowdUserModel, UserEntity> sb
                = SchemaDefinition.newBuilder(USER_OBJECT_CLASS, CrowdUserModel.class, UserEntity.class);

        // __UID__
        // The id for the user. Must be unique and unchangeable.
        sb.addUid("key",
                SchemaDefinition.Types.UUID,
                null,
                (source) -> source.getExternalId(),
                null,
                REQUIRED, NOT_CREATABLE, NOT_UPDATEABLE
        );

        // username (__NAME__)
        // The name for the user. Must be unique and changeable.
        // Also, it's case-insensitive.
        sb.addName("username",
                SchemaDefinition.Types.STRING,
                (source, dest) -> dest.setUserName(source),
                (source) -> source.getName(),
                null
        );

        // __PASSWORD__
        sb.add(OperationalAttributes.PASSWORD_NAME,
                SchemaDefinition.Types.GUARDED_STRING,
                (source, dest) -> dest.setPassword(source),
                null,
                null,
                NOT_READABLE, NOT_RETURNED_BY_DEFAULT
        );

        // __ENABLE__
        sb.add(OperationalAttributes.ENABLE_NAME,
                SchemaDefinition.Types.BOOLEAN,
                (source, dest) -> dest.setActive(source),
                (source) -> source.isActive(),
                "active"
        );

        // Attributes
        sb.add("last-name",
                SchemaDefinition.Types.STRING,
                (source, dest) -> dest.setLastName(source),
                (source) -> source.getLastName(),
                null
        );
        sb.add("first-name",
                SchemaDefinition.Types.STRING,
                (source, dest) -> dest.setFirstName(source),
                (source) -> source.getFirstName(),
                null
        );
        sb.add("display-name",
                SchemaDefinition.Types.STRING,
                (source, dest) -> dest.setDisplayName(source),
                (source) -> source.getDisplayName(),
                null
        );
        // Currently, changing email is not allowed using REST API.
        // But we don't set the schema as NOT_UPDATABLE because it might be supported in the future.
        // https://jira.atlassian.com/browse/CWD-5792
        sb.add("email",
                SchemaDefinition.Types.STRING,
                (source, dest) -> dest.setEmailAddress(source),
                (source) -> source.getEmailAddress(),
                null
        );

        // Attributes
        Arrays.stream(configuration.getUserAttributesSchema())
                .map(x -> x.split("\\$"))
                .filter(x -> x.length == 2)
                .map(x -> new String[]{x[0], x[1].toLowerCase()})
                .filter(x -> SUPPORTED_TYPES.contains(x[1]))
                .forEach(x -> {
                    String attrName = x[0];
                    String attrType = x[1];

                    if (!attrType.endsWith("array")) {
                        sb.add("attributes." + attrName,
                                SchemaDefinition.Types.STRING,
                                (source, dest) -> dest.replaceAttribute(attrName, source),
                                (source) -> source.getAttributes().getValue(attrName),
                                null
                        );
                    } else {
                        sb.addAsMultiple("attributes." + attrName,
                                SchemaDefinition.Types.STRING,
                                (source, dest) -> dest.setAttributes(attrName, source),
                                (source, dest) -> dest.addAttributes(attrName, source),
                                (source, dest) -> dest.removeAttributes(attrName, source),
                                (source) -> {
                                    Set<String> values = source.getAttributes().getValues(attrName);
                                    if (values == null) {
                                        return Collections.emptyList();
                                    }
                                    return new ArrayList(values);
                                },
                                null
                        );
                    }
                });

        // Association
        sb.addAsMultiple("groups",
                SchemaDefinition.Types.STRING,
                (source, dest) -> dest.setGroups(source),
                (add, dest) -> dest.addGroups(add),
                (remove, dest) -> dest.removeGroups(remove),
                (source) -> client.getGroupsForUser(source.getName(), configuration.getDefaultQueryPageSize()),
                null,
                NOT_RETURNED_BY_DEFAULT
        );

        // Metadata (readonly)
        sb.add("created-date",
                SchemaDefinition.Types.DATETIME,
                null,
                (source) -> toZoneDateTime(source.getCreatedDate()),
                null,
                NOT_CREATABLE, NOT_UPDATEABLE
        );
        sb.add("updated-date",
                SchemaDefinition.Types.DATETIME,
                null,
                (source) -> toZoneDateTime(source.getUpdatedDate()),
                null,
                NOT_CREATABLE, NOT_UPDATEABLE
        );

        LOGGER.ok("The constructed user schema");

        return sb;
    }

    @Override
    public SchemaDefinition getSchema() {
        return schema;
    }

    @Override
    public Uid create(Set<Attribute> attributes) {
        CrowdUserModel user = CrowdUserModel.create();
        CrowdUserModel mapped = schema.apply(attributes, user);

        Uid newUid = client.createUser(mapped.toUser().withName(mapped.newUserName), mapped.password);

        if (mapped.addGroups != null) {
            client.addUserToGroup(newUid.getNameHintValue(), mapped.addGroups);
        }

        return newUid;
    }

    @Override
    public Set<AttributeDelta> updateDelta(Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        // To apply the diff, we need to fetch the current object

        UserEntity current = client.getUser(uid, options, null);

        CrowdUserModel dest = CrowdUserModel.create(current);

        schema.applyDelta(modifications, dest);

        if (dest.hasUserChange) {
            client.updateUser(dest.toUser());
        }
        if (dest.hasAttributesChange) {
            client.updateUserAttributes(current.getName(), dest.updateAttributes);
        }
        if (dest.hasPasswordChange) {
            client.updatePassword(current.getName(), dest.password);
        }
        if (dest.addGroups != null) {
            client.addUserToGroup(current.getName(), dest.addGroups);
        }
        if (dest.removeGroups != null) {
            client.deleteUserFromGroup(current.getName(), dest.removeGroups);
        }
        if (dest.hasUsernameChange) {
            client.updateUsername(current.getName(), dest.newUserName);
        }

        return null;
    }

    @Override
    public void delete(Uid uid, OperationOptions options) {
        client.deleteUser(uid);
    }

    @Override
    public int getByUid(Uid uid, ResultsHandler resultsHandler, OperationOptions options,
                        Set<String> returnAttributesSet, Set<String> fetchFieldsSet,
                        boolean allowPartialAttributeValues, int pageSize, int pageOffset) {
        UserEntity user = client.getUser(uid, options, fetchFieldsSet);


        if (user != null) {
            resultsHandler.handle(toConnectorObject(schema, user, returnAttributesSet, allowPartialAttributeValues));
            return 1;
        }
        return 0;
    }

    @Override
    public int getByName(Name name, ResultsHandler resultsHandler, OperationOptions options,
                         Set<String> returnAttributesSet, Set<String> fetchFieldsSet,
                         boolean allowPartialAttributeValues, int pageSize, int pageOffset) {
        UserEntity user = client.getUser(name, options, fetchFieldsSet);

        if (user != null) {
            resultsHandler.handle(toConnectorObject(schema, user, returnAttributesSet, allowPartialAttributeValues));
            return 1;
        }
        return 0;
    }

    @Override
    public int getAll(ResultsHandler resultsHandler, OperationOptions options,
                      Set<String> returnAttributesSet, Set<String> fetchFieldsSet,
                      boolean allowPartialAttributeValues, int pageSize, int pageOffset) {
        return client.getUsers((u) -> resultsHandler.handle(toConnectorObject(schema, u, returnAttributesSet, allowPartialAttributeValues)),
                options, fetchFieldsSet, pageSize, pageOffset);
    }
}
