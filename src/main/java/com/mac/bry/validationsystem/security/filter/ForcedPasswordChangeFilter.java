package com.mac.bry.validationsystem.security.filter;

import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.service.SecurityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Filtr sprawdzający czy zalogowany użytkownik musi zmienić hasło
 * (wymuszenie administratora lub wygaśnięcie hasła)
 * i przekierowujący go na stronę wymuszonej zmiany hasła.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForcedPasswordChangeFilter extends OncePerRequestFilter {

    private final SecurityService securityService;

    // URL-e które są dozwolone bez zmiany hasła
    private static final Set<String> ALLOWED_PATHS = Set.of(
        "/profile/forced-change-password",
        "/logout",
        "/css/",
        "/js/",
        "/images/",
        "/static/",
        "/favicon.ico",
        "/fonts/",
        "/.well-known/",
        "/error"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Sprawdź czy użytkownik jest zalogowany i czy to nie jest użytkownik anonimowy
        if (authentication != null && authentication.isAuthenticated() &&
            !"anonymousUser".equals(authentication.getPrincipal())) {

            try {
                User currentUser = securityService.getCurrentUser();

                // Jeśli użytkownik jest zalogowany, sprawdź czy musi zmienić hasło
                if (currentUser != null && currentUser.mustChangePasswordNow()) {
                    String requestURI = request.getRequestURI();

                    // Pomiń sprawdzenie dla dozwolonych ścieżek
                    if (!isAllowedPath(requestURI)) {
                        String username = currentUser.getUsername();
                        String reason = currentUser.isMustChangePassword() ? "wymuszenie" : "wygaśnięcie hasła";
                        
                        log.info("🔒 forced-password-change: Użytkownik {} musi zmienić hasło ({}) - przekierowanie z {} na /profile/forced-change-password",
                                username, reason, requestURI);

                        response.sendRedirect("/profile/forced-change-password");
                        return;
                    }
                }
            } catch (Exception e) {
                // Loguj błąd, ale pozwól na kontynuację, aby nie blokować całego systemu przy błędzie pobierania danych użytkownika
                log.error("⚠️ forced-password-change-error: Błąd podczas sprawdzania statusu zmiany hasła: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Sprawdza czy ścieżka jest dozwolona bez zmiany hasła
     */
    private boolean isAllowedPath(String path) {
        return ALLOWED_PATHS.stream().anyMatch(allowedPath ->
            path.equals(allowedPath) || path.startsWith(allowedPath)
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.startsWith("/fonts/") ||
               path.startsWith("/.well-known/") ||
               path.startsWith("/fa/") ||
               path.startsWith("/webjars/") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/h2-console/");
    }
}