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

import org.springframework.http.ResponseEntity;
import java.util.Map;

import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.NoHandlerFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public Object handleNotFoundException(Exception ex, Model model, jakarta.servlet.http.HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        
        // Cichy log dla szumu przeglądarkowego/rozszerzeń
        if (requestUri.contains(".well-known") || requestUri.contains("favicon")) {
            logger.debug("Zasób nieodnaleziony (szum): {}", requestUri);
        } else {
            logger.warn("Zasób nieodnaleziony: {}", requestUri);
        }

        if (isAjaxRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "message", "Zasób nie istnieje", "error", "Nie znaleziono"));
        }
        
        model.addAttribute("status", 404);
        model.addAttribute("error", "Nie znaleziono");
        model.addAttribute("message", "Strona lub zasób, którego szukasz, nie istnieje.");
        return "error";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgumentException(IllegalArgumentException ex, Model model, jakarta.servlet.http.HttpServletRequest request) {
        logger.error("Błąd argumentu: {}", ex.getMessage());
        if (isAjaxRequest(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "message", ex.getMessage(), "error", "Nieprawidłowe żądanie"));
        }
        model.addAttribute("status", 400);
        model.addAttribute("error", "Nieprawidłowe żądanie");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(IllegalStateException.class)
    public Object handleIllegalStateException(IllegalStateException ex, Model model, jakarta.servlet.http.HttpServletRequest request) {
        logger.error("Błąd stanu aplikacji: {}", ex.getMessage());
        if (isAjaxRequest(request)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("success", false, "message", ex.getMessage(), "error", "Konflikt stanu"));
        }
        model.addAttribute("status", 409);
        model.addAttribute("error", "Konflikt stanu");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public Object handleEntityNotFoundException(EntityNotFoundException ex, Model model, jakarta.servlet.http.HttpServletRequest request) {
        logger.warn("Nie znaleziono encji: {}", ex.getMessage());
        if (isAjaxRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "message", ex.getMessage(), "error", "Nie znaleziono"));
        }
        model.addAttribute("status", 404);
        model.addAttribute("error", "Nie znaleziono");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDeniedException(AccessDeniedException ex, Model model, jakarta.servlet.http.HttpServletRequest request) {
        logger.warn("Brak uprawnień: {}", ex.getMessage());
        if (isAjaxRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "message", "Nie masz uprawnień do wykonania tej operacji.", "error", "Brak dostępu"));
        }
        model.addAttribute("status", 403);
        model.addAttribute("error", "Brak dostępu");
        model.addAttribute("message", "Nie masz uprawnień do wykonania tej operacji.");
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public Object handleGeneralException(Exception ex, Model model, jakarta.servlet.http.HttpServletRequest request) {
        logger.error("Nieoczekiwany błąd systemowy", ex);
        if (isAjaxRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Wystąpił nieoczekiwany błąd serwera. " + ex.getMessage(), "error", "Błąd wewnętrzny"));
        }
        model.addAttribute("status", 500);
        model.addAttribute("error", "Błąd wewnętrzny serwera");
        model.addAttribute("message", "Wystąpił nieoczekiwany błąd. Zespół techniczny został powiadomiony.");
        return "error";
    }

    private boolean isAjaxRequest(jakarta.servlet.http.HttpServletRequest request) {
        String requestedWith = request.getHeader("X-Requested-With");
        return "XMLHttpRequest".equals(requestedWith) || 
               (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json"));
    }
}
