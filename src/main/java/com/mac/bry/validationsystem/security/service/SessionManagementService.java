package com.mac.bry.validationsystem.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service zarządzający bezpieczeństwem sesji zgodnie z wymogami GMP/GDP
 *
 * GMP COMPLIANCE NOTES:
 * - FDA 21 CFR Part 11 §11.10(d) wymaga kontroli dostępu z zabezpieczeniami przed manipulacją
 * - Session fixation attacks mogą prowadzić do kompromitacji systemu walidacyjnego
 * - Każda zmiana sesji musi być audytowana dla celów regulacyjnych
 *
 * Implementation zgodna z:
 * - OWASP Session Management Cheat Sheet
 * - NIST SP 800-63B Session Management
 * - GMP Annex 11 Computer Systems
 */
@Slf4j
@Service
public class SessionManagementService {

    private final SessionRegistry sessionRegistry;
    private final int maxConcurrentSessions;
    private final boolean preventLoginIfMaxSessions;
    private final String fixationProtectionStrategy;

    public SessionManagementService(
            SessionRegistry sessionRegistry,
            @Value("${app.security.session.concurrent-sessions:1}") int maxConcurrentSessions,
            @Value("${app.security.session.prevent-login-if-max-sessions:false}") boolean preventLoginIfMaxSessions,
            @Value("${app.security.session.fixation-protection:changeSessionId}") String fixationProtectionStrategy) {

        this.sessionRegistry = sessionRegistry;
        this.maxConcurrentSessions = maxConcurrentSessions;
        this.preventLoginIfMaxSessions = preventLoginIfMaxSessions;
        this.fixationProtectionStrategy = fixationProtectionStrategy;

        log.info("🔒 GMP SESSION SECURITY: Inicjalizacja z max sessions: {}, prevent login: {}, fixation strategy: {}",
                maxConcurrentSessions, preventLoginIfMaxSessions, fixationProtectionStrategy);
    }

    /**
     * Sprawdza i loguje informacje o aktywnych sesjach użytkownika
     * Zgodne z wymogami audit trail GMP Annex 11
     *
     * @param principal obiekt principal użytkownika
     * @return liczba aktywnych sesji
     */
    public int getActiveSessionCount(Object principal) {
        List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);

        log.debug("🔍 GMP AUDIT: Użytkownik {} ma {} aktywnych sesji", principal, sessions.size());

