package com.mac.bry.validationsystem.security.controller;

import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.service.AuditService;
import com.mac.bry.validationsystem.security.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kontroler do zarządzania wygaszaniem haseł użytkowników
 * Dostępny tylko dla administratorów
 */
@Slf4j
@Controller
@RequestMapping("/admin/password-expiry")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class PasswordExpiryController {

    private final UserService userService;
    private final AuditService auditService;

    /**
     * Strona główna zarządzania wygaszaniem haseł
     */
    @GetMapping
    public String passwordExpiryManagement(Model model) {
        log.debug("Wyświetlanie strony zarządzania wygaszaniem haseł");

        // Statystyki wygaśnięć
        List<User> expiredUsers = userService.findUsersWithExpiredPasswords();
        List<User> expiringSoon = userService.findUsersWithPasswordsExpiringInDays(7);
        List<User> expiringIn30Days = userService.findUsersWithPasswordsExpiringInDays(30);

        model.addAttribute("expiredUsers", expiredUsers);
        model.addAttribute("expiringSoon", expiringSoon);
        model.addAttribute("expiringIn30Days", expiringIn30Days);

        return "security/admin/password-expiry";
    }

    /**
     * Przedłuż ważność hasła użytkownika
     */
    @PostMapping("/extend/{userId}")
    @ResponseBody
    public String extendPasswordExpiry(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "30") int days,
            RedirectAttributes redirectAttributes) {

        try {
            User user = userService.findById(userId);
            if (user == null) {
                return "Użytkownik nie został znaleziony";
            }

            userService.extendPasswordExpiry(userId, days);

            auditService.logOperation("User", userId, "PASSWORD_EXPIRY_EXTENDED",
                    "Extended by " + days + " days", null);

            log.info("Przedłużono ważność hasła użytkownika {} o {} dni", user.getUsername(), days);
            return "Ważność hasła przedłużona o " + days + " dni";

        } catch (Exception e) {
            log.error("Błąd podczas przedłużania ważności hasła dla użytkownika {}: {}", userId, e.getMessage());
            return "Błąd: " + e.getMessage();
        }
    }

    /**
     * Ustaw politykę wygaszania hasła dla użytkownika
     */
    @PostMapping("/set-policy/{userId}")
    public String setPasswordExpiryPolicy(
            @PathVariable Long userId,
            @RequestParam(required = false) Integer expiryDays,
            RedirectAttributes redirectAttributes) {

        try {
            User user = userService.findById(userId);
            if (user == null) {
                redirectAttributes.addFlashAttribute("error", "Użytkownik nie został znaleziony");
                return "redirect:/admin/password-expiry";
            }

            userService.setPasswordExpiryPolicy(userId, expiryDays);

            auditService.logOperation("User", userId, "PASSWORD_EXPIRY_POLICY_CHANGED",
                    "Set to " + expiryDays + " days", null);

            String message = expiryDays != null && expiryDays > 0
                    ? "Ustawiono politykę wygaszania: " + expiryDays + " dni"
                    : "Wyłączono wygaszanie hasła";

            redirectAttributes.addFlashAttribute("success",
                    message + " dla użytkownika " + user.getUsername());

            log.info("Zmieniono politykę wygaszania hasła dla użytkownika {} na {} dni",
                    user.getUsername(), expiryDays);

        } catch (Exception e) {
            log.error("Błąd podczas ustawiania polityki wygaszania dla użytkownika {}: {}",
                    userId, e.getMessage());
            redirectAttributes.addFlashAttribute("error",
                    "Błąd podczas ustawiania polityki: " + e.getMessage());
        }

        return "redirect:/admin/password-expiry";
    }

    /**
     * Wymuś zmianę hasła dla wszystkich użytkowników z wygasłymi hasłami
     */
    @PostMapping("/force-expired")
    public String forceExpiredPasswordChanges(RedirectAttributes redirectAttributes) {
        try {
            int countBefore = userService.findUsersWithExpiredPasswords().size();
            userService.forcePasswordChangeForExpiredUsers();

            auditService.logOperation("System", null, "FORCE_EXPIRED_PASSWORD_CHANGES",
                    "Affected " + countBefore + " users", null);

            redirectAttributes.addFlashAttribute("success",
                    "Wymuszono zmianę hasła dla " + countBefore + " użytkowników z wygasłymi hasłami");

            log.info("Wymuszono zmianę hasła dla {} użytkowników z wygasłymi hasłami", countBefore);

        } catch (Exception e) {
            log.error("Błąd podczas wymuszania zmiany haseł: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error",
                    "Błąd podczas wymuszania zmian: " + e.getMessage());
        }

        return "redirect:/admin/password-expiry";
    }

    /**
     * API endpoint - statystyki wygaśnięć
     */
    @GetMapping("/api/stats")
    @ResponseBody
    public PasswordExpiryStats getPasswordExpiryStats() {
        List<User> expired = userService.findUsersWithExpiredPasswords();
        List<User> expiring7Days = userService.findUsersWithPasswordsExpiringInDays(7);
        List<User> expiring30Days = userService.findUsersWithPasswordsExpiringInDays(30);

        return new PasswordExpiryStats(
                expired.size(),
                expiring7Days.size(),
                expiring30Days.size());
    }

    /**
     * DTO dla statystyk wygaśnięć
     */
    public static class PasswordExpiryStats {
        public final int expired;
        public final int expiring7Days;
        public final int expiring30Days;

        public PasswordExpiryStats(int expired, int expiring7Days, int expiring30Days) {
            this.expired = expired;
            this.expiring7Days = expiring7Days;
            this.expiring30Days = expiring30Days;
        }
    }
}