package com.mac.bry.validationsystem.notification;

import com.mac.bry.validationsystem.calibration.Calibration;
import com.mac.bry.validationsystem.calibration.CalibrationRepository;
import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.device.CoolingDeviceService;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import com.mac.bry.validationsystem.security.service.SecurityService;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorder;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationService;
import com.mac.bry.validationsystem.validation.ValidationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testy jednostkowe NotificationService.
 * 
 * Używają Mockito — bez kontekstu Spring — więc są szybkie i izolowane.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService — testy jednostkowe")
class NotificationServiceTest {

    @Mock SecurityService securityService;
    @Mock CoolingDeviceService coolingDeviceService;
    @Mock ValidationService validationService;
    @Mock CalibrationRepository calibrationRepository;
    @Mock UserRepository userRepository;

    @InjectMocks NotificationService notificationService;

    // =========================================================================
    // Helpery do budowania obiektów testowych
    // =========================================================================

    private CoolingDevice device(Long id, String name, String inv) {
        CoolingDevice d = new CoolingDevice();
        d.setId(id);
        d.setName(name);
        d.setInventoryNumber(inv);
        return d;
    }

    private Validation validation(Long devId, ValidationStatus status, LocalDateTime createdAt) {
        CoolingDevice dev = new CoolingDevice();
        dev.setId(devId);

        Validation v = new Validation();
        v.setId(devId * 10);
        v.setCoolingDevice(dev);
        v.setStatus(status);
        v.setCreatedDate(createdAt);
        return v;
    }

    private Calibration calibrationFor(String serialNumber, Long recorderId, LocalDate validUntil) {
        ThermoRecorder rec = new ThermoRecorder();
        rec.setId(recorderId);
        rec.setSerialNumber(serialNumber);

        Calibration cal = new Calibration();
        cal.setId(recorderId * 10);
        cal.setThermoRecorder(rec);
        cal.setCalibrationDate(validUntil.minusYears(1));
        cal.setValidUntil(validUntil);
        cal.setCertificateNumber("CERT-" + recorderId);
        return cal;
    }

