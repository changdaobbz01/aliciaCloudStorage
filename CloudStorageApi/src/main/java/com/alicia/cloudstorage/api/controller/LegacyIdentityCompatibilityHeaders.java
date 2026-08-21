package com.alicia.cloudstorage.api.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

final class LegacyIdentityCompatibilityHeaders {

    static final String DEPRECATION_HEADER = "Deprecation";
    static final String DEPRECATION_VALUE = "true";

    private LegacyIdentityCompatibilityHeaders() {
    }

    static void markDeprecated(HttpServletResponse response, String successorPath) {
        response.setHeader(DEPRECATION_HEADER, DEPRECATION_VALUE);
        response.setHeader(HttpHeaders.LINK, "<" + successorPath + ">; rel=\"successor-version\"");
    }
}
