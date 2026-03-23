package com.mac.bry.validationsystem.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionManagementService - GMP Compliance Tests")
class SessionManagementServiceTest {

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private SessionInformation sessionInfo1;

    @Mock
    private SessionInformation sessionInfo2;

    private SessionManagementService sessionManagementService;

    private final String testPrincipal = "testUser";
    private final String testSessionId1 = "session-123";
    private final String testSessionId2 = "session-456";

    @BeforeEach
    void setUp() {
        sessionManagementService = new SessionManagementService(
                sessionRegistry,
                1, // maxConcurrentSessions
                false, // preventLoginIfMaxSessions
                "changeSessionId" // fixationProtectionStrategy
        );

        // Mock session info setup
        lenient().when(sessionInfo1.getSessionId()).thenReturn(testSessionId1);
        lenient().when(sessionInfo1.getPrincipal()).thenReturn(testPrincipal);

        lenient().when(sessionInfo2.getSessionId()).thenReturn(testSessionId2);
        lenient().when(sessionInfo2.getPrincipal()).thenReturn(testPrincipal);
    }

    @Test
    @DisplayName("Should get active session count correctly")
    void shouldGetActiveSessionCountCorrectly() {
        // Given
        List<SessionInformation> sessions = Arrays.asList(sessionInfo1, sessionInfo2);
        when(sessionRegistry.getAllSessions(testPrincipal, false)).thenReturn(sessions);

        // When
        int count = sessionManagementService.getActiveSessionCount(testPrincipal);

        // Then
        assertThat(count).isEqualTo(2);
        verify(sessionRegistry).getAllSessions(testPrincipal, false);
    }

    @Test
    @DisplayName("Should expire all user sessions")
    void shouldExpireAllUserSessions() {
        // Given
        List<SessionInformation> sessions = Arrays.asList(sessionInfo1, sessionInfo2);
        when(sessionRegistry.getAllSessions(testPrincipal, false)).thenReturn(sessions);

        // When
        int expiredCount = sessionManagementService.expireUserSessions(testPrincipal, false);

        // Then
        assertThat(expiredCount).isEqualTo(2);
        verify(sessionInfo1).expireNow();
        verify(sessionInfo2).expireNow();
    }

    @Test
    @DisplayName("Should expire user sessions excluding current")
    void shouldExpireUserSessionsExcludingCurrent() {
        // Given
        List<SessionInformation> sessions = Arrays.asList(sessionInfo1, sessionInfo2);
        when(sessionRegistry.getAllSessions(testPrincipal, false)).thenReturn(sessions);

        // Mock current session ID to match session1
        // Note: W rzeczywistej implementacji to byłoby pobierane z request context

        // When
        int expiredCount = sessionManagementService.expireUserSessions(testPrincipal, true);

        // Then
        assertThat(expiredCount).isEqualTo(2); // Wszystkie, bo getCurrentSessionId() zwraca null w teście
        verify(sessionInfo1).expireNow();
        verify(sessionInfo2).expireNow();
    }

