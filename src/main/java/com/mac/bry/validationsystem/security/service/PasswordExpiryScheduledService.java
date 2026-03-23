package com.mac.bry.validationsystem.security.service;

import com.mac.bry.validationsystem.security.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Serwis do automatycznego zarządzania wygasaniem haseł
 * Uruchamia się codziennie o 6:00 rano
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.security.password-expiry.scheduled.enabled", havingValue = "true", matchIfMissing = true)
public class PasswordExpiryScheduledService {

    private final UserService userService;
    private final AuditService auditService;

    /**
     * Codzienne zadanie sprawdzające wygasłe hasła
     * Uruchamiane o 6:00 rano każdego dnia
     */
    @Scheduled(cron = "0 0 6 * * ?") // 6:00 AM codziennie
    public void processExpiredPasswords() {
        log.info("Rozpoczęcie codziennego sprawdzania wygasłych haseł...");

        try {
            // Znajdź użytkowników z wygasłymi hasłami
            List<User> expiredUsers = userService.findUsersWithExpiredPasswords();

            if (expiredUsers.isEmpty()) {
                log.info("Brak użytkowników z wygasłymi hasłami");
                return;
            }

            log.warn("Znaleziono {} użytkowników z wygasłymi hasłami", expiredUsers.size());

            // Wymuś zmianę hasła dla wszystkich użytkowników z wygasłymi hasłami
            userService.forcePasswordChangeForExpiredUsers();

            // Loguj do audit trail
            auditService.logOperation("System", null, "SCHEDULED_PASSWORD_EXPIRY_CHECK",
                    "Processed " + expiredUsers.size() + " expired passwords", null);

            log.info("Pomyślnie wymuszono zmianę hasła dla {} użytkowników z wygasłymi hasłami", expiredUsers.size());

        } catch (Exception e) {
            log.error("Błąd podczas codziennego sprawdzania wygasłych haseł", e);

            auditService.logOperation("System", null, "SCHEDULED_PASSWORD_EXPIRY_ERROR",
                    "Error: " + e.getMessage(), null);
        }
    }

    /**
     * Cotygodniowe powiadomienie o hasłach wygasających w ciągu 7 dni
     * Uruchamiane w poniedziałek o 8:00 rano
     */
    @Scheduled(cron = "0 0 8 * * MON") // 8:00 AM w poniedziałek
    public void reportPasswordsExpiringSoon() {
        log.info("Rozpoczęcie cotygodniowego raportu haseł wygasających wkrótce...");

        try {
            List<User> expiring7Days = userService.findUsersWithPasswordsExpiringInDays(7);
            List<User> expiring30Days = userService.findUsersWithPasswordsExpiringInDays(30);

            if (expiring7Days.isEmpty() && expiring30Days.isEmpty()) {
                log.info("Brak haseł wygasających w najbliższych 30 dniach");
                return;
            }

            // Loguj statystyki
            if (!expiring7Days.isEmpty()) {
                log.warn("UWAGA: {} haseł wygasa w ciągu 7 dni:", expiring7Days.size());
                for (User user : expiring7Days) {
                    log.warn("- Użytkownik '{}' - hasło wygasa: {} (za {} dni)",
                            user.getUsername(),
                            user.getPasswordExpiresAt(),
                            user.getDaysUntilPasswordExpiry());
                }
            }

            if (!expiring30Days.isEmpty()) {
                log.info("{} haseł wygasa w ciągu 30 dni", expiring30Days.size());
            }

            auditService.logOperation("System", null, "SCHEDULED_PASSWORD_EXPIRY_REPORT",
                    String.format("Expiring in 7 days: %d, expiring in 30 days: %d",
                            expiring7Days.size(), expiring30Days.size()), null);

        } catch (Exception e) {
            log.error("Błąd podczas cotygodniowego raportu wygaśnięć haseł", e);

            auditService.logOperation("System", null, "SCHEDULED_PASSWORD_EXPIRY_REPORT_ERROR",
                    "Error: " + e.getMessage(), null);
        }
    }

    /**
     * Miesięczne zadanie czyszczące - można rozszerzyć w przyszłości
     * Uruchamiane pierwszego dnia miesiąca o 3:00 rano
     */
    @Scheduled(cron = "0 0 3 1 * ?") // 3:00 AM pierwszego dnia miesiąca
    public void monthlyPasswordMaintenancee() {
        log.info("Rozpoczęcie miesięcznego zadania konserwacyjnego haseł...");

        try {
            // Statystyki miesięczne
            List<User> expired = userService.findUsersWithExpiredPasswords();
            List<User> expiringSoon = userService.findUsersWithPasswordsExpiringInDays(30);

            log.info("Miesięczny raport bezpieczeństwa haseł:");
            log.info("- Hasła wygasłe: {}", expired.size());
            log.info("- Hasła wygasające w 30 dni: {}", expiringSoon.size());

            auditService.logOperation("System", null, "MONTHLY_PASSWORD_MAINTENANCE",
                    String.format("Expired: %d, expiring soon: %d", expired.size(), expiringSoon.size()),
                    null);

            log.info("Zakończono miesięczne zadanie konserwacyjne haseł");

        } catch (Exception e) {
            log.error("Błąd podczas miesięcznego zadania konserwacyjnego haseł", e);

            auditService.logOperation("System", null, "MONTHLY_PASSWORD_MAINTENANCE_ERROR",
                    "Error: " + e.getMessage(), null);
        }
    }

    /**
     * Metoda do ręcznego uruchomienia sprawdzenia wygasłych haseł
     * (przydatna do testów lub ręcznego uruchomienia przez administratora)
     */
    public void runPasswordExpiryCheckNow() {
        log.info("Ręczne uruchomienie sprawdzania wygasłych haseł...");
        processExpiredPasswords();
    }

    /**
     * Metoda do ręcznego uruchomienia raportu
     */
    public void runPasswordExpiryReportNow() {
        log.info("Ręczne uruchomienie raportu wygaśnięć haseł...");
        reportPasswordsExpiringSoon();
    }
}