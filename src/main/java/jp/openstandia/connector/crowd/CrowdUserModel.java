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
        if (this.createAttributes != null && !this.currentAttributes.isEmpty()) {
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

    // For create/update single attribute
    public void replaceAttribute(String key, String value) {
        this.hasAttributesChange = true;
        if (this.updateAttributes == null) {
            this.updateAttributes = new HashMap<>();
        }
        Set<String> newValue = new HashSet<>(1);

        // Empty set will clears the attribute
        if (value != null) {
            newValue.add(value);
        }
        this.updateAttributes.put(key, newValue);
    }

    // For create multiple attribute
    public void setAttributes(String attrName, List<String> values) {
        if (this.createAttributes == null) {
            this.createAttributes = HashMultimap.create();
        }
        this.createAttributes.putAll(attrName, values);
    }

    // For update(ADD) multiple attribute
    public void addAttributes(String key, List<String> values) {
        this.hasAttributesChange = true;
        if (updateAttributes == null) {
            this.updateAttributes = new HashMap<>();
        }

        Set<String> current = null;
        if (this.currentAttributes != null) {
            current = this.currentAttributes.get(key);
        }
        if (current == null) {
            current = new HashSet<>(values.size());
        }
        current.addAll(values);

        this.updateAttributes.put(key, current);
    }

    // For update(DEL) multiple attribute
    public void removeAttributes(String key, List<String> values) {
        this.hasAttributesChange = true;
        if (updateAttributes == null) {
            this.updateAttributes = new HashMap<>();
        }

        Set<String> current = null;
        if (this.currentAttributes != null) {
            current = this.currentAttributes.get(key);
        }
        if (current == null) {
            current = Collections.emptySet();
        }

        current.removeAll(values);

        // Empty set will clear the attribute
        this.updateAttributes.put(key, current);
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
