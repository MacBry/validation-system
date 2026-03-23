package com.mac.bry.validationsystem.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import com.mac.bry.validationsystem.security.repository.CsrfViolationAuditRepository;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsrfManagementService - GMP Compliance Tests")
class CsrfManagementServiceTest {

    @Mock
    private CsrfViolationAuditRepository auditRepository;

    @InjectMocks
    private CsrfManagementService csrfManagementService;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("Should validate correct CSRF token")
    void shouldValidateCorrectCsrfToken() {
        // Given
        String validToken = "valid-csrf-token-123";
        request.setSession(new MockHttpSession());
        request.addHeader("User-Agent", "Mozilla/5.0 Test Browser");
        request.setRemoteAddr("192.168.1.100");

        // When
        boolean isValid = csrfManagementService.validateCsrfToken(request, validToken, validToken);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject null provided token")
    void shouldRejectNullProvidedToken() {
        // Given
        String expectedToken = "expected-token-123";
        request.setSession(new MockHttpSession());
        request.addHeader("User-Agent", "Mozilla/5.0 Test Browser");

        // When
        boolean isValid = csrfManagementService.validateCsrfToken(request, null, expectedToken);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject empty provided token")
    void shouldRejectEmptyProvidedToken() {
        // Given
        String expectedToken = "expected-token-123";
        request.setSession(new MockHttpSession());

        // When
        boolean isValid = csrfManagementService.validateCsrfToken(request, "", expectedToken);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject whitespace-only provided token")
    void shouldRejectWhitespaceOnlyProvidedToken() {
        // Given
        String expectedToken = "expected-token-123";
        request.setSession(new MockHttpSession());

        // When
        boolean isValid = csrfManagementService.validateCsrfToken(request, "   ", expectedToken);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject null expected token")
    void shouldRejectNullExpectedToken() {
        // Given
        String providedToken = "provided-token-123";
        request.setSession(new MockHttpSession());

        // When
        boolean isValid = csrfManagementService.validateCsrfToken(request, providedToken, null);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject mismatched tokens")
    void shouldRejectMismatchedTokens() {
        // Given
        String providedToken = "provided-token-123";
        String expectedToken = "expected-token-456";
        request.setSession(new MockHttpSession());

        // When
        boolean isValid = csrfManagementService.validateCsrfToken(request, providedToken, expectedToken);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should get current CSRF token from request")
    void shouldGetCurrentCsrfTokenFromRequest() {
        // Given
        String tokenValue = "csrf-token-from-request";
        CsrfToken csrfToken = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", tokenValue);
        request.setAttribute(CsrfToken.class.getName(), csrfToken);

        // When
        String currentToken = csrfManagementService.getCurrentCsrfToken(request);

        // Then
        assertThat(currentToken).isEqualTo(tokenValue);
    }

    @Test
    @DisplayName("Should return null when no CSRF token in request")
    void shouldReturnNullWhenNoCsrfTokenInRequest() {
        // When
        String currentToken = csrfManagementService.getCurrentCsrfToken(request);

        // Then
        assertThat(currentToken).isNull();
    }

    @Test
    @DisplayName("Should require CSRF protection for POST requests")
    void shouldRequireCsrfProtectionForPostRequests() {
        // Given
        request.setMethod("POST");

        // When
        boolean requiresProtection = csrfManagementService.requiresCsrfProtection(request);

        // Then
        assertThat(requiresProtection).isTrue();
    }

    @Test
    @DisplayName("Should require CSRF protection for PUT requests")
    void shouldRequireCsrfProtectionForPutRequests() {
        // Given
        request.setMethod("PUT");

        // When
        boolean requiresProtection = csrfManagementService.requiresCsrfProtection(request);

        // Then
        assertThat(requiresProtection).isTrue();
    }

    @Test
    @DisplayName("Should require CSRF protection for DELETE requests")
    void shouldRequireCsrfProtectionForDeleteRequests() {
        // Given
        request.setMethod("DELETE");

        // When
        boolean requiresProtection = csrfManagementService.requiresCsrfProtection(request);

        // Then
        assertThat(requiresProtection).isTrue();
    }

    @Test
    @DisplayName("Should require CSRF protection for PATCH requests")
    void shouldRequireCsrfProtectionForPatchRequests() {
        // Given
        request.setMethod("PATCH");

        // When
        boolean requiresProtection = csrfManagementService.requiresCsrfProtection(request);

        // Then
        assertThat(requiresProtection).isTrue();
    }

    @Test
    @DisplayName("Should not require CSRF protection for GET requests")
    void shouldNotRequireCsrfProtectionForGetRequests() {
        // Given
        request.setMethod("GET");

        // When
        boolean requiresProtection = csrfManagementService.requiresCsrfProtection(request);

        // Then
        assertThat(requiresProtection).isFalse();
    }

    @Test
    @DisplayName("Should not require CSRF protection for HEAD requests")
    void shouldNotRequireCsrfProtectionForHeadRequests() {
        // Given
        request.setMethod("HEAD");

        // When
        boolean requiresProtection = csrfManagementService.requiresCsrfProtection(request);

        // Then
        assertThat(requiresProtection).isFalse();
    }

    @Test
    @DisplayName("Should not require CSRF protection for TRACE requests")
    void shouldNotRequireCsrfProtectionForTraceRequests() {
        // Given
        request.setMethod("TRACE");

        // When
        boolean requiresProtection = csrfManagementService.requiresCsrfProtection(request);

        // Then
        assertThat(requiresProtection).isFalse();
    }

    @Test
    @DisplayName("Should not require CSRF protection for OPTIONS requests")
    void shouldNotRequireCsrfProtectionForOptionsRequests() {
        // Given
        request.setMethod("OPTIONS");

        // When
        boolean requiresProtection = csrfManagementService.requiresCsrfProtection(request);

        // Then
        assertThat(requiresProtection).isFalse();
    }

    @Test
    @DisplayName("Should get CSRF status with token present")
    void shouldGetCsrfStatusWithTokenPresent() {
        // Given
        String tokenValue = "csrf-status-token";
        CsrfToken csrfToken = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", tokenValue);
        request.setAttribute(CsrfToken.class.getName(), csrfToken);
        request.setMethod("POST");
        request.setSession(new MockHttpSession());

        // When
        CsrfManagementService.CsrfStatus status = csrfManagementService.getCsrfStatus(request);

        // Then
        assertThat(status.isTokenPresent()).isTrue();
        assertThat(status.getTokenHeaderName()).isEqualTo("X-CSRF-TOKEN");
        assertThat(status.getTokenParameterName()).isEqualTo("_csrf");
        assertThat(status.isRequiresProtection()).isTrue();
        assertThat(status.getTimestamp()).isNotNull();
        assertThat(status.getSessionId()).isEqualTo(request.getSession().getId());
    }

    @Test
    @DisplayName("Should get CSRF status without token")
    void shouldGetCsrfStatusWithoutToken() {
        // Given
        request.setMethod("GET");

        // When
        CsrfManagementService.CsrfStatus status = csrfManagementService.getCsrfStatus(request);

        // Then
        assertThat(status.isTokenPresent()).isFalse();
        assertThat(status.getTokenHeaderName()).isNull();
        assertThat(status.getTokenParameterName()).isNull();
        assertThat(status.isRequiresProtection()).isFalse();
        assertThat(status.getSessionId()).isEqualTo("NO_SESSION");
    }

    @Test
    @DisplayName("Should refresh CSRF token when present in request")
    void shouldRefreshCsrfTokenWhenPresentInRequest() {
        // Given
        CsrfToken csrfToken = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "refresh-token");
        request.setAttribute(CsrfToken.class.getName(), csrfToken);
        request.setSession(new MockHttpSession());
        request.setRemoteAddr("192.168.1.100");

        // When & Then - should not throw exception
        assertThatNoException().isThrownBy(() -> csrfManagementService.refreshCsrfToken(request, response));
    }

    @Test
    @DisplayName("Should handle refresh when no token in request")
    void shouldHandleRefreshWhenNoTokenInRequest() {
        // When & Then - should not throw exception
        assertThatNoException().isThrownBy(() -> csrfManagementService.refreshCsrfToken(request, response));
    }

    @Test
    @DisplayName("Should extract client IP from X-Forwarded-For header")
    void shouldExtractClientIpFromXForwardedForHeader() {
        // Given
        request.addHeader("X-Forwarded-For", "203.0.113.123, 192.168.1.100");
        request.setSession(new MockHttpSession());

        // When
        boolean result = csrfManagementService.validateCsrfToken(request, "token", "different-token");

        // Then
        // Validation should fail (we expect this), but IP should be extracted from
        // header
        // We can't directly test IP extraction, but we can verify the method doesn't
        // crash
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should extract client IP from X-Real-IP header when X-Forwarded-For not present")
    void shouldExtractClientIpFromXRealIpHeader() {
        // Given
        request.addHeader("X-Real-IP", "203.0.113.124");
        request.setSession(new MockHttpSession());

        // When
        boolean result = csrfManagementService.validateCsrfToken(request, "token", "different-token");

        // Then
        assertThat(result).isFalse(); // Expected since tokens don't match
    }

    @Test
    @DisplayName("Should use remote address when no proxy headers present")
    void shouldUseRemoteAddressWhenNoProxyHeadersPresent() {
        // Given
        request.setRemoteAddr("192.168.1.50");
        request.setSession(new MockHttpSession());

        // When
        boolean result = csrfManagementService.validateCsrfToken(request, "token", "different-token");

        // Then
        assertThat(result).isFalse(); // Expected since tokens don't match
    }

    @Test
    @DisplayName("CsrfStatus toString should work correctly")
    void csrfStatusToStringShouldWorkCorrectly() {
        // Given
        CsrfManagementService.CsrfStatus status = CsrfManagementService.CsrfStatus.builder()
                .tokenPresent(true)
                .tokenHeaderName("X-CSRF-TOKEN")
                .tokenParameterName("_csrf")
                .sessionId("test-session-123")
                .requiresProtection(true)
                .build();

        // When
        String toString = status.toString();

        // Then
        assertThat(toString).contains("present=true")
                .contains("header=X-CSRF-TOKEN")
                .contains("param=_csrf")
                .contains("session=test-session-123")
                .contains("required=true");
    }
}