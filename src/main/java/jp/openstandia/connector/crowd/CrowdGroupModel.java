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
import com.atlassian.crowd.model.group.GroupType;
import com.atlassian.crowd.model.group.ImmutableGroupWithAttributes;

import java.util.*;
import java.util.stream.Collectors;

public class CrowdGroupModel {
    private final ImmutableGroupWithAttributes.Builder group;
    public Map<String, Set<String>> currentAttributes;
    public boolean hasGroupChange;
    public List<String> addGroups;
    public List<String> removeGroups;

    public CrowdGroupModel(ImmutableGroupWithAttributes.Builder builder) {
        this.group = builder;
    }

    public CrowdGroupModel(GroupEntity group) {
        this.group = ImmutableGroupWithAttributes.builder(group);
        MultiValuedAttributeEntityList attributes = group.getAttributes();
        if (attributes != null && attributes.size() > 0) {
            Map<String, Set<String>> current = new HashMap<>();

            Iterator<MultiValuedAttributeEntity> iter = group.getAttributes().iterator();
            while (iter.hasNext()) {
                MultiValuedAttributeEntity entity = iter.next();
                current.put(entity.getName(), entity.getValues().stream().collect(Collectors.toSet()));
            }
            this.currentAttributes = Collections.unmodifiableMap(current);
        }
    }

    public static CrowdGroupModel create() {
        return new CrowdGroupModel(ImmutableGroupWithAttributes.builder(GroupEntity.newMinimalInstance("")).setType(GroupType.GROUP));
    }

    public ImmutableGroupWithAttributes toGroup() {
        return this.group.build();
    }

    public void setGroupName(String source) {
        this.hasGroupChange = true;
        this.group.setName(source);
    }

    public void setDescription(String s) {
        this.hasGroupChange = true;
        this.group.setDescription(s);
    }

    public void setActive(boolean b) {
        this.hasGroupChange = true;
        this.group.setActive(b);
    }

    public void setGroups(List<String> groups) {
        this.addGroups = groups;
    }

    public void addGroups(List<String> groups) {
        if (this.addGroups == null) {
            this.addGroups = new ArrayList<>();
        }
        this.addGroups.addAll(groups);
    }

    public void removeGroups(List<String> groups) {
        if (this.removeGroups == null) {
            this.removeGroups = new ArrayList<>();
        }
        this.removeGroups.addAll(groups);
    }
}
