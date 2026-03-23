package com.mac.bry.validationsystem.security.handler;

import com.mac.bry.validationsystem.security.service.CsrfManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsrfViolationHandler - GMP Compliance Tests")
class CsrfViolationHandlerTest {

    @Mock
    private CsrfManagementService csrfManagementService;

    private CsrfViolationHandler csrfViolationHandler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        csrfViolationHandler = new CsrfViolationHandler(csrfManagementService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setSession(new MockHttpSession());
    }

    @Test
    @DisplayName("Should handle CSRF violation with browser redirect")
    void shouldHandleCsrfViolationWithBrowserRedirect() throws Exception {
        // Given
        CsrfException csrfException = new CsrfException("Invalid CSRF token");
        request.setMethod("POST");
        request.setRequestURI("/companies");
        request.setScheme("https");
        request.setServerName("localhost");
        request.setServerPort(8443);
        request.addHeader("Accept", "text/html");

        // When
        csrfViolationHandler.handle(request, response, csrfException);

        // Then
        assertThat(response.getStatus()).isEqualTo(302); // Redirect
        assertThat(response.getHeader("Location")).contains("/error/csrf");
        assertThat(response.getHeader("Location")).contains("type=INVALID_CSRF_TOKEN");
    }

    @Test
    @DisplayName("Should handle CSRF violation with AJAX JSON response")
    void shouldHandleCsrfViolationWithAjaxJsonResponse() throws Exception {
        // Given
        CsrfException csrfException = new CsrfException("CSRF token null");
        request.setMethod("POST");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        request.addHeader("Accept", "application/json");

        // When
        csrfViolationHandler.handle(request, response, csrfException);

        // Then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");

        String responseContent = response.getContentAsString();
        assertThat(responseContent).contains("csrf_violation");
        assertThat(responseContent).contains("MISSING_CSRF_TOKEN");
        assertThat(responseContent).contains("Żądanie zostało odrzucone");
    }

    @Test
    @DisplayName("Should detect missing CSRF token violation type")
    void shouldDetectMissingCsrfTokenViolationType() throws Exception {
        // Given
        CsrfException csrfException = new CsrfException("CSRF token is null");
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        // When
        csrfViolationHandler.handle(request, response, csrfException);

        // Then
        String responseContent = response.getContentAsString();
        assertThat(responseContent).contains("MISSING_CSRF_TOKEN");
    }

    @Test
    @DisplayName("Should detect invalid CSRF token violation type")
    void shouldDetectInvalidCsrfTokenViolationType() throws Exception {
        // Given
        CsrfException csrfException = new CsrfException("Invalid CSRF token mismatch");
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        // When
        csrfViolationHandler.handle(request, response, csrfException);

        // Then
        String responseContent = response.getContentAsString();
        assertThat(responseContent).contains("INVALID_CSRF_TOKEN");
    }

    @Test
    @DisplayName("Should detect session CSRF violation type")
    void shouldDetectSessionCsrfViolationType() throws Exception {
        // Given
        CsrfException csrfException = new CsrfException("Session CSRF violation detected");
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        // When
        csrfViolationHandler.handle(request, response, csrfException);

        // Then
        String responseContent = response.getContentAsString();
        assertThat(responseContent).contains("SESSION_CSRF_VIOLATION");
    }

    @Test
    @DisplayName("Should handle CSRF violation with external referer as high threat")
    void shouldHandleCsrfViolationWithExternalRefererAsHighThreat() throws Exception {
        // Given
        CsrfException csrfException = new CsrfException("CSRF token mismatch");
        request.setMethod("POST");
        request.setServerName("localhost");
        request.setScheme("https");
        request.addHeader("Referer", "https://malicious-site.com/attack");
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        // When
        csrfViolationHandler.handle(request, response, csrfException);

        // Then
        assertThat(response.getStatus()).isEqualTo(403);
        // High threat should be logged (we can't easily test logging, but method should
        // complete)
    }

    @Test
    @DisplayName("Should handle CSRF violation with missing referer")
    void shouldHandleCsrfViolationWithMissingReferer() throws Exception {
        // Given
        CsrfException csrfException = new CsrfException("CSRF attack detected");
        request.setMethod("POST");
        // No referer header added
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        // When
        csrfViolationHandler.handle(request, response, csrfException);

        // Then
        assertThat(response.getStatus()).isEqualTo(403);
        // Missing referer is treated as HIGH threat level
    }

