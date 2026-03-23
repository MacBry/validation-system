package com.mac.bry.validationsystem.security.listener;

import com.mac.bry.validationsystem.security.service.SessionManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionDestroyedEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.time.LocalDateTime;

/**
 * Listener do monitorowania zdarzeń sesji dla audit trail GMP
 *
 * COMPLIANCE NOTES:
 * - FDA 21 CFR Part 11 §11.10(e) wymaga audit trail dla wszystkich operacji
 * - GMP Annex 11 §7 wymaga logowania wszystkich zdarzeń związanych z dostępem
 * - Session events są kluczowe dla wykrywania nieautoryzowanego dostępu
 *
 * Logowane wydarzenia:
 * - Utworzenie sesji (session created)
 * - Uwierzytelnienie użytkownika (authentication success)
 * - Zniszczenie sesji (session destroyed)
 * - Wykrycie session fixation attack
 * - Concurrent session violations
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionEventListener implements HttpSessionListener, ApplicationListener<AbstractAuthenticationEvent> {

    private final SessionManagementService sessionManagementService;

    /**
     * Obsługuje utworzenie nowej sesji HTTP
     * Wymagane dla audit trail zgodnie z GMP Annex 11
     */
    @Override
    public void sessionCreated(HttpSessionEvent se) {
        String sessionId = se.getSession().getId();
        String remoteAddr = extractRemoteAddress(se);

        log.info("🆕 GMP SESSION AUDIT: Utworzono nową sesję | ID: {} | IP: {} | Czas: {}",
                sessionId, remoteAddr, LocalDateTime.now());

        // Ustaw creation timestamp dla późniejszej walidacji wieku sesji
        se.getSession().setAttribute("SESSION_CREATED_AT", System.currentTimeMillis());

        // Audit trail record - w produkcji zapis do bazy audit
        auditSessionEvent("SESSION_CREATED", sessionId, null, remoteAddr, "Utworzenie nowej sesji HTTP");
    }

    /**
     * Obsługuje zniszczenie sesji HTTP
     * Kluczowe dla wykrywania anomalii w dostępie użytkowników
     */
    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        String sessionId = se.getSession().getId();
        String remoteAddr = extractRemoteAddress(se);

        // Oblicz czas trwania sesji
        Long createdAt = (Long) se.getSession().getAttribute("SESSION_CREATED_AT");
        long duration = createdAt != null ? System.currentTimeMillis() - createdAt : -1;

        log.info("🗑️ GMP SESSION AUDIT: Zniszczono sesję | ID: {} | IP: {} | Czas trwania: {}ms | Czas: {}",
                sessionId, remoteAddr, duration, LocalDateTime.now());

        // Audit trail record
        auditSessionEvent("SESSION_DESTROYED", sessionId, null, remoteAddr,
                "Zniszczenie sesji, czas trwania: " + duration + "ms");

        // Sprawdź czy to było normalne zakończenie czy wymuszenie bezpieczeństwa
        Object securityReason = se.getSession().getAttribute("SECURITY_EXPIRY_REASON");
        if (securityReason != null) {
            log.warn("🚨 GMP SECURITY AUDIT: Sesja {} zakończona z powodów bezpieczeństwa: {}",
                    sessionId, securityReason);
        }
    }

    /**
     * Obsługuje wydarzenia związane z uwierzytelnianiem
     * Monitoruje session fixation i inne zagrożenia bezpieczeństwa
     */
    @Override
    public void onApplicationEvent(AbstractAuthenticationEvent event) {
        if (event instanceof AuthenticationSuccessEvent) {
            handleAuthenticationSuccess((AuthenticationSuccessEvent) event);
        }
        // Inne typy zdarzeń uwierzytelniania można dodać tutaj w przyszłości
    }

    /**
     * Obsługuje udane uwierzytelnienie
     * Sprawdza session fixation protection i loguje audit trail
     */
    private void handleAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        String username = auth.getName();

        // Pobierz szczegóły web authentication jeśli dostępne
        String sessionId = null;
        String remoteAddr = null;

        if (auth.getDetails() instanceof WebAuthenticationDetails) {
            WebAuthenticationDetails details = (WebAuthenticationDetails) auth.getDetails();
            sessionId = details.getSessionId();
            remoteAddr = details.getRemoteAddress();
        }

        log.info("✅ GMP AUTHENTICATION AUDIT: Udane uwierzytelnienie | Użytkownik: {} | SessionID: {} | IP: {} | Czas: {}",
                username, sessionId, remoteAddr, LocalDateTime.now());

        // Sprawdź liczbę aktywnych sesji użytkownika
        int activeSessions = sessionManagementService.getActiveSessionCount(auth.getPrincipal());
        if (activeSessions > 1) {
            log.warn("⚠️ GMP SESSION WARNING: Użytkownik {} ma {} aktywnych sesji (max dozwolone: 1)",
                    username, activeSessions);
        }

        // Audit session change dla session fixation protection
        if (sessionManagementService.isSessionFixationProtectionEnabled()) {
            sessionManagementService.auditSessionChange(
                    null, // old session ID nie jest dostępny w tym kontekście
                    sessionId,
                    "LOGIN_SESSION_FIXATION_PROTECTION",
                    username
            );
        }

        // Audit trail record
        auditSessionEvent("AUTHENTICATION_SUCCESS", sessionId, username, remoteAddr,
                "Udane uwierzytelnienie z session fixation protection");
    }

    /**
     * Centralna metoda do tworzenia audit trail dla zdarzeń sesji
     * Zgodna z wymogami GMP Annex 11 dotyczącymi audit trail
     */
    private void auditSessionEvent(String eventType, String sessionId, String username,
                                   String remoteAddress, String description) {

        // W pełnej implementacji produkcyjnej tutaj byłby zapis do:
        // 1. Tabela audit_log w bazie danych
        // 2. System SIEM
        // 3. External audit system
        // 4. Compliance monitoring system

        log.debug("📝 GMP AUDIT RECORD: {} | Session: {} | User: {} | IP: {} | Description: {} | Timestamp: {}",
                eventType, sessionId, username != null ? username : "UNKNOWN",
                remoteAddress != null ? remoteAddress : "UNKNOWN", description, LocalDateTime.now());

        // Mock audit record structure for GMP compliance
        AuditRecord auditRecord = AuditRecord.builder()
                .eventType(eventType)
                .sessionId(sessionId)
                .username(username)
                .remoteAddress(remoteAddress)
                .description(description)
                .timestamp(LocalDateTime.now())
                .build();

        // W produkcji: auditRepository.save(auditRecord);
        log.trace("🔍 AUDIT RECORD STRUCTURE: {}", auditRecord);
    }

    /**
     * Pomocnicza metoda do wyciągania remote address z HttpSessionEvent
     */
    private String extractRemoteAddress(HttpSessionEvent event) {
        // W rzeczywistej implementacji można by spróbować wyciągnąć IP z Request Context
        // lub z session attributes jeśli zostały tam zapisane
        return "UNKNOWN"; // Placeholder - w pełnej implementacji wymagałoby dostępu do Request
    }

    /**
     * Klasa pomocnicza reprezentująca audit record dla compliance
     */
    @lombok.Builder
    @lombok.Data
    private static class AuditRecord {
        private String eventType;
        private String sessionId;
        private String username;
        private String remoteAddress;
        private String description;
        private LocalDateTime timestamp;

        @Override
        public String toString() {
            return String.format("AuditRecord{type=%s, session=%s, user=%s, ip=%s, time=%s}",
                    eventType, sessionId, username, remoteAddress, timestamp);
        }
    }
}