    private User userWithPassword(Long id, String username, LocalDateTime passwordExpiresAt) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPassword("hashed");
        u.setPasswordExpiresAt(passwordExpiresAt);
        return u;
    }

    // =========================================================================
    // 1. Powiadomienia o walidacji urządzeń
    // =========================================================================

    @Nested
    @DisplayName("Walidacje urządzeń")
    class ValidationWarnings {

        @Test
        @DisplayName("Brak powiadomień gdy walidacja jest aktualna (> 30 dni do wygaśnięcia)")
        void noWarning_whenValidationIsFresh() {
            CoolingDevice dev = device(1L, "Zamrażarka", "INW-001");
            Validation fresh = validation(1L, ValidationStatus.APPROVED,
                    LocalDateTime.now().minusDays(200)); // 200 dni temu — zostało 165 dni

            when(coolingDeviceService.getAllAccessibleDevices()).thenReturn(List.of(dev));
            when(validationService.getAllAccessibleValidations()).thenReturn(List.of(fresh));
            when(calibrationRepository.findLatestExpiringCalibrations(any(), any())).thenReturn(List.of());
            when(securityService.getCurrentUser()).thenReturn(null);
            when(securityService.isCompanyAdmin()).thenReturn(false);
            when(securityService.isSuperAdmin()).thenReturn(false);

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertTrue(result.stream().noneMatch(n -> n.type().equals("VALIDATION")));
        }

        @Test
        @DisplayName("Powiadomienie WARNING gdy walidacja wygasa za 25 dni")
        void warning_whenValidationExpiresIn25Days() {
            CoolingDevice dev = device(1L, "Lodówka Lab", "INW-002");
            // Ostatnia walidacja była 340 dni temu → zostało 25 dni do rocznicy
            Validation old = validation(1L, ValidationStatus.APPROVED,
                    LocalDateTime.now().minusDays(340));

            when(coolingDeviceService.getAllAccessibleDevices()).thenReturn(List.of(dev));
            when(validationService.getAllAccessibleValidations()).thenReturn(List.of(old));
            when(calibrationRepository.findLatestExpiringCalibrations(any(), any())).thenReturn(List.of());
            when(securityService.getCurrentUser()).thenReturn(null);
            when(securityService.isCompanyAdmin()).thenReturn(false);
            when(securityService.isSuperAdmin()).thenReturn(false);

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertEquals(1, result.size());
            NotificationDTO n = result.get(0);
            assertEquals("VALIDATION", n.type());
            assertEquals("warning", n.severity());
            assertTrue(n.daysLeft() >= 24 && n.daysLeft() <= 26);
            assertTrue(n.link().startsWith("/devices/"));
        }

        @Test
        @DisplayName("Powiadomienie DANGER gdy walidacja wygasa za 5 dni")
        void danger_whenValidationExpiresIn5Days() {
            CoolingDevice dev = device(2L, "Chiller", "INW-003");
            Validation almostExpired = validation(2L, ValidationStatus.COMPLETED,
                    LocalDateTime.now().minusDays(360));

            when(coolingDeviceService.getAllAccessibleDevices()).thenReturn(List.of(dev));
            when(validationService.getAllAccessibleValidations()).thenReturn(List.of(almostExpired));
            when(calibrationRepository.findLatestExpiringCalibrations(any(), any())).thenReturn(List.of());
            when(securityService.getCurrentUser()).thenReturn(null);
            when(securityService.isCompanyAdmin()).thenReturn(false);
            when(securityService.isSuperAdmin()).thenReturn(false);

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertEquals(1, result.size());
            assertEquals("danger", result.get(0).severity());
        }

        @Test
        @DisplayName("Pomijaj walidacje o statusie DRAFT — nie generują powiadomień")
        void noWarning_forDraftValidation() {
            CoolingDevice dev = device(3L, "Mroźnia", "INW-004");
            Validation draft = validation(3L, ValidationStatus.DRAFT,
                    LocalDateTime.now().minusDays(350));

            when(coolingDeviceService.getAllAccessibleDevices()).thenReturn(List.of(dev));
            when(validationService.getAllAccessibleValidations()).thenReturn(List.of(draft));
            when(calibrationRepository.findLatestExpiringCalibrations(any(), any())).thenReturn(List.of());
            when(securityService.getCurrentUser()).thenReturn(null);
            when(securityService.isCompanyAdmin()).thenReturn(false);
            when(securityService.isSuperAdmin()).thenReturn(false);

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertTrue(result.stream().noneMatch(n -> n.type().equals("VALIDATION")));
        }

        @Test
        @DisplayName("Brak powiadomień gdy urządzenie nie ma żadnej walidacji")
        void noWarning_whenDeviceHasNoValidation() {
            CoolingDevice dev = device(4L, "Nowe urządzenie", "INW-005");

            when(coolingDeviceService.getAllAccessibleDevices()).thenReturn(List.of(dev));
            when(validationService.getAllAccessibleValidations()).thenReturn(List.of());
            when(calibrationRepository.findLatestExpiringCalibrations(any(), any())).thenReturn(List.of());
            when(securityService.getCurrentUser()).thenReturn(null);
            when(securityService.isCompanyAdmin()).thenReturn(false);
            when(securityService.isSuperAdmin()).thenReturn(false);

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // 2. Powiadomienia o wzorcowaniach
    // =========================================================================

    @Nested
    @DisplayName("Wzorcowania rejestratorów")
    class CalibrationWarnings {

        private void mockNoDeviceOrPasswordNotif() {
            when(coolingDeviceService.getAllAccessibleDevices()).thenReturn(List.of());
            when(validationService.getAllAccessibleValidations()).thenReturn(List.of());
            when(securityService.getCurrentUser()).thenReturn(null);
            when(securityService.isCompanyAdmin()).thenReturn(false);
            when(securityService.isSuperAdmin()).thenReturn(false);
        }

        @Test
        @DisplayName("Powiadomienie WARNING gdy wzorcowanie wygasa za 30 dni")
        void warning_whenCalibrationExpiresIn30Days() {
            Calibration cal = calibrationFor("SN-12345", 1L,
                    LocalDate.now().plusDays(30));

            when(calibrationRepository.findLatestExpiringCalibrations(any(), any())).thenReturn(List.of(cal));
            mockNoDeviceOrPasswordNotif();

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertEquals(1, result.size());
            NotificationDTO n = result.get(0);
            assertEquals("CALIBRATION", n.type());
            assertEquals("warning", n.severity());
            assertTrue(n.message().contains("SN-12345"));
            assertEquals("/recorders/1", n.link());
        }

        @Test
        @DisplayName("Powiadomienie DANGER gdy wzorcowanie wygasa za 7 dni")
        void danger_whenCalibrationExpiresIn7Days() {
            Calibration cal = calibrationFor("SN-99999", 2L,
                    LocalDate.now().plusDays(7));

            when(calibrationRepository.findLatestExpiringCalibrations(any(), any())).thenReturn(List.of(cal));
            mockNoDeviceOrPasswordNotif();

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertEquals(1, result.size());
            assertEquals("danger", result.get(0).severity());
        }

        @Test
        @DisplayName("Wiele rejestratorów z wygasającymi wzorcowaniami")
        void multipleCalibrationWarnings() {
            Calibration cal1 = calibrationFor("SN-AAAA", 1L, LocalDate.now().plusDays(10));
            Calibration cal2 = calibrationFor("SN-BBBB", 2L, LocalDate.now().plusDays(45));

            when(calibrationRepository.findLatestExpiringCalibrations(any(), any()))
                    .thenReturn(List.of(cal1, cal2));
            mockNoDeviceOrPasswordNotif();

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(n -> n.type().equals("CALIBRATION")));
        }

        @Test
        @DisplayName("Brak powiadomień gdy brak wygasających wzorcowań")
        void noWarning_whenNoExpiringCalibrations() {
            when(calibrationRepository.findLatestExpiringCalibrations(any(), any())).thenReturn(List.of());
            mockNoDeviceOrPasswordNotif();

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // 3. Powiadomienia o własnym haśle
    // =========================================================================

    @Nested
    @DisplayName("Hasło własnego konta")
    class PasswordWarnings {

        private void mockNoOtherNotif() {
            when(coolingDeviceService.getAllAccessibleDevices()).thenReturn(List.of());
            when(validationService.getAllAccessibleValidations()).thenReturn(List.of());
            when(calibrationRepository.findLatestExpiringCalibrations(any(), any())).thenReturn(List.of());
            when(securityService.isCompanyAdmin()).thenReturn(false);
            when(securityService.isSuperAdmin()).thenReturn(false);
        }

        @Test
        @DisplayName("Powiadomienie PASSWORD gdy hasło wygasa za 10 dni")
        void passwordWarning_whenExpiringIn10Days() {
            User currentUser = userWithPassword(1L, "jankowalski",
                    LocalDateTime.now().plusDays(10));

            when(securityService.getCurrentUser()).thenReturn(currentUser);
            mockNoOtherNotif();

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertEquals(1, result.size());
            NotificationDTO n = result.get(0);
            assertEquals("PASSWORD", n.type());
            assertEquals("/profile/change-password", n.link());
            assertEquals("warning", n.severity());
            assertTrue(n.daysLeft() >= 9 && n.daysLeft() <= 11);
        }

        @Test
        @DisplayName("Powiadomienie DANGER gdy hasło wygasa za 2 dni")
        void danger_whenPasswordExpiresIn2Days() {
            User currentUser = userWithPassword(1L, "krytyczny",
                    LocalDateTime.now().plusDays(2));

            when(securityService.getCurrentUser()).thenReturn(currentUser);
            mockNoOtherNotif();

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertEquals(1, result.size());
            assertEquals("danger", result.get(0).severity());
        }

        @Test
        @DisplayName("Brak powiadomień gdy hasło wygasa za 20 dni (poza progiem 14d)")
        void noWarning_whenPasswordExpiresIn20Days() {
            User currentUser = userWithPassword(1L, "bezpieczny",
                    LocalDateTime.now().plusDays(20));

            when(securityService.getCurrentUser()).thenReturn(currentUser);
            mockNoOtherNotif();

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertTrue(result.stream().noneMatch(n -> n.type().equals("PASSWORD")));
        }

        @Test
        @DisplayName("Brak powiadomień gdy brak ustawionego wygaśnięcia hasła")
        void noWarning_whenNoPasswordExpiry() {
            User currentUser = userWithPassword(1L, "bezterminowy", null);

            when(securityService.getCurrentUser()).thenReturn(currentUser);
            mockNoOtherNotif();

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertTrue(result.stream().noneMatch(n -> n.type().equals("PASSWORD")));
        }

        @Test
        @DisplayName("Brak powiadomień gdy brak zalogowanego użytkownika")
        void noWarning_whenNotAuthenticated() {
            when(securityService.getCurrentUser()).thenReturn(null);
            mockNoOtherNotif();

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // 4. Powiadomienia adminstracyjne o hasłach
    // =========================================================================

    @Nested
    @DisplayName("Admin: Hasła użytkowników")
    class AdminPasswordWarnings {

        private void mockNoDeviceOrCalibrationNotif() {
            when(coolingDeviceService.getAllAccessibleDevices()).thenReturn(List.of());
            when(validationService.getAllAccessibleValidations()).thenReturn(List.of());
            when(calibrationRepository.findLatestExpiringCalibrations(any(), any())).thenReturn(List.of());
        }

        @Test
        @DisplayName("Admin widzi powiadomienie o wygasającym haśle innego użytkownika")
        void adminSeesOtherUserPasswordWarning() {
            User admin = userWithPassword(1L, "admin", LocalDateTime.now().plusDays(60));
            User otherUser = userWithPassword(2L, "pracownik", LocalDateTime.now().plusDays(15));

            lenient().when(securityService.getCurrentUser()).thenReturn(admin);
            lenient().when(securityService.isCompanyAdmin()).thenReturn(true);
            lenient().when(securityService.isSuperAdmin()).thenReturn(false);
            when(securityService.hasAccessToUser(2L)).thenReturn(true);
            when(userRepository.findByPasswordExpiresAtBetween(any(), any())).thenReturn(List.of(otherUser));
            mockNoDeviceOrCalibrationNotif();

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertEquals(1, result.size());
            NotificationDTO n = result.get(0);
            assertEquals("ADMIN_PASSWORD", n.type());
            assertTrue(n.message().contains("pracownik"));
            assertEquals("/admin/users/2", n.link());
        }

        @Test
        @DisplayName("Admin nie widzi powiadomienia o własnym haśle w kategorii ADMIN_PASSWORD")
        void adminDoesNotSeeOwnPasswordInAdminWarnings() {
            User admin = userWithPassword(1L, "admin", LocalDateTime.now().plusDays(15));

            lenient().when(securityService.getCurrentUser()).thenReturn(admin);
            lenient().when(securityService.isCompanyAdmin()).thenReturn(true);
            lenient().when(securityService.isSuperAdmin()).thenReturn(false);
            // Repository zwraca samego admina
            when(userRepository.findByPasswordExpiresAtBetween(any(), any())).thenReturn(List.of(admin));
            mockNoDeviceOrCalibrationNotif();

            List<NotificationDTO> result = notificationService.getAllNotifications();

            // Może być powiadomienie PASSWORD dla samego admina, ale nie ADMIN_PASSWORD
            assertTrue(result.stream().noneMatch(n -> n.type().equals("ADMIN_PASSWORD")));
        }

        @Test
        @DisplayName("Admin nie widzi użytkownika bez dostępu")
        void adminDoesNotSeeUsersWithoutAccess() {
            User admin = userWithPassword(1L, "admin", LocalDateTime.now().plusDays(60));
            User restricted = userWithPassword(99L, "inny_dzial", LocalDateTime.now().plusDays(5));

            lenient().when(securityService.getCurrentUser()).thenReturn(admin);
            lenient().when(securityService.isCompanyAdmin()).thenReturn(true);
            lenient().when(securityService.isSuperAdmin()).thenReturn(false);
            when(securityService.hasAccessToUser(99L)).thenReturn(false);
            when(userRepository.findByPasswordExpiresAtBetween(any(), any())).thenReturn(List.of(restricted));
            mockNoDeviceOrCalibrationNotif();

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertTrue(result.stream().noneMatch(n -> n.type().equals("ADMIN_PASSWORD")));
        }

        @Test
        @DisplayName("Zwykły użytkownik nie widzi powiadomień admina")
        void regularUserDoesNotSeeAdminWarnings() {
            User regular = userWithPassword(5L, "zwykly", LocalDateTime.now().plusDays(60));

            when(securityService.getCurrentUser()).thenReturn(regular);
            when(securityService.isCompanyAdmin()).thenReturn(false);
            when(securityService.isSuperAdmin()).thenReturn(false);
            mockNoDeviceOrCalibrationNotif();

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertTrue(result.stream().noneMatch(n -> n.type().equals("ADMIN_PASSWORD")));
            // userRepository nie powinno być wywołane
            verify(userRepository, never()).findByPasswordExpiresAtBetween(any(), any());
        }
    }

    // =========================================================================
    // 5. Sortowanie i agregacja
    // =========================================================================

    @Nested
    @DisplayName("Sortowanie i agregacja wyników")
    class SortingAndAggregation {

        @Test
        @DisplayName("Powiadomienia są posortowane rosnąco według liczby dni (naglejsze pierwsze)")
        void notificationsAreSortedByDaysLeftAscending() {
            // Wzorcowanie za 5 dni, walidacja za 20 dni
            Calibration calSoon = calibrationFor("SN-SOON", 1L, LocalDate.now().plusDays(5));

            CoolingDevice dev = device(1L, "Stary chiller", "INW-100");
            Validation oldValidation = validation(1L, ValidationStatus.APPROVED,
                    LocalDateTime.now().minusDays(345)); // ~20 dni do wygaśnięcia rocznicy

            when(coolingDeviceService.getAllAccessibleDevices()).thenReturn(List.of(dev));
            when(validationService.getAllAccessibleValidations()).thenReturn(List.of(oldValidation));
            when(calibrationRepository.findLatestExpiringCalibrations(any(), any())).thenReturn(List.of(calSoon));
            when(securityService.getCurrentUser()).thenReturn(null);
            when(securityService.isCompanyAdmin()).thenReturn(false);
            when(securityService.isSuperAdmin()).thenReturn(false);

            List<NotificationDTO> result = notificationService.getAllNotifications();

            assertTrue(result.size() >= 2);
            // Pierwsze powinno mieć mniej dni niż drugie
            for (int i = 0; i < result.size() - 1; i++) {
                assertTrue(result.get(i).daysLeft() <= result.get(i + 1).daysLeft(),
                        "Powiadomienie na pozycji " + i + " powinno mieć daysLeft <= pozycja " + (i + 1));
            }
        }

        @Test
        @DisplayName("getAllNotifications nie rzuca wyjątku gdy serwisy zwracają puste listy")
        void doesNotThrow_whenAllServicesReturnEmpty() {
            when(coolingDeviceService.getAllAccessibleDevices()).thenReturn(List.of());
            when(validationService.getAllAccessibleValidations()).thenReturn(List.of());
            when(calibrationRepository.findLatestExpiringCalibrations(any(), any())).thenReturn(List.of());
            when(securityService.getCurrentUser()).thenReturn(null);
            when(securityService.isCompanyAdmin()).thenReturn(false);
            when(securityService.isSuperAdmin()).thenReturn(false);

            assertDoesNotThrow(() -> notificationService.getAllNotifications());
        }
    }

    // =========================================================================
    // 6. NotificationDTO — severity
    // =========================================================================

    @Nested
    @DisplayName("NotificationDTO.severityFor()")
    class SeverityTests {

        @Test
        @DisplayName("Zwraca 'danger' gdy daysLeft <= dangerThreshold")
        void returnsDanger_whenAtOrBelowThreshold() {
            assertEquals("danger", NotificationDTO.severityFor(0, 7));
            assertEquals("danger", NotificationDTO.severityFor(3, 7));
            assertEquals("danger", NotificationDTO.severityFor(7, 7));
        }

        @Test
        @DisplayName("Zwraca 'warning' gdy daysLeft > dangerThreshold")
        void returnsWarning_whenAboveThreshold() {
            assertEquals("warning", NotificationDTO.severityFor(8, 7));
            assertEquals("warning", NotificationDTO.severityFor(30, 7));
        }
    }
}
