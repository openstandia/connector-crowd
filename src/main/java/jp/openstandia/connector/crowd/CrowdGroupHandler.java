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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jp.openstandia.connector.util.Utils.filterGroups;
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.*;

public class CrowdGroupHandler implements ObjectHandler {

    public static final ObjectClass GROUP_OBJECT_CLASS = new ObjectClass("group");

    private static final Log LOGGER = Log.getLog(CrowdGroupHandler.class);
    private static final Set<String> SUPPORTED_TYPES = Arrays.asList("string", "stringarray").stream().collect(Collectors.toSet());

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
        // The id for the group. Must be unique and unchangeable.
        // Currently, the group doesn't have key like user, so it's same as "groupname".
        sb.addUid("key",
                SchemaDefinition.Types.STRING_CASE_IGNORE,
                null,
                (source) -> source.getName(),
                "name",
                NOT_CREATABLE, NOT_UPDATEABLE
        );

        // username (__NAME__)
        // The name for the group. Must be unique and changeable.
        // Also, it's case-insensitive.
        sb.addName("groupname",
                SchemaDefinition.Types.STRING_CASE_IGNORE,
                (source, dest) -> dest.setGroupName(source),
                (source) -> source.getName(),
                "name",
                REQUIRED, NOT_UPDATEABLE
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

        // Custom Attributes
        Arrays.stream(configuration.getGroupAttributesSchema())
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
                                (source, dest) -> dest.addAttributes(attrName, source),
                                (source, dest) -> dest.addAttributes(attrName, source),
                                (source, dest) -> dest.removeAttributes(attrName, source),
                                (source) -> {
                                    Set<String> values = source.getAttributes().getValues(attrName);
                                    if (values == null) {
                                        return Stream.empty();
                                    }
                                    return values.stream();
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
                (source) -> filterGroups(configuration, client.getGroupsForGroup(source.getName(), configuration.getDefaultQueryPageSize())),
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
        // We need to call another API to add group attributes
        if (mapped.hasAttributesChange) {
            client.updateGroupAttributes(newUid.getNameHintValue(), mapped.updateAttributes);
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
        if (dest.hasAttributesChange) {
            client.updateGroupAttributes(current.getName(), dest.updateAttributes);
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
