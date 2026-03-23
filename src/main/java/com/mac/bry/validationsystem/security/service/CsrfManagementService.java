package com.mac.bry.validationsystem.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Service;

import com.mac.bry.validationsystem.security.CsrfViolationAudit;
import com.mac.bry.validationsystem.security.repository.CsrfViolationAuditRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;

/**
 * Service zarządzający CSRF tokens zgodnie z wymogami GMP/GDP
 *
 * GMP COMPLIANCE NOTES:
 * - FDA 21 CFR Part 11 §11.10(d) wymaga kontroli dostępu z zabezpieczeniami
 * przed manipulacją
 * - CSRF attacks mogą prowadzić do nieautoryzowanych zmian w danych
 * walidacyjnych
 * - Wszystkie CSRF violations muszą być audytowane dla celów regulacyjnych
 *
 * Implementation zgodna z:
 * - OWASP CSRF Prevention Cheat Sheet
 * - Spring Security CSRF Protection
 * - GMP Annex 11 Computer Systems
 * - NIST SP 800-63B Authentication Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsrfManagementService {

    private final CsrfViolationAuditRepository auditRepository;

    /**
     * Waliduje CSRF token z żądania HTTP
     * Loguje wszystkie próby naruszenia bezpieczeństwa dla audit trail
     *
     * @param request       żądanie HTTP
     * @param providedToken token dostarczony przez klienta
     * @param expectedToken oczekiwany token z sesji
     * @return true jeśli token jest prawidłowy
     */
    public boolean validateCsrfToken(HttpServletRequest request, String providedToken, String expectedToken) {
        String sessionId = getSessionId(request);
        String userAgent = request.getHeader("User-Agent");
        String remoteAddr = extractClientIP(request);

        if (providedToken == null || providedToken.trim().isEmpty()) {
            logCsrfViolation("MISSING_TOKEN", request, sessionId, userAgent, remoteAddr,
                    "CSRF token nie został dostarczony w żądaniu");
            return false;
        }

        if (expectedToken == null || expectedToken.trim().isEmpty()) {
            logCsrfViolation("MISSING_SESSION_TOKEN", request, sessionId, userAgent, remoteAddr,
                    "Brak CSRF token w sesji - możliwa manipulacja sesji");
            return false;
        }

        if (!providedToken.equals(expectedToken)) {
            logCsrfViolation("TOKEN_MISMATCH", request, sessionId, userAgent, remoteAddr,
                    String.format("CSRF token niezgodny - expected length: %d, provided length: %d",
                            expectedToken.length(), providedToken.length()));
            return false;
        }

        // Token prawidłowy - loguj sukces dla audit trail
        log.debug("✅ GMP CSRF AUDIT: Token prawidłowy | URL: {} | Session: {} | IP: {} | Czas: {}",
                request.getRequestURL(), sessionId, remoteAddr, LocalDateTime.now());

        return true;
    }

    /**
     * Generuje nowy CSRF token i ustawia go w odpowiedzi
     * Używane przy pierwszym dostępie lub po rotacji token
     */
    public void refreshCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());

        if (csrfToken != null) {
            String sessionId = getSessionId(request);
            String remoteAddr = extractClientIP(request);

            log.info("🔄 GMP CSRF AUDIT: Token odświeżony | Session: {} | IP: {} | Czas: {}",
                    sessionId, remoteAddr, LocalDateTime.now());

            // W Spring Security 6 token jest automatycznie ustawiany w cookie
            // przez CookieCsrfTokenRepository - tu tylko logujemy audit
        }
    }

    /**
     * Pobiera aktualny CSRF token z żądania
     * Bezpieczne API dla kontrolerów i services
     */
    public String getCurrentCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return csrfToken != null ? csrfToken.getToken() : null;
    }

    /**
     * Sprawdza czy żądanie wymaga CSRF protection
     * Metody GET, HEAD, TRACE, OPTIONS są domyślnie wyłączone z CSRF
     */
    public boolean requiresCsrfProtection(HttpServletRequest request) {
        String method = request.getMethod();
        return !("GET".equals(method) || "HEAD".equals(method) ||
                "TRACE".equals(method) || "OPTIONS".equals(method));
    }

    /**
     * Pobiera informacje o stanie CSRF protection dla monitoringu
     */
    public CsrfStatus getCsrfStatus(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String sessionId = getSessionId(request);

        return CsrfStatus.builder()
                .tokenPresent(csrfToken != null)
                .tokenHeaderName(csrfToken != null ? csrfToken.getHeaderName() : null)
                .tokenParameterName(csrfToken != null ? csrfToken.getParameterName() : null)
                .sessionId(sessionId)
                .requiresProtection(requiresCsrfProtection(request))
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Centralne logowanie CSRF violations dla audit trail GMP
     */
    private void logCsrfViolation(String violationType, HttpServletRequest request,
            String sessionId, String userAgent, String remoteAddr, String details) {

        String url = request.getRequestURL().toString();
        String method = request.getMethod();

        log.error(
                "🚨 GMP CSRF VIOLATION: {} | Method: {} | URL: {} | Session: {} | IP: {} | UserAgent: {} | Details: {} | Czas: {}",
                violationType, method, url, sessionId, remoteAddr, userAgent, details, LocalDateTime.now());

        // W pełnej implementacji produkcyjnej tutaj byłby zapis do:
        // 1. Tabela security_audit_log w bazie danych
        // 2. System SIEM/SOC
        // 3. External security monitoring system
        // 4. Alerting system dla security team

        auditCsrfViolation(violationType, method, url, sessionId, remoteAddr, userAgent, details);
    }

    /**
     * Tworzy strukturalny audit record dla CSRF violation
     * Zgodny z wymogami GMP Annex 11 dotyczącymi audit trail
     */
    private void auditCsrfViolation(String violationType, String method, String url,
            String sessionId, String remoteAddr, String userAgent, String details) {

        CsrfViolationAudit audit = CsrfViolationAudit.builder()
                .violationType(violationType)
                .httpMethod(method)
                .requestUrl(url)
                .sessionId(sessionId)
                .remoteAddress(remoteAddr)
                .userAgent(userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 200)) : null)
                .details(details)
                .timestamp(LocalDateTime.now())
                .severity("HIGH") // CSRF violations są zawsze wysokiej wagi
                .complianceRequired(true) // GMP wymaga audytu
                .build();

        auditRepository.save(audit);
        log.trace("🔍 CSRF AUDIT RECORD: {}", audit);
    }

    /**
     * Wyciąga rzeczywiste IP klienta uwzględniając proxy/load balancer
     */
    private String extractClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String xRealIP = request.getHeader("X-Real-IP");

        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }

        return request.getRemoteAddr();
    }

    /**
     * Pobiera session ID bezpiecznie
     */
    private String getSessionId(HttpServletRequest request) {
        return request.getSession(false) != null ? request.getSession(false).getId() : "NO_SESSION";
    }

    /**
     * Klasa reprezentująca status CSRF protection
     */
    @lombok.Builder
    @lombok.Data
    public static class CsrfStatus {
        private boolean tokenPresent;
        private String tokenHeaderName;
        private String tokenParameterName;
        private String sessionId;
        private boolean requiresProtection;
        private LocalDateTime timestamp;

        @Override
        public String toString() {
            return String.format("CsrfStatus{present=%s, header=%s, param=%s, session=%s, required=%s}",
                    tokenPresent, tokenHeaderName, tokenParameterName, sessionId, requiresProtection);
        }
    }
}