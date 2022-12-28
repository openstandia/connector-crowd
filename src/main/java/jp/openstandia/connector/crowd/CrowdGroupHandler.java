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
import jp.openstandia.connector.util.ObjectHandler;
import jp.openstandia.connector.util.SchemaDefinition;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.*;

import java.util.Set;

import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.*;

public class CrowdGroupHandler implements ObjectHandler {

    public static final ObjectClass GROUP_OBJECT_CLASS = new ObjectClass("group");

    private static final Log LOGGER = Log.getLog(CrowdGroupHandler.class);

    private final CrowdConfiguration configuration;
    private final CrowdRESTClient client;
    private final SchemaDefinition schema;

    public CrowdGroupHandler(CrowdConfiguration configuration, CrowdRESTClient client,
                             SchemaDefinition schema) {
        this.configuration = configuration;
        this.client = client;
        this.schema = schema;
    }

    public static SchemaDefinition.Builder createSchema(CrowdConfiguration configuration, CrowdRESTClient client) {
        SchemaDefinition.Builder<CrowdGroupModel, CrowdGroupModel, GroupEntity> sb
                = SchemaDefinition.newBuilder(GROUP_OBJECT_CLASS, CrowdGroupModel.class, GroupEntity.class);

        // __UID__
        // The id for the user. Must be unique and unchangeable.
        sb.addUid("groupname",
                SchemaDefinition.Types.UUID,
                null,
                (source) -> source.getName(),
                null,
                REQUIRED, NOT_CREATABLE, NOT_UPDATEABLE
        );

        // username (__NAME__)
        // The name for the group. Must be unique and changeable.
        // Also, it's case-insensitive.
        sb.addName("groupname",
                SchemaDefinition.Types.STRING,
                (source, dest) -> dest.setGroupName(source),
                (source) -> source.getName(),
                null
        );

        // __ENABLE__
        sb.add(OperationalAttributes.ENABLE_NAME,
                SchemaDefinition.Types.BOOLEAN,
                (source, dest) -> dest.setActive(source),
                (source) -> source.isActive(),
                "active"
        );

        // Attributes
        sb.add("description",
                SchemaDefinition.Types.STRING,
                (source, dest) -> dest.setDescription(source),
                (source) -> source.getDescription(),
                null
        );

        // Association
        sb.addAsMultiple("groups",
                SchemaDefinition.Types.STRING,
                (source, dest) -> dest.setGroups(source),
                (add, dest) -> dest.addGroups(add),
                (remove, dest) -> dest.removeGroups(remove),
                (source) -> client.getGroupsForGroup(source.getName(), configuration.getDefaultQueryPageSize()),
                null,
                NOT_RETURNED_BY_DEFAULT
        );

        LOGGER.ok("The constructed group schema");

        return sb;
    }

    @Override
    public SchemaDefinition getSchema() {
        return schema;
    }

    @Override
    public Uid create(Set<Attribute> attributes) {
        CrowdGroupModel user = CrowdGroupModel.create();
        CrowdGroupModel mapped = schema.apply(attributes, user);

        Uid newUid = client.createGroup(mapped.toGroup());

        if (mapped.addGroups != null) {
            client.addGroupToGroup(newUid.getNameHintValue(), mapped.addGroups);
        }

        return newUid;
    }

    @Override
    public Set<AttributeDelta> updateDelta(Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        // To apply the diff, we need to fetch the current object

        GroupEntity current = client.getGroup(uid, options, null);

        CrowdGroupModel dest = new CrowdGroupModel(current);

        schema.applyDelta(modifications, dest);

        if (dest.hasGroupChange) {
            client.updateGroup(dest.toGroup());
        }
        if (dest.addGroups != null) {
            client.addGroupToGroup(current.getName(), dest.addGroups);
        }
        if (dest.removeGroups != null) {
            client.deleteGroupFromGroup(current.getName(), dest.removeGroups);
        }

        return null;
    }

    @Override
    public void delete(Uid uid, OperationOptions options) {
        client.deleteGroup(uid);
    }

    @Override
    public int getByUid(Uid uid, ResultsHandler resultsHandler, OperationOptions options,
                        Set<String> returnAttributesSet, Set<String> fetchFieldsSet,
                        boolean allowPartialAttributeValues, int pageSize, int pageOffset) {
        GroupEntity group = client.getGroup(uid, options, fetchFieldsSet);

        if (group != null) {
            resultsHandler.handle(toConnectorObject(schema, group, returnAttributesSet, allowPartialAttributeValues));
            return 1;
        }
        return 0;
    }

    @Override
    public int getByName(Name name, ResultsHandler resultsHandler, OperationOptions options,
                         Set<String> returnAttributesSet, Set<String> fetchFieldsSet,
                         boolean allowPartialAttributeValues, int pageSize, int pageOffset) {
        GroupEntity group = client.getGroup(name, options, fetchFieldsSet);

        if (group != null) {
            resultsHandler.handle(toConnectorObject(schema, group, returnAttributesSet, allowPartialAttributeValues));
            return 1;
        }
        return 0;
    }

    @Override
    public int getAll(ResultsHandler resultsHandler, OperationOptions options,
                      Set<String> returnAttributesSet, Set<String> fetchFieldsSet,
                      boolean allowPartialAttributeValues, int pageSize, int pageOffset) {
        return client.getGroups((g) -> resultsHandler.handle(toConnectorObject(schema, g, returnAttributesSet, allowPartialAttributeValues)),
                options, fetchFieldsSet, pageSize, pageOffset);
    }
}
