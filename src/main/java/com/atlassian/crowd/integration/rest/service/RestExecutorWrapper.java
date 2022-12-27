package com.atlassian.crowd.integration.rest.service;

import com.atlassian.crowd.exception.ApplicationPermissionException;
import com.atlassian.crowd.exception.InvalidAuthenticationException;
import com.atlassian.crowd.exception.OperationFailedException;
import com.atlassian.crowd.exception.UserNotFoundException;
import com.atlassian.crowd.integration.rest.entity.ErrorEntity;
import com.atlassian.crowd.integration.rest.entity.UserEntity;
import com.atlassian.crowd.model.user.User;
import com.atlassian.crowd.service.client.CrowdClient;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

import java.lang.reflect.Field;

public class RestExecutorWrapper {
    private final RestExecutor executor;

    public RestExecutorWrapper(CrowdClient crowdClient) {
        if (crowdClient == null) {
            this.executor = null;
            return;
        }

        // Workaround for using search by key with attributes
        try {
            Field f = crowdClient.getClass().getDeclaredField("executor");
            f.setAccessible(true);
            this.executor = (RestExecutor) f.get(crowdClient);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ConnectorException(e);
        }
    }

    public User getUserByKey(String key) throws UserNotFoundException, OperationFailedException, ApplicationPermissionException, InvalidAuthenticationException {
        try {
            // CAUTION: Need to check this API is supported
            // Crowd 5.1.1: OK
            return (User) this.executor.get("/user?key=%s&expand=attributes", new Object[]{key}).andReceive(UserEntity.class);
        } catch (CrowdRestException e) {
            if (e.getErrorEntity().getReason() == ErrorEntity.ErrorReason.USER_NOT_FOUND) {
                UserNotFoundException.throwNotFoundByExternalId(key);
            }

            throw handleCommonExceptions(e);
        }
    }

    private static OperationFailedException handleCommonExceptions(CrowdRestException e) throws ApplicationPermissionException, OperationFailedException {
        if (e.getErrorEntity().getReason() == ErrorEntity.ErrorReason.APPLICATION_PERMISSION_DENIED) {
            throw new ApplicationPermissionException(e.getErrorEntity().getMessage(), e);
        } else {
            throw new OperationFailedException("Error from Crowd server propagated to here via REST API (check the Crowd server logs for details): " + e.getMessage(), e);
        }
    }
}
