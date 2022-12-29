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

import com.atlassian.crowd.integration.rest.entity.MultiValuedAttributeEntity;
import com.atlassian.crowd.integration.rest.entity.UserEntity;
import com.atlassian.crowd.model.user.ImmutableUserWithAttributes;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.identityconnectors.common.security.GuardedString;

import java.util.*;
import java.util.stream.Collectors;

public class CrowdUserModel {
    private final ImmutableUserWithAttributes.Builder user;
    public SetMultimap<String, String> createAttributes;
    public Map<String, Set<String>> currentAttributes;
    public Map<String, Set<String>> updateAttributes;
    public GuardedString password;
    public String newUserName;
    public boolean hasUserChange;
    public boolean hasUsernameChange;
    public boolean hasPasswordChange;
    public boolean hasAttributesChange;
    public List<String> addGroups;
    public List<String> removeGroups;

    private CrowdUserModel(ImmutableUserWithAttributes.Builder builder) {
        this.user = builder;
    }

    private CrowdUserModel(UserEntity user) {
        this.user = ImmutableUserWithAttributes.builder(user);
        if (user.getAttributes().size() > 0) {
            Map<String, Set<String>> current = new HashMap<>();

            Iterator<MultiValuedAttributeEntity> iter = user.getAttributes().iterator();
            while (iter.hasNext()) {
                MultiValuedAttributeEntity entity = iter.next();
                current.put(entity.getName(), entity.getValues().stream().collect(Collectors.toSet()));
            }
            this.currentAttributes = Collections.unmodifiableMap(current);
        }
    }

    public static CrowdUserModel create() {
        return new CrowdUserModel(ImmutableUserWithAttributes.builder(UserEntity.newMinimalInstance("")));
    }

    public static CrowdUserModel create(UserEntity user) {
        return new CrowdUserModel(user);
    }

    public ImmutableUserWithAttributes toUser() {
        // Create user API can have the attributes
        if (this.createAttributes != null && !this.createAttributes.isEmpty()) {
            this.user.setAttributes(this.createAttributes);
        }
        return this.user.build();
    }

    public void setUserName(String s) {
        this.hasUsernameChange = true;
        this.newUserName = s;
    }

    public void setFirstName(String s) {
        this.hasUserChange = true;
        this.user.firstName(s);
    }

    public void setLastName(String s) {
        this.hasUserChange = true;
        this.user.lastName(s);
    }

    public void setDisplayName(String s) {
        this.hasUserChange = true;
        this.user.displayName(s);
    }

    public void setActive(boolean b) {
        this.hasUserChange = true;
        this.user.active(b);
    }

    public void setEmailAddress(String s) {
        this.hasUserChange = true;
        this.user.emailAddress(s);
    }

    public void setPassword(GuardedString gs) {
        this.hasPasswordChange = true;
        this.password = gs;
    }

    // For create single attribute
    public void createAttribute(String attrName, String value) {
        if (this.createAttributes == null) {
            this.createAttributes = HashMultimap.create();
        }
        this.createAttributes.put(attrName, value);
    }

    // For update single attribute
    public void replaceAttribute(String attrName, String value) {
        this.hasAttributesChange = true;
        if (this.updateAttributes == null) {
            this.updateAttributes = new HashMap<>();
        }
        Set<String> newValue = new HashSet<>(1);

        // Empty set will clears the attribute
        if (value != null) {
            newValue.add(value);
        }
        this.updateAttributes.put(attrName, newValue);
    }

    // For create multiple attribute
    public void createAttributes(String attrName, List<String> values) {
        if (this.createAttributes == null) {
            this.createAttributes = HashMultimap.create();
        }
        this.createAttributes.putAll(attrName, values);
    }

    // For update(ADD) multiple attribute
    public void addAttributes(String attrName, List<String> values) {
        this.hasAttributesChange = true;
        if (updateAttributes == null) {
            this.updateAttributes = new HashMap<>();
        }

        Set<String> current = null;
        if (this.currentAttributes != null) {
            current = this.currentAttributes.get(attrName);
        }
        if (current == null) {
            current = new HashSet<>(values.size());
        }
        current.addAll(values);

        this.updateAttributes.put(attrName, current);
    }

    // For update(DEL) multiple attribute
    public void removeAttributes(String attrName, List<String> values) {
        this.hasAttributesChange = true;
        if (updateAttributes == null) {
            this.updateAttributes = new HashMap<>();
        }

        Set<String> current = null;
        if (this.currentAttributes != null) {
            current = this.currentAttributes.get(attrName);
        }
        if (current == null) {
            current = Collections.emptySet();
        }

        current.removeAll(values);

        // Empty set will clear the attribute
        this.updateAttributes.put(attrName, current);
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
