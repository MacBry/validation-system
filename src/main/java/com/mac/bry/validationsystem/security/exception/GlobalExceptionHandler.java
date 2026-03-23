package com.mac.bry.validationsystem.security.exception;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgumentException(IllegalArgumentException ex, Model model) {
        logger.error("Błąd argumentu: {}", ex.getMessage());
        model.addAttribute("status", 400);
        model.addAttribute("error", "Nieprawidłowe żądanie");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleIllegalStateException(IllegalStateException ex, Model model) {
        logger.error("Błąd stanu aplikacji: {}", ex.getMessage());
        model.addAttribute("status", 409);
        model.addAttribute("error", "Konflikt stanu");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleEntityNotFoundException(EntityNotFoundException ex, Model model) {
        logger.warn("Nie znaleziono encji: {}", ex.getMessage());
        model.addAttribute("status", 404);
        model.addAttribute("error", "Nie znaleziono");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDeniedException(AccessDeniedException ex, Model model) {
        logger.warn("Brak uprawnień: {}", ex.getMessage());
        model.addAttribute("status", 403);
        model.addAttribute("error", "Brak dostępu");
        model.addAttribute("message", "Nie masz uprawnień do wykonania tej operacji.");
        return "error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneralException(Exception ex, Model model) {
        logger.error("Nieoczekiwany błąd systemowy", ex);
        model.addAttribute("status", 500);
        model.addAttribute("error", "Błąd wewnętrzny serwera");
        model.addAttribute("message", "Wystąpił nieoczekiwany błąd. Zespół techniczny został powiadomiony.");
        return "error";
    }
}
