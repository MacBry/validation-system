package com.mac.bry.validationsystem.security.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

/**
 * Controller obsługujący błędy CSRF dla user-friendly error pages
 *
 * GMP COMPLIANCE:
 * Zgodnie z FDA 21 CFR Part 11 §11.10(c) - system musi informować użytkowników
 * o próbach nieautoryzowanego dostępu bez ujawniania szczegółów technicznych
 */
@Slf4j
@Controller
public class CsrfErrorController {

    @GetMapping("/error/csrf")
    public String csrfError(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String returnUrl,
            Model model) {

        // Validate returnUrl to prevent Open Redirect
        String safeReturnUrl = com.mac.bry.validationsystem.security.util.UrlValidator.isSafeInternalUrl(returnUrl)
                ? returnUrl : null;

        // Loguj że użytkownik został przekierowany na error page
        log.info("📄 CSRF Error Page: type={}, returnUrl={}, timestamp={}",
                type, returnUrl, LocalDateTime.now());

        // Przygotuj user-friendly messages na podstawie typu violation
        String userMessage = getUserFriendlyMessage(type);
        String technicalType = type != null ? type : "UNKNOWN";

        model.addAttribute("errorMessage", userMessage);
        model.addAttribute("technicalType", technicalType);
        model.addAttribute("returnUrl", safeReturnUrl);
        model.addAttribute("timestamp", LocalDateTime.now());

        return "error/csrf-error";
    }

    @GetMapping("/error/access-denied")
    public String accessDeniedError(Model model) {
        log.info("📄 Access Denied Error Page: timestamp={}", LocalDateTime.now());

        model.addAttribute("errorMessage", "Nie masz uprawnień do wykonania tej operacji.");
        model.addAttribute("timestamp", LocalDateTime.now());

        return "error/access-denied";
    }

    /**
     * Konwertuje techniczne typy CSRF violations na user-friendly messages
     */
    private String getUserFriendlyMessage(String type) {
        if (type == null) {
            return "Żądanie zostało odrzucone z powodów bezpieczeństwa.";
        }

        return switch (type) {
            case "MISSING_CSRF_TOKEN" ->
                    "Formularz wygasł. Odśwież stronę i wypełnij formularz ponownie.";

            case "INVALID_CSRF_TOKEN" ->
                    "Token bezpieczeństwa jest nieprawidłowy. Odśwież stronę i spróbuj ponownie.";

            case "SESSION_CSRF_VIOLATION" ->
                    "Sesja wygasła. Zaloguj się ponownie i powtórz operację.";

            case "GENERAL_CSRF_VIOLATION" ->
                    "Wykryto próbę nieautoryzowanego dostępu. Operacja została zablokowana.";

            default ->
                    "Żądanie zostało odrzucone z powodów bezpieczeństwa. Skontaktuj się z administratorem jeśli problem się powtarza.";
        };
    }
}