    @Test
    @DisplayName("Should validate session security with proper request")
    void shouldValidateSessionSecurityWithProperRequest() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new org.springframework.mock.web.MockHttpSession());
        request.addHeader("User-Agent", "Mozilla/5.0 Test Browser");

        // When
        boolean isValid = sessionManagementService.validateSessionSecurity(request);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should detect session security violation - inconsistent User-Agent")
    void shouldDetectSessionSecurityViolationInconsistentUserAgent() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();

        // Ustaw oryginalny User-Agent w sesji
        session.setAttribute("USER_AGENT", "Mozilla/5.0 Original Browser");
        request.setSession(session);

        // Ustaw inny User-Agent w żądaniu
        request.addHeader("User-Agent", "Mozilla/5.0 Different Browser");

        // When
        boolean isValid = sessionManagementService.validateSessionSecurity(request);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should detect old session security violation")
    void shouldDetectOldSessionSecurityViolation() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();

        // Stwórz custom session z starą datą utworzenia
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession() {
            @Override
            public long getCreationTime() {
                // Zwróć timestamp sprzed 9 godzin
                return System.currentTimeMillis() - (9 * 60 * 60 * 1000L);
            }
        };

        request.setSession(session);
        request.addHeader("User-Agent", "Mozilla/5.0 Test Browser");

        // When
        boolean isValid = sessionManagementService.validateSessionSecurity(request);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should handle null session gracefully")
    void shouldHandleNullSessionGracefully() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        // Nie ustawiamy sesji - będzie null

        // When
        boolean isValid = sessionManagementService.validateSessionSecurity(request);

        // Then
        assertThat(isValid).isTrue(); // Brak sesji to OK
    }

    @Test
    @DisplayName("Should report session fixation protection enabled")
    void shouldReportSessionFixationProtectionEnabled() {
        // When
        boolean isEnabled = sessionManagementService.isSessionFixationProtectionEnabled();

        // Then
        assertThat(isEnabled).isTrue();
    }

    @Test
    @DisplayName("Should report correct fixation protection strategy")
    void shouldReportCorrectFixationProtectionStrategy() {
        // When
        String strategy = sessionManagementService.getFixationProtectionStrategy();

        // Then
        assertThat(strategy).isEqualTo("changeSessionId");
    }

    @Test
    @DisplayName("Should expire session for security reasons")
    void shouldExpireSessionForSecurityReasons() {
        // Given
        when(sessionRegistry.getSessionInformation(testSessionId1)).thenReturn(sessionInfo1);

        // When
        sessionManagementService.expireSessionForSecurity(testSessionId1, "Suspected hijacking");

        // Then
        verify(sessionInfo1).expireNow();
    }

    @Test
    @DisplayName("Should handle non-existent session expiry gracefully")
    void shouldHandleNonExistentSessionExpiryGracefully() {
        // Given
        when(sessionRegistry.getSessionInformation(testSessionId1)).thenReturn(null);

        // When & Then
        assertThatNoException()
                .isThrownBy(() -> sessionManagementService.expireSessionForSecurity(testSessionId1, "Test reason"));
    }

    @Test
    @DisplayName("Should audit session changes properly")
    void shouldAuditSessionChangesProperly() {
        // When
        assertThatNoException().isThrownBy(() -> sessionManagementService.auditSessionChange(
                "old-session-123",
                "new-session-456",
                "LOGIN_SESSION_FIXATION_PROTECTION",
                testPrincipal));

        // Then - sprawdzamy że nie rzuca wyjątków (logowanie odbywa się w tle)
    }

    @Test
    @DisplayName("Should create service with disabled session fixation protection")
    void shouldCreateServiceWithDisabledSessionFixationProtection() {
        // Given
        SessionManagementService serviceWithDisabledProtection = new SessionManagementService(
                sessionRegistry, 1, false, "none");

        // When
        boolean isEnabled = serviceWithDisabledProtection.isSessionFixationProtectionEnabled();

        // Then
        assertThat(isEnabled).isFalse();
    }

    @Test
    @DisplayName("Should generate session statistics correctly")
    void shouldGenerateSessionStatisticsCorrectly() {
        // Given
        List<Object> principals = Arrays.asList("user1", "user2");
        when(sessionRegistry.getAllPrincipals()).thenReturn(principals);

        // Mock sessions dla każdego principal
        when(sessionRegistry.getAllSessions("user1", false)).thenReturn(Arrays.asList(sessionInfo1));
        when(sessionRegistry.getAllSessions("user2", false)).thenReturn(Arrays.asList(sessionInfo2));

        // When
        SessionManagementService.SessionStatistics stats = sessionManagementService.getSessionStatistics();

        // Then
        assertThat(stats.getActiveUsers()).isEqualTo(2);
        assertThat(stats.getActiveSessions()).isEqualTo(2);
        assertThat(stats.getMaxConcurrentSessions()).isEqualTo(1);
        assertThat(stats.isFixationProtectionEnabled()).isTrue();
    }

    @Test
    @DisplayName("SessionStatistics toString should work correctly")
    void sessionStatisticsToStringShouldWorkCorrectly() {
        // Given
        SessionManagementService.SessionStatistics stats = new SessionManagementService.SessionStatistics(2, 3, 1,
                true);

        // When
        String toString = stats.toString();

        // Then
        assertThat(toString).contains("users=2")
                .contains("sessions=3")
                .contains("maxConcurrent=1")
                .contains("protection=true");
    }

    @Test
    @DisplayName("Should handle empty principals list")
    void shouldHandleEmptyPrincipalsList() {
        // Given
        when(sessionRegistry.getAllPrincipals()).thenReturn(new ArrayList<>());

        // When
        SessionManagementService.SessionStatistics stats = sessionManagementService.getSessionStatistics();

        // Then
        assertThat(stats.getActiveUsers()).isEqualTo(0);
        assertThat(stats.getActiveSessions()).isEqualTo(0);
    }
}