    @Test
    @DisplayName("Should handle CSRF violation with suspicious user agent")
    void shouldHandleCsrfViolationWithSuspiciousUserAgent() throws Exception {
        // Given
        CsrfException csrfException = new CsrfException("CSRF token invalid");
        request.addHeader("User-Agent", "Bot"); // Very short user agent
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        // When
        csrfViolationHandler.handle(request, response, csrfException);

        // Then
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Should handle general access denied (non-CSRF)")
    void shouldHandleGeneralAccessDenied() throws Exception {
        // Given
        AccessDeniedException accessDeniedException = new AccessDeniedException("Access is denied");
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        // When
        csrfViolationHandler.handle(request, response, accessDeniedException);

        // Then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");

        String responseContent = response.getContentAsString();
        assertThat(responseContent).contains("access_denied");
        assertThat(responseContent).contains("Brak uprawnień");
    }

    @Test
    @DisplayName("Should handle general access denied with browser redirect")
    void shouldHandleGeneralAccessDeniedWithBrowserRedirect() throws Exception {
        // Given
        AccessDeniedException accessDeniedException = new AccessDeniedException("Access is denied");
        request.addHeader("Accept", "text/html");

        // When
        csrfViolationHandler.handle(request, response, accessDeniedException);

        // Then
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("/error/access-denied");
    }

    @Test
    @DisplayName("Should extract client IP from X-Forwarded-For header")
    void shouldExtractClientIpFromXForwardedForHeader() throws Exception {
        // Given
        CsrfException csrfException = new CsrfException("CSRF violation");
        request.addHeader("X-Forwarded-For", "203.0.113.123, 192.168.1.100");
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        // When
        csrfViolationHandler.handle(request, response, csrfException);

        // Then
        assertThat(response.getStatus()).isEqualTo(403);
        // IP extraction should work (we can't directly test it, but method should
        // complete)
    }

    @Test
    @DisplayName("Should extract client IP from X-Real-IP header")
    void shouldExtractClientIpFromXRealIpHeader() throws Exception {
        // Given
        CsrfException csrfException = new CsrfException("CSRF violation");
        request.addHeader("X-Real-IP", "203.0.113.124");
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        // When
        csrfViolationHandler.handle(request, response, csrfException);

        // Then
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Should use remote address when no proxy headers")
    void shouldUseRemoteAddressWhenNoProxyHeaders() throws Exception {
        // Given
        CsrfException csrfException = new CsrfException("CSRF violation");
        request.setRemoteAddr("192.168.1.50");
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        // When
        csrfViolationHandler.handle(request, response, csrfException);

        // Then
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Should detect AJAX request by Accept header")
    void shouldDetectAjaxRequestByAcceptHeader() throws Exception {
        // Given
        CsrfException csrfException = new CsrfException("CSRF violation");
        request.addHeader("Accept", "application/json");
        // No X-Requested-With header

        // When
        csrfViolationHandler.handle(request, response, csrfException);

        // Then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
    }

    @Test
    @DisplayName("Should handle request without session")
    void shouldHandleRequestWithoutSession() throws Exception {
        // Given
        CsrfException csrfException = new CsrfException("CSRF violation");
        request.setSession(null);
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        // When
        csrfViolationHandler.handle(request, response, csrfException);

        // Then
        assertThat(response.getStatus()).isEqualTo(403);
        // Should handle gracefully without throwing exceptions
    }

    @Test
    @DisplayName("Should encode return URL in redirect")
    void shouldEncodeReturnUrlInRedirect() throws Exception {
        // Given
        CsrfException csrfException = new CsrfException("CSRF violation");
        request.setMethod("POST");
        request.setRequestURI("/companies?param=value with spaces");
        request.setScheme("https");
        request.setServerName("localhost");
        request.setServerPort(8443);
        request.addHeader("Accept", "text/html");

        // When
        csrfViolationHandler.handle(request, response, csrfException);

        // Then
        assertThat(response.getStatus()).isEqualTo(302);
        String location = response.getHeader("Location");
        assertThat(location).contains("return=");
        // URL should be encoded with '+' for space
        assertThat(location).contains("+");
    }
}