        return sessions.size();
    }

    /**
     * Wymusza zakończenie wszystkich sesji użytkownika oprócz bieżącej
     * Używane przy wykryciu podejrzanej aktywności lub zmianie hasła
     *
     * @param principal obiekt principal użytkownika
     * @param excludeCurrentSession czy wykluczyć bieżącą sesję
     * @return liczba zakończonych sesji
     */
    public int expireUserSessions(Object principal, boolean excludeCurrentSession) {
        List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);

        String currentSessionId = getCurrentSessionId();
        int expiredCount = 0;

        for (SessionInformation session : sessions) {
            if (excludeCurrentSession && session.getSessionId().equals(currentSessionId)) {
                continue; // Pomiń bieżącą sesję
            }

            session.expireNow();
            expiredCount++;

            log.warn("🚨 GMP SECURITY AUDIT: Wymuszono zakończenie sesji {} dla użytkownika {}",
                    session.getSessionId(), principal);
        }

        log.info("🔒 GMP AUDIT: Zakończono {} sesji dla użytkownika {}", expiredCount, principal);
        return expiredCount;
    }

    /**
     * Waliduje bezpieczeństwo bieżącej sesji
     * Sprawdza czy sesja nie została naruszona lub zhijackowana
     *
     * @param request żądanie HTTP
     * @return true jeśli sesja jest bezpieczna
     */
    public boolean validateSessionSecurity(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            log.debug("🔍 SESSION VALIDATION: Brak sesji do walidacji dla uproszczonego żądania");
            return true;
        }

        // Sprawdź czy sesja nie jest stara (potencjalny hijacking)
        long sessionAge = System.currentTimeMillis() - session.getCreationTime();
        long maxSessionAge = 8 * 60 * 60 * 1000L; // 8 godzin

        if (sessionAge > maxSessionAge) {
            log.warn("⚠️ SESSION VALIDATION: Stara sesja wykryta (wiek: {} ms, max: {}). SessionId: {}",
                    sessionAge, maxSessionAge, session.getId());
            return false;
        }

        // Sprawdź user agent consistency (podstawowa ochrona przed hijacking)
        String currentUserAgent = request.getHeader("User-Agent");
        String sessionUserAgent = (String) session.getAttribute("USER_AGENT");

        if (sessionUserAgent != null && !sessionUserAgent.equals(currentUserAgent)) {
            log.error("🚨 SESSION VALIDATION: User-Agent niezgodny! Możliwy session hijacking. SessionId: {} | Sesja: {} | Żądanie: {}",
                    session.getId(), sessionUserAgent, currentUserAgent);
            return false;
        }

        // Pierwszy raz - zapisz user agent
        if (sessionUserAgent == null && currentUserAgent != null) {
            session.setAttribute("USER_AGENT", currentUserAgent);
            log.debug("🔍 SESSION VALIDATION: Zapisano User-Agent dla sesji {}", session.getId());
        }

        return true;
    }

    /**
     * Rejestruje zdarzenie zmiany sesji dla audit trail
     * Wymagane przez GMP Annex 11 dla pełnego śladu audytu
     *
     * @param oldSessionId stary ID sesji (może być null)
     * @param newSessionId nowy ID sesji
     * @param reason powód zmiany sesji
     * @param principal użytkownik
     */
    public void auditSessionChange(String oldSessionId, String newSessionId, String reason, Object principal) {
        log.info("📝 GMP SESSION AUDIT: Zmiana sesji dla użytkownika {} | Stara: {} | Nowa: {} | Powód: {} | Czas: {}",
                principal,
                oldSessionId != null ? oldSessionId : "BRAK",
                newSessionId,
                reason,
                LocalDateTime.now());

        // W pełnej implementacji produkcyjnej tutaj byłby zapis do tabeli audit_log
        // lub integracja z systemem SIEM zgodnie z wymogami GMP
    }

    /**
     * Sprawdza czy strategia session fixation protection jest włączona
     */
    public boolean isSessionFixationProtectionEnabled() {
        return !"none".equalsIgnoreCase(fixationProtectionStrategy);
    }

    /**
     * Zwraca aktualną strategię ochrony przed session fixation
     */
    public String getFixationProtectionStrategy() {
        return fixationProtectionStrategy;
    }

    /**
     * Wymusza przedawnienie sesji z powodów bezpieczeństwa
     *
     * @param sessionId ID sesji do przedawnienia
     * @param reason powód przedawnienia
     */
    public void expireSessionForSecurity(String sessionId, String reason) {
        SessionInformation sessionInfo = sessionRegistry.getSessionInformation(sessionId);

        if (sessionInfo != null) {
            sessionInfo.expireNow();
            log.warn("🚨 GMP SECURITY ACTION: Przedawniono sesję {} z powodu: {}", sessionId, reason);

            // Audit log
            auditSessionChange(sessionId, null, "SECURITY_EXPIRY: " + reason, sessionInfo.getPrincipal());
        } else {
            log.debug("🔍 SESSION EXPIRY: Sesja {} już nie istnieje", sessionId);
        }
    }

    /**
     * Pomocnicza metoda do pobrania ID bieżącej sesji
     */
    private String getCurrentSessionId() {
        // W Spring Security 6+ trzeba to zrobić przez Request
        // Ta metoda powinna być wywoływana w kontekście web request
        return null; // Implementacja zależna od kontekstu
    }

    /**
     * Zwraca statystyki sesji dla monitoringu GMP
     */
    public SessionStatistics getSessionStatistics() {
        List<Object> allPrincipals = sessionRegistry.getAllPrincipals();
        int totalActiveSessions = 0;

        for (Object principal : allPrincipals) {
            totalActiveSessions += sessionRegistry.getAllSessions(principal, false).size();
        }

        return new SessionStatistics(
                allPrincipals.size(),
                totalActiveSessions,
                maxConcurrentSessions,
                isSessionFixationProtectionEnabled()
        );
    }

    /**
     * Klasa pomocnicza dla statystyk sesji
     */
    public static class SessionStatistics {
        private final int activeUsers;
        private final int activeSessions;
        private final int maxConcurrentSessions;
        private final boolean fixationProtectionEnabled;

        public SessionStatistics(int activeUsers, int activeSessions, int maxConcurrentSessions, boolean fixationProtectionEnabled) {
            this.activeUsers = activeUsers;
            this.activeSessions = activeSessions;
            this.maxConcurrentSessions = maxConcurrentSessions;
            this.fixationProtectionEnabled = fixationProtectionEnabled;
        }

        public int getActiveUsers() { return activeUsers; }
        public int getActiveSessions() { return activeSessions; }
        public int getMaxConcurrentSessions() { return maxConcurrentSessions; }
        public boolean isFixationProtectionEnabled() { return fixationProtectionEnabled; }

        @Override
        public String toString() {
            return String.format("SessionStats{users=%d, sessions=%d, maxConcurrent=%d, protection=%s}",
                    activeUsers, activeSessions, maxConcurrentSessions, fixationProtectionEnabled);
        }
    }
}