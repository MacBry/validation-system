package com.mac.bry.validationsystem.security.filter;

import com.mac.bry.validationsystem.security.service.SessionManagementService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionSecurityFilter - GMP Compliance Tests")
class SessionSecurityFilterTest {

    @Mock
    private SessionManagementService sessionManagementService;

    @Mock
    private FilterChain filterChain;

    @Mock
    private SecurityContext securityContext;

    private SessionSecurityFilter sessionSecurityFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        sessionSecurityFilter = new SessionSecurityFilter(sessionManagementService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("Should skip filter for public endpoints")
    void shouldSkipFilterForPublicEndpoints() throws Exception {
        // Given
        request.setRequestURI("/css/styles.css");

        // When
        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(sessionManagementService);
    }

    @Test
    @DisplayName("Should skip filter for login page")
    void shouldSkipFilterForLoginPage() throws Exception {
        // Given
        request.setRequestURI("/login");

        // When
        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(sessionManagementService);
    }

    @Test
    @DisplayName("Should skip filter for static resources")
    void shouldSkipFilterForStaticResources() throws Exception {
        // Given
        request.setRequestURI("/js/app.js");

        // When
        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(sessionManagementService);
    }

    @Test
    @DisplayName("Should continue processing when no authentication")
    void shouldContinueProcessingWhenNoAuthentication() throws Exception {
        // Given
        request.setRequestURI("/dashboard");
        when(securityContext.getAuthentication()).thenReturn(null);

        // When
        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(sessionManagementService);
    }

    @Test
    @DisplayName("Should continue processing when authentication not authenticated")
    void shouldContinueProcessingWhenAuthenticationNotAuthenticated() throws Exception {
        // Given
        request.setRequestURI("/dashboard");
        Authentication auth = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        when(auth.isAuthenticated()).thenReturn(false);

        // When
        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(sessionManagementService);
    }

    @Test
    @DisplayName("Should perform security checks for authenticated user")
    void shouldPerformSecurityChecksForAuthenticatedUser() throws Exception {
        // Given
        request.setRequestURI("/dashboard");
        Authentication auth = createMockAuthentication("testUser");
        when(securityContext.getAuthentication()).thenReturn(auth);
        when(sessionManagementService.validateSessionSecurity(request)).thenReturn(true);
        when(sessionManagementService.getActiveSessionCount(any())).thenReturn(1);

        // When
        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(sessionManagementService).validateSessionSecurity(request);
        verify(sessionManagementService).getActiveSessionCount(auth.getPrincipal());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should block insecure session and redirect to login")
    void shouldBlockInsecureSessionAndRedirectToLogin() throws Exception {
        // Given
        request.setRequestURI("/dashboard");
        request.setSession(new org.springframework.mock.web.MockHttpSession());
        Authentication auth = createMockAuthentication("testUser");
        when(securityContext.getAuthentication()).thenReturn(auth);
        when(sessionManagementService.validateSessionSecurity(request)).thenReturn(false);

        // When
        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(sessionManagementService).validateSessionSecurity(request);
        verify(sessionManagementService).auditSessionChange(
                anyString(), // old session ID (can be anything in mock)
                isNull(), // new session ID
                eq("SECURITY_VIOLATION_FORCED_LOGOUT"),
                eq("testUser"));

        // Sprawdź czy nie kontynuowano przetwarzania
        verify(filterChain, never()).doFilter(request, response);

        // Sprawdź redirect
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("/login?security_logout=true");
    }

    @Test
    @DisplayName("Should warn about multiple active sessions")
    void shouldWarnAboutMultipleActiveSessions() throws Exception {
        // Given
        request.setRequestURI("/dashboard");
        Authentication auth = createMockAuthentication("testUser");
        when(securityContext.getAuthentication()).thenReturn(auth);
        when(sessionManagementService.validateSessionSecurity(request)).thenReturn(true);
        when(sessionManagementService.getActiveSessionCount(any())).thenReturn(2); // Więcej niż 1

        // When
        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(sessionManagementService).getActiveSessionCount(auth.getPrincipal());
        verify(filterChain).doFilter(request, response); // Mimo ostrzeżenia kontynuujemy
    }

    @Test
    @DisplayName("Should handle session security with session attributes")
    void shouldHandleSessionSecurityWithSessionAttributes() throws Exception {
        // Given
        request.setRequestURI("/dashboard");
        request.setSession(new org.springframework.mock.web.MockHttpSession());
        request.getSession().setAttribute("ORIGINAL_IP", "192.168.1.100");
        request.setRemoteAddr("192.168.1.100");

        Authentication auth = createMockAuthentication("testUser");
        when(securityContext.getAuthentication()).thenReturn(auth);
        when(sessionManagementService.validateSessionSecurity(request)).thenReturn(true);
        when(sessionManagementService.getActiveSessionCount(any())).thenReturn(1);

        // When
        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should detect IP change security violation")
    void shouldDetectIPChangeSecurityViolation() throws Exception {
        // Given
        request.setRequestURI("/dashboard");
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("ORIGINAL_IP", "192.168.1.100");
        request.setSession(session);
        request.setRemoteAddr("10.0.0.5"); // Different IP

        Authentication auth = createMockAuthentication("testUser");
        when(securityContext.getAuthentication()).thenReturn(auth);
        when(sessionManagementService.validateSessionSecurity(request)).thenReturn(true);
        when(sessionManagementService.getActiveSessionCount(any())).thenReturn(1);

        // When
        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        // Then
        // Should redirect to login
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("/login?security_logout=true");
    }

    @Test
    @DisplayName("Should detect security violation flag in session")
    void shouldDetectSecurityViolationFlagInSession() throws Exception {
        // Given
        request.setRequestURI("/dashboard");
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("SECURITY_VIOLATION_FLAG", "PREVIOUS_VIOLATION");
        request.setSession(session);

        Authentication auth = createMockAuthentication("testUser");
        when(securityContext.getAuthentication()).thenReturn(auth);
        when(sessionManagementService.validateSessionSecurity(request)).thenReturn(true);
        when(sessionManagementService.getActiveSessionCount(any())).thenReturn(1);

        // When
        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("/login?security_logout=true");
    }

    @Test
    @DisplayName("Should handle exceptions gracefully and force logout")
    void shouldHandleExceptionsGracefullyAndForceLogout() throws Exception {
        // Given
        request.setRequestURI("/dashboard");
        request.setSession(new org.springframework.mock.web.MockHttpSession());
        Authentication auth = createMockAuthentication("testUser");
        when(securityContext.getAuthentication()).thenReturn(auth);
        when(sessionManagementService.validateSessionSecurity(request))
                .thenThrow(new RuntimeException("Test exception"));

        // When
        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("/login?security_logout=true");
    }

    @Test
    @DisplayName("Should extract client IP from X-Forwarded-For header")
    void shouldExtractClientIPFromXForwardedForHeader() throws Exception {
        // Given
        request.setRequestURI("/dashboard");
        request.addHeader("X-Forwarded-For", "203.0.113.123, 192.168.1.100");
        request.setSession(new org.springframework.mock.web.MockHttpSession());

        Authentication auth = createMockAuthentication("testUser");
        when(securityContext.getAuthentication()).thenReturn(auth);
        when(sessionManagementService.validateSessionSecurity(request)).thenReturn(true);
        when(sessionManagementService.getActiveSessionCount(any())).thenReturn(1);

        // When
        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        // Then
        // Sprawdź czy IP został zapisany w sesji (pierwszy IP z listy)
        String savedIP = (String) request.getSession().getAttribute("ORIGINAL_IP");
        assertThat(savedIP).isEqualTo("203.0.113.123");
    }

    private Authentication createMockAuthentication(String username) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    }
}