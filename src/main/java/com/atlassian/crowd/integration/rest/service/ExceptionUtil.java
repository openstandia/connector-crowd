package com.atlassian.crowd.integration.rest.service;

import com.atlassian.crowd.exception.CrowdException;

public class ExceptionUtil {

    public static int getStatusCode(CrowdException e) {
        Throwable cause = e.getCause();
        if (cause != null && cause instanceof CrowdRestException) {
            return ((CrowdRestException) cause).getStatusCode();
        }
        return -1;
    }
}
