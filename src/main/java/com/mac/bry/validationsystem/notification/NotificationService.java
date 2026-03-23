package com.mac.bry.validationsystem.notification;

import com.mac.bry.validationsystem.calibration.Calibration;
import com.mac.bry.validationsystem.calibration.CalibrationRepository;
import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.device.CoolingDeviceService;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import com.mac.bry.validationsystem.security.service.SecurityService;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationService;
import com.mac.bry.validationsystem.validation.ValidationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    /** Próg dni do ponownej walidacji — ostrzeżenie 30 dni wcześniej */
    private static final int VALIDATION_WARN_DAYS = 365;
    private static final int VALIDATION_DANGER_DAYS = 7;

    /** Próg dni do wygaśnięcia wzorcowania */
    private static final int CALIBRATION_WARN_DAYS = 60;
    private static final int CALIBRATION_DANGER_DAYS = 14;

    /** Próg dni do wygaśnięcia hasła własnego */
    private static final int PASSWORD_WARN_DAYS = 14;
    private static final int PASSWORD_DANGER_DAYS = 3;

    /** Próg dni do wygaśnięcia haseł użytkowników (widok admina) */
    private static final int ADMIN_PASSWORD_WARN_DAYS = 30;

    private final SecurityService securityService;
    private final CoolingDeviceService coolingDeviceService;
    private final ValidationService validationService;
    private final CalibrationRepository calibrationRepository;
    private final UserRepository userRepository;

    /**
     * Zwraca wszystkie aktywne powiadomienia dla aktualnie zalogowanego użytkownika.
     */
    public List<NotificationDTO> getAllNotifications() {
        List<NotificationDTO> all = new ArrayList<>();

        try { all.addAll(getValidationWarnings()); } catch (Exception e) {
            log.warn("Błąd pobierania powiadomień walidacyjnych: {}", e.getMessage());
        }
        try { all.addAll(getCalibrationWarnings()); } catch (Exception e) {
            log.warn("Błąd pobierania powiadomień wzorcowań: {}", e.getMessage());
        }
        try { all.addAll(getPasswordWarnings()); } catch (Exception e) {
            log.warn("Błąd pobierania powiadomień hasła: {}", e.getMessage());
        }
        try { all.addAll(getAdminPasswordWarnings()); } catch (Exception e) {
            log.warn("Błąd pobierania powiadomień haseł admina: {}", e.getMessage());
        }

        // Sortuj: najważniejsze (najmniej dni) na górze
        all.sort(Comparator.comparingInt(NotificationDTO::daysLeft));

        return all;
    }

    // =========================================================================
    // 1. Walidacje urządzeń
    // =========================================================================

    private List<NotificationDTO> getValidationWarnings() {
        List<NotificationDTO> result = new ArrayList<>();
        List<CoolingDevice> devices = coolingDeviceService.getAllAccessibleDevices();
        List<Validation> allValidations = validationService.getAllAccessibleValidations();

        for (CoolingDevice device : devices) {
            // Znajdź najnowszą COMPLETED/APPROVED walidację dla urządzenia
            Optional<Validation> latestOpt = allValidations.stream()
                    .filter(v -> v.getCoolingDevice() != null
                            && v.getCoolingDevice().getId().equals(device.getId())
                            && (v.getStatus() == ValidationStatus.COMPLETED
                                || v.getStatus() == ValidationStatus.APPROVED))
                    .max(Comparator.comparing(Validation::getCreatedDate));

            if (latestOpt.isEmpty()) continue;

            Validation latest = latestOpt.get();
            LocalDateTime nextDue = latest.getCreatedDate().plusDays(VALIDATION_WARN_DAYS);
            long daysLeft = ChronoUnit.DAYS.between(LocalDateTime.now(), nextDue);

            if (daysLeft <= 30) {
                int daysInt = (int) Math.max(daysLeft, 0);
                result.add(new NotificationDTO(
                        "VALIDATION",
                        "Zbliża się termin walidacji",
                        String.format("Urządzenie \"%s\" (%s) — za %d dni",
                                device.getName(), device.getInventoryNumber(), daysInt),
                        "/devices/" + device.getId(),
                        daysInt,
                        NotificationDTO.severityFor(daysInt, VALIDATION_DANGER_DAYS)
                ));
            }
        }
        return result;
    }

    // =========================================================================
    // 2. Wzorcowania rejestratorów
    // =========================================================================

    private List<NotificationDTO> getCalibrationWarnings() {
        List<NotificationDTO> result = new ArrayList<>();
        LocalDate now = LocalDate.now();
        LocalDate window = now.plusDays(CALIBRATION_WARN_DAYS);

        List<Calibration> expiring = calibrationRepository.findLatestExpiringCalibrations(now, window);

        for (Calibration cal : expiring) {
            long daysLeft = ChronoUnit.DAYS.between(now, cal.getValidUntil());
            int daysInt = (int) Math.max(daysLeft, 0);
            String recorderSn = cal.getThermoRecorder() != null
                    ? cal.getThermoRecorder().getSerialNumber() : "?";
            Long recorderId = cal.getThermoRecorder() != null
                    ? cal.getThermoRecorder().getId() : null;

            result.add(new NotificationDTO(
                    "CALIBRATION",
                    "Wygasa wzorcowanie rejestratora",
                    String.format("Rejestrator S/N: %s — za %d dni", recorderSn, daysInt),
                    recorderId != null ? "/recorders/" + recorderId : "/recorders",
                    daysInt,
                    NotificationDTO.severityFor(daysInt, CALIBRATION_DANGER_DAYS)
            ));
        }
        return result;
    }

    // =========================================================================
    // 3. Hasło własnego konta
    // =========================================================================

    private List<NotificationDTO> getPasswordWarnings() {
        User current = securityService.getCurrentUser();
        if (current == null || current.getPasswordExpiresAt() == null) return List.of();

        if (current.isPasswordExpiringInDays(PASSWORD_WARN_DAYS)) {
            Long daysLeft = current.getDaysUntilPasswordExpiry();
            int daysInt = daysLeft != null ? (int) Math.max(daysLeft, 0) : 0;
            return List.of(new NotificationDTO(
                    "PASSWORD",
                    "Zbliża się wygaśnięcie hasła",
                    String.format("Twoje hasło wygasa za %d dni — zmień je teraz", daysInt),
                    "/profile/change-password",
                    daysInt,
                    NotificationDTO.severityFor(daysInt, PASSWORD_DANGER_DAYS)
            ));
        }
        return List.of();
    }

    // =========================================================================
    // 4. Hasła użytkowników (dla Admin / Company Admin)
    // =========================================================================

    private List<NotificationDTO> getAdminPasswordWarnings() {
        if (!securityService.isCompanyAdmin() && !securityService.isSuperAdmin()) {
            return List.of();
        }

        List<NotificationDTO> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime window = now.plusDays(ADMIN_PASSWORD_WARN_DAYS);

        List<User> expiringUsers = userRepository.findByPasswordExpiresAtBetween(now, window);

        for (User user : expiringUsers) {
            // Nie powiadamiaj o sobie (to obsługuje getPasswordWarnings)
            User current = securityService.getCurrentUser();
            if (current != null && current.getId().equals(user.getId())) continue;

            // Sprawdź dostęp do użytkownika
            if (!securityService.hasAccessToUser(user.getId())) continue;

            Long daysLeft = user.getDaysUntilPasswordExpiry();
            int daysInt = daysLeft != null ? (int) Math.max(daysLeft, 0) : 0;

            result.add(new NotificationDTO(
                    "ADMIN_PASSWORD",
                    "Wygasa hasło użytkownika",
                    String.format("Użytkownik \"%s\" — za %d dni", user.getFullName(), daysInt),
                    "/admin/users/" + user.getId(),
                    daysInt,
                    NotificationDTO.severityFor(daysInt, 7)
            ));
        }
        return result;
    }
}
