package com.mac.bry.validationsystem.security.filter;

import com.mac.bry.validationsystem.security.service.SessionManagementService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Filtr sprawdzający bezpieczeństwo sesji przy każdym żądaniu HTTP
 *
 * GMP COMPLIANCE FEATURES:
 * - Walidacja integralności sesji zgodnie z FDA 21 CFR Part 11 §11.10(d)
 * - Wykrywanie session hijacking attacks
 * - Audit trail dla podejrzanych aktywności
 * - Ochrona przed session fixation attacks
 *
 * Wykonuje następujące sprawdzenia:
 * 1. Waliduje wiek sesji (timeout handling)
 * 2. Sprawdza User-Agent consistency (podstawowa ochrona anti-hijacking)
 * 3. Monitoruje anomalie w sesji
 * 4. Wymusza logout przy wykryciu zagrożeń
 */
@Slf4j
@RequiredArgsConstructor
public class SessionSecurityFilter extends OncePerRequestFilter {

    private final SessionManagementService sessionManagementService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Wykonaj sprawdzenia bezpieczeństwa sesji
        boolean securityChecksPassed = false;
        try {
            securityChecksPassed = performSessionSecurityChecks(request, response);
        } catch (Exception e) {
            log.error("🚨 session-security-failure: Krytyczny błąd podczas sprawdzania bezpieczeństwa sesji: {} | Path: {}", 
                    e.getMessage(), request.getRequestURI());
            // W razie błędu w samej logice filtra, dla bezpieczeństwa wylogowujemy (fail-safe)
            handleInsecureSession(request, response);
            return;
        }

        if (!securityChecksPassed) {
            log.warn("🚨 session-security-blocked: Próba dostępu z niebezpiecznej sesji zablokowana: {}", request.getRequestURI());
            handleInsecureSession(request, response);
            return;
        }

        // 2. Kontynuuj do następnego elementu w łańcuchu
        filterChain.doFilter(request, response);
    }

    /**
     * Wykonuje kompleksowe sprawdzenia bezpieczeństwa sesji
     *
     * @param request żądanie HTTP
     * @param response odpowiedź HTTP
     * @return true jeśli sesja jest bezpieczna, false w przeciwnym razie
     */
    private boolean performSessionSecurityChecks(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Sprawdź czy użytkownik jest uwierzytelniony
        if (auth == null || !auth.isAuthenticated()) {
            log.debug("🔍 SESSION CHECK: Brak uwierzytelnienia - pomijam sprawdzenia sesji");
            return true; // Pozwól Spring Security obsłużyć brak uwierzytelnienia
        }

        // Sprawdź podstawowe bezpieczeństwo sesji
        if (!sessionManagementService.validateSessionSecurity(request)) {
            log.warn("⚠️ GMP SECURITY WARNING: Sesja nie przeszła sprawdzenia bezpieczeństwa dla użytkownika: {}",
                    auth.getName());
            return false;
        }

        // Sprawdź liczbę aktywnych sesji (concurrent session control)
        int activeSessions = sessionManagementService.getActiveSessionCount(auth.getPrincipal());
        if (activeSessions > 1) {
            log.warn("⚠️ GMP SESSION WARNING: Użytkownik {} ma {} aktywnych sesji (dozwolona: 1)",
                    auth.getName(), activeSessions);
            // Można skonfigurować czy to ma blokować czy tylko ostrzegać
        }

        // Dodatkowe sprawdzenia specyficzne dla GMP
        return performGMPSpecificChecks(request, auth);
    }

    /**
     * Wykonuje sprawdzenia specyficzne dla compliance GMP/GDP
     */
    private boolean performGMPSpecificChecks(HttpServletRequest request, Authentication auth) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return true;
        }

        // Sprawdź czy sesja nie ma atrybutów wskazujących na naruszenie bezpieczeństwa
        Object securityFlag = session.getAttribute("SECURITY_VIOLATION_FLAG");
        if (securityFlag != null) {
            log.error("🚨 GMP SECURITY ALERT: Wykryto flagę naruszenia bezpieczeństwa w sesji {}",
                    session.getId());
            return false;
        }

        // Sprawdź czy IP address się nie zmienił (podstawowa ochrona przed hijacking)
        String currentIP = extractClientIP(request);
        String sessionIP = (String) session.getAttribute("ORIGINAL_IP");

        log.debug("🔍 SESSION IP CHECK: SessionID: {} | CurrentIP: {} | StoredIP: {}", 
                session.getId(), currentIP, sessionIP);

        if (sessionIP != null && !isSameIP(sessionIP, currentIP)) {
            log.error("🚨 GMP SECURITY ALERT: Zmiana IP address w sesji! Użytkownik: {}, Oryginalne IP: {}, Aktualne IP: {}. Wymuszam logout.",
                    auth.getName(), sessionIP, currentIP);

            // Oznacz sesję jako potencjalnie skompromitowaną
            session.setAttribute("SECURITY_VIOLATION_FLAG", "IP_CHANGE");
            return false;
        }

        // Pierwszy raz - zapisz IP
        if (sessionIP == null) {
            log.debug("🔍 SESSION IP INIT: Zapisuję pierwsze IP dla sesji {}: {}", session.getId(), currentIP);
            session.setAttribute("ORIGINAL_IP", currentIP);
        }

        return true;
    }

    /**
     * Sprawdza czy dwa adresy IP są tożsame (uwzględniając localhost IPv4/v6)
     */
    private boolean isSameIP(String ip1, String ip2) {
        if (ip1.equals(ip2)) {
            return true;
        }
        
        // Specjalne traktowanie localhost (127.0.0.1 == ::1 == 0:0:0:0:0:0:0:1)
        Set<String> localhostIPs = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");
        boolean bothLocal = localhostIPs.contains(ip1) && localhostIPs.contains(ip2);
        
        if (!ip1.equals(ip2)) {
            log.debug("🔍 IP COMPARISON: ip1='{}', ip2='{}', same={}, bothLocal={}", 
                    ip1, ip2, ip1.equals(ip2), bothLocal);
        }
                
        return bothLocal;
    }

    /**
     * Obsługuje niebezpieczną sesję - wymusza logout i audit trail
     */
    private void handleInsecureSession(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        String sessionId = session != null ? session.getId() : "UNKNOWN";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "UNKNOWN";

        log.error("🚨 GMP SECURITY ACTION: Wymuszanie logout z powodu niebezpiecznej sesji | User: {} | SessionID: {}",
                username, sessionId);

        // Invalidate session
        if (session != null) {
            session.setAttribute("SECURITY_EXPIRY_REASON", "SECURITY_VIOLATION_DETECTED");
            session.invalidate();
        }

        // Clear security context
        SecurityContextHolder.clearContext();

        // Audit trail
        sessionManagementService.auditSessionChange(sessionId, null, "SECURITY_VIOLATION_FORCED_LOGOUT", username);

        // Redirect to login with security message
        response.sendRedirect("/login?security_logout=true");
    }

    /**
     * Określa czy filtr powinien być pominięty dla danego żądania
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/login") ||
               path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.startsWith("/fa/") ||
               path.startsWith("/fonts/") ||
               path.startsWith("/webjars/") ||
               path.startsWith("/error") ||
               path.startsWith("/actuator/health");
    }

    /**
     * Wyciąga rzeczywiste IP klienta uwzględniając proxy/load balancer
     */
    private String extractClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String xRealIP = request.getHeader("X-Real-IP");

        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Bierz pierwsze IP z listy (rzeczywiste IP klienta)
            return xForwardedFor.split(",")[0].trim();
        }

        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }

        return request.getRemoteAddr();
    }
}