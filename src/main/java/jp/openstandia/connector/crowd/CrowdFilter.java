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

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;

public class CrowdFilter {
    final String attributeName;
    final FilterType filterType;
    final Attribute attributeValue;

    public CrowdFilter(String attributeName, FilterType filterType, Attribute attributeValue) {
        this.attributeName = attributeName;
        this.filterType = filterType;
        this.attributeValue = attributeValue;
    }

    public CrowdFilter(String attributeName, FilterType filterType) {
        this.attributeName = attributeName;
        this.filterType = filterType;
        this.attributeValue = null;
    }

    public boolean isByName() {
        return attributeName.equals(Name.NAME) && filterType == FilterType.EXACT_MATCH;
    }

    public boolean isByUid() {
        return attributeName.equals(Uid.NAME) && filterType == FilterType.EXACT_MATCH;
    }

    public enum FilterType {
        EXACT_MATCH;
    }

    @Override
    public String toString() {
        return "CrowdFilter{" +
                "attributeName='" + attributeName + '\'' +
                ", filterType=" + filterType +
                ", attributeValue='" + attributeValue + '\'' +
                '}';
    }
}
