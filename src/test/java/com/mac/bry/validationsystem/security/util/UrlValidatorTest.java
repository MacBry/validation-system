package com.mac.bry.validationsystem.security.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    @Test
    void shouldAcceptSimpleInternalUrl() {
        assertTrue(UrlValidator.isSafeInternalUrl("/dashboard"));
        assertTrue(UrlValidator.isSafeInternalUrl("/validations/1?status=DRAFT"));
    }

    @Test
    void shouldRejectNullOrEmpty() {
        assertFalse(UrlValidator.isSafeInternalUrl(null));
        assertFalse(UrlValidator.isSafeInternalUrl(""));
        assertFalse(UrlValidator.isSafeInternalUrl("   "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "//google.com",
            "////evil.com",
            "//127.0.0.1"
    })
    void shouldRejectProtocolRelativeUrls(String url) {
        assertFalse(UrlValidator.isSafeInternalUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://evil.com",
            "https://evil.com",
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>"
    })
    void shouldRejectExternalOrMaliciousSchemas(String url) {
        assertFalse(UrlValidator.isSafeInternalUrl(url));
    }

    @Test
    void shouldRejectInternalUrlWithSchemaInjected() {
        assertFalse(UrlValidator.isSafeInternalUrl("/redirect?target=https://evil.com"));
        // While standard internal paths like /foo?url=http://... might be legitimate for some apps,
        // for simple redirects, we block any schema to be conservative.
    }

    @Test
    void shouldRejectUrlWithoutLeadingSlash() {
        assertFalse(UrlValidator.isSafeInternalUrl("dashboard"));
        assertFalse(UrlValidator.isSafeInternalUrl("google.com"));
    }
}
