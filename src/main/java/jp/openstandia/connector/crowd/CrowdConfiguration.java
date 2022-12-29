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

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public class CrowdConfiguration extends AbstractConfiguration {

    private String baseURL;
    private String applicationName;
    private GuardedString applicationPassword;
    private String httpProxyHost;
    private int httpProxyPort = 3128;
    private String httpProxyUser;
    private GuardedString httpProxyPassword;
    private int defaultQueryPageSize = 50;
    private int connectionTimeoutInSeconds = 5000;
    private int socketTimeoutInMilliseconds = 600000;
    private String[] userAttributesSchema = new String[]{};
    private String[] groupAttributesSchema = new String[]{};

    @ConfigurationProperty(
            order = 1,
            displayMessageKey = "Crowd Base URL",
            helpMessageKey = "Crowd Base URL which is connected from this connector.",
            required = true,
            confidential = false)
    public String getBaseURL() {
        return baseURL;
    }

    public void setBaseURL(String baseURL) {
        this.baseURL = baseURL;
    }

    @ConfigurationProperty(
            order = 2,
            displayMessageKey = "Crowd Application Name",
            helpMessageKey = "Name of the application.",
            required = true,
            confidential = false)
    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    @ConfigurationProperty(
            order = 3,
            displayMessageKey = "Crowd Application Password",
            helpMessageKey = "Password of the application.",
            required = true,
            confidential = true)
    public GuardedString getApplicationPassword() {
        return applicationPassword;
    }

    public void setApplicationPassword(GuardedString applicationPassword) {
        this.applicationPassword = applicationPassword;
    }

    @ConfigurationProperty(
            order = 4,
            displayMessageKey = "HTTP Proxy Host",
            helpMessageKey = "Hostname for the HTTP Proxy.",
            required = false,
            confidential = false)
    public String getHttpProxyHost() {
        return httpProxyHost;
    }

    public void setHttpProxyHost(String httpProxyHost) {
        this.httpProxyHost = httpProxyHost;
    }

    @ConfigurationProperty(
            order = 5,
            displayMessageKey = "HTTP Proxy Port",
            helpMessageKey = "Port for the HTTP Proxy. (Default: 3128)",
            required = false,
            confidential = false)
    public int getHttpProxyPort() {
        return httpProxyPort;
    }

    public void setHttpProxyPort(int httpProxyPort) {
        this.httpProxyPort = httpProxyPort;
    }

    @ConfigurationProperty(
            order = 6,
            displayMessageKey = "HTTP Proxy User",
            helpMessageKey = "Username for the HTTP Proxy Authentication.",
            required = false,
            confidential = false)
    public String getHttpProxyUser() {
        return httpProxyUser;
    }

    public void setHttpProxyUser(String httpProxyUser) {
        this.httpProxyUser = httpProxyUser;
    }

    @ConfigurationProperty(
            order = 7,
            displayMessageKey = "HTTP Proxy Password",
            helpMessageKey = "Password for the HTTP Proxy Authentication.",
            required = false,
            confidential = true)
    public GuardedString getHttpProxyPassword() {
        return httpProxyPassword;
    }

    public void setHttpProxyPassword(GuardedString httpProxyPassword) {
        this.httpProxyPassword = httpProxyPassword;
    }

    @ConfigurationProperty(
            order = 8,
            displayMessageKey = "Default Query Page Size",
            helpMessageKey = "Number of results to return per page. (Default: 50)",
            required = false,
            confidential = false)
    public int getDefaultQueryPageSize() {
        return defaultQueryPageSize;
    }

    public void setDefaultQueryPageSize(int defaultQueryPageSize) {
        this.defaultQueryPageSize = defaultQueryPageSize;
    }

    @ConfigurationProperty(
            order = 9,
            displayMessageKey = "Connection Timeout (in milliseconds)",
            helpMessageKey = "Connection timeout when connecting to Crowd. (Default: 5000)",
            required = false,
            confidential = false)
    public int getConnectionTimeoutInSeconds() {
        return connectionTimeoutInSeconds;
    }

    public void setConnectionTimeoutInSeconds(int connectionTimeoutInSeconds) {
        this.connectionTimeoutInSeconds = connectionTimeoutInSeconds;
    }

    @ConfigurationProperty(
            order = 10,
            displayMessageKey = "Socket Timeout (in milliseconds)",
            helpMessageKey = "Socket timeout when fetching data from Crowd. (Default: 600000)",
            required = false,
            confidential = false)
    public int getSocketTimeoutInMilliseconds() {
        return socketTimeoutInMilliseconds;
    }

    public void setSocketTimeoutInMilliseconds(int socketTimeoutInMilliseconds) {
        this.socketTimeoutInMilliseconds = socketTimeoutInMilliseconds;
    }

    @ConfigurationProperty(
            order = 11,
            displayMessageKey = "User Attributes Schema",
            helpMessageKey = "Define schema for user attributes. The format is \"fieldName$dataType\". " +
                    "The dataType is selected from \"string\", \"stringArray\".",
            required = false,
            confidential = false)
    public String[] getUserAttributesSchema() {
        return userAttributesSchema;
    }

    public void setUserAttributesSchema(String[] userAttributesSchema) {
        this.userAttributesSchema = userAttributesSchema;
    }

    @ConfigurationProperty(
            order = 12,
            displayMessageKey = "Group Attributes Schema",
            helpMessageKey = "Define schema for group attributes. The format is \"fieldName$dataType\". " +
                    "The dataType is selected from \"string\", \"stringArray\".",
            required = false,
            confidential = false)
    public String[] getGroupAttributesSchema() {
        return groupAttributesSchema;
    }

    public void setGroupAttributesSchema(String[] groupAttributesSchema) {
        this.groupAttributesSchema = groupAttributesSchema;
    }

    @Override
    public void validate() {
        if (baseURL == null) {
            throw new ConfigurationException("Crowd Base URL is required");
        }
        if (applicationName == null) {
            throw new ConfigurationException("Crowd Application Name is required");
        }
        if (applicationPassword == null) {
            throw new ConfigurationException("Crowd Application Password is required");
        }
    }
}
