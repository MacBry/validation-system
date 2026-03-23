package com.mac.bry.validationsystem.security.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility for validating URLs to prevent Open Redirect vulnerabilities.
 */
@Slf4j
@UtilityClass
public class UrlValidator {

    /**
     * Checks if a URL is a safe internal redirect target.
     * A safe URL must start with a single '/' and must not be a protocol-relative link (starting with '//').
     *
     * @param url the URL to validate
     * @return true if the URL is safe and internal, false otherwise
     */
    public static boolean isSafeInternalUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        // Prevent protocol-relative redirects (e.g., //evil.com)
        if (url.startsWith("//")) {
            log.warn("Potential Open Redirect attempt blocked: URL starts with // ({})", url);
            return false;
        }

        // Must start with / to be considered internal
        if (url.startsWith("/")) {
            // Further check to ensure it doesn't contain a schema like http:// or https:// later on
            // (Standard Java URL/URI parsing would be better but this covers basic cases)
            if (url.contains("://")) {
                 log.warn("Potential Open Redirect attempt blocked: URL contains schema ({})", url);
                 return false;
            }
            return true;
        }

        log.warn("URL rejected as unsafe for redirect: {}", url);
        return false;
    }
}
