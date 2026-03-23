package com.mac.bry.validationsystem.security.service;

import com.mac.bry.validationsystem.security.PasswordHistory;
import com.mac.bry.validationsystem.security.PasswordHistory.PasswordChangeType;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.PasswordHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testy jednostkowe dla PasswordHistoryService.
 * Weryfikacja zgodnosci z FDA 21 CFR Part 11 Sec. 11.300(b).
 */
@ExtendWith(MockitoExtension.class)
class PasswordHistoryServiceTest {

    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordHistoryService passwordHistoryService;

    @Captor
    private ArgumentCaptor<PasswordHistory> historyCaptor;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
    }

    // ========================================================================
    // isPasswordPreviouslyUsed - FDA 21 CFR Part 11 Sec. 11.300(b)
    // ========================================================================

    @Nested
    @DisplayName("isPasswordPreviouslyUsed - weryfikacja unikalnosci hasel")
    class PasswordUniquenessTests {

        @Test
        @DisplayName("Powinno zwrocic true gdy haslo bylo juz uzywane")
        void shouldReturnTrueWhenPasswordWasPreviouslyUsed() {
            // given
            PasswordHistory oldEntry = new PasswordHistory(
                    testUser, "$2a$12$oldHashedPassword", "testuser", PasswordChangeType.SELF);

            when(passwordHistoryRepository.findTopNByUserIdOrderByChangedAtDesc(
                    eq(1L), eq(PasswordHistory.MAX_HISTORY_SIZE)))
                    .thenReturn(List.of(oldEntry));
            when(passwordEncoder.matches("OldPassword1!", "$2a$12$oldHashedPassword"))
                    .thenReturn(true);

            // when
            boolean result = passwordHistoryService.isPasswordPreviouslyUsed(1L, "OldPassword1!");

            // then
            assertTrue(result, "Haslo uzyte wczesniej powinno zostac wykryte");
            verify(passwordHistoryRepository).findTopNByUserIdOrderByChangedAtDesc(1L, 12);
        }

        @Test
        @DisplayName("Powinno zwrocic false gdy haslo jest nowe i unikalne")
        void shouldReturnFalseWhenPasswordIsNew() {
            // given
            PasswordHistory oldEntry = new PasswordHistory(
                    testUser, "$2a$12$oldHashedPassword", "testuser", PasswordChangeType.SELF);

            when(passwordHistoryRepository.findTopNByUserIdOrderByChangedAtDesc(
                    eq(1L), eq(PasswordHistory.MAX_HISTORY_SIZE)))
                    .thenReturn(List.of(oldEntry));
            when(passwordEncoder.matches("NewUniqueP@ss1", "$2a$12$oldHashedPassword"))
                    .thenReturn(false);

            // when
            boolean result = passwordHistoryService.isPasswordPreviouslyUsed(1L, "NewUniqueP@ss1");

            // then
            assertFalse(result, "Nowe haslo powinno byc zaakceptowane");
        }

        @Test
        @DisplayName("Powinno zwrocic false gdy historia jest pusta (nowy uzytkownik)")
        void shouldReturnFalseWhenHistoryIsEmpty() {
            // given
            when(passwordHistoryRepository.findTopNByUserIdOrderByChangedAtDesc(
                    eq(1L), eq(PasswordHistory.MAX_HISTORY_SIZE)))
                    .thenReturn(Collections.emptyList());

            // when
            boolean result = passwordHistoryService.isPasswordPreviouslyUsed(1L, "AnyP@ssw0rd!");

            // then
            assertFalse(result);
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("Powinno sprawdzac dokladnie 12 ostatnich hasel (MAX_HISTORY_SIZE)")
        void shouldCheckExactlyMaxHistorySizePasswords() {
            // given
            when(passwordHistoryRepository.findTopNByUserIdOrderByChangedAtDesc(
                    eq(1L), eq(12)))
                    .thenReturn(Collections.emptyList());

            // when
            passwordHistoryService.isPasswordPreviouslyUsed(1L, "Test1234!");

            // then
            verify(passwordHistoryRepository).findTopNByUserIdOrderByChangedAtDesc(1L, 12);
        }

        @Test
        @DisplayName("Powinno wykryc haslo nawet jesli jest na 12. pozycji w historii")
        void shouldDetectPasswordAtLastPositionInHistory() {
            // given - 12 wpisow, match na ostatnim
            List<PasswordHistory> entries = new java.util.ArrayList<>();
            for (int i = 0; i < 11; i++) {
                PasswordHistory entry = new PasswordHistory(
                        testUser, "$2a$12$hash" + i, "testuser", PasswordChangeType.SELF);
                entries.add(entry);
            }
            PasswordHistory lastEntry = new PasswordHistory(
                    testUser, "$2a$12$matchingHash", "testuser", PasswordChangeType.SELF);
            entries.add(lastEntry);

            when(passwordHistoryRepository.findTopNByUserIdOrderByChangedAtDesc(eq(1L), eq(12)))
                    .thenReturn(entries);
            // Nie pasuje do pierwszych 11
            for (int i = 0; i < 11; i++) {
                when(passwordEncoder.matches("ReusedP@ss1", "$2a$12$hash" + i))
                        .thenReturn(false);
            }
            // Pasuje do ostatniego
            when(passwordEncoder.matches("ReusedP@ss1", "$2a$12$matchingHash"))
                    .thenReturn(true);

            // when
            boolean result = passwordHistoryService.isPasswordPreviouslyUsed(1L, "ReusedP@ss1");

            // then
            assertTrue(result, "Haslo na 12. pozycji powinno byc wykryte");
        }
    }

    // ========================================================================
    // recordPasswordChange - audit trail
    // ========================================================================

    @Nested
    @DisplayName("recordPasswordChange - zapis do historii")
    class RecordPasswordChangeTests {

        @Test
        @DisplayName("Powinno zapisac wpis historii z poprawnymi danymi")
        void shouldRecordPasswordChangeWithCorrectData() {
            // given
            when(passwordHistoryRepository.save(any(PasswordHistory.class)))
                    .thenAnswer(i -> i.getArguments()[0]);
            when(passwordHistoryRepository.countByUserId(1L)).thenReturn(1L);

            // when
            passwordHistoryService.recordPasswordChange(
                    testUser, "$2a$12$encodedHash", "testuser", PasswordChangeType.SELF);

            // then
            verify(passwordHistoryRepository).save(historyCaptor.capture());
            PasswordHistory saved = historyCaptor.getValue();

            assertEquals(testUser, saved.getUser());
            assertEquals("$2a$12$encodedHash", saved.getPasswordHash());
            assertEquals("testuser", saved.getChangedBy());
            assertEquals(PasswordChangeType.SELF, saved.getChangeType());
            assertNotNull(saved.getChangedAt());
        }

        @Test
        @DisplayName("Powinno zapisac rozne typy zmian hasel")
        void shouldRecordDifferentChangeTypes() {
            when(passwordHistoryRepository.save(any(PasswordHistory.class)))
                    .thenAnswer(i -> i.getArguments()[0]);
            when(passwordHistoryRepository.countByUserId(1L)).thenReturn(1L);

            for (PasswordChangeType type : PasswordChangeType.values()) {
                passwordHistoryService.recordPasswordChange(
                        testUser, "$2a$12$hash", "actor", type);
            }

            verify(passwordHistoryRepository, times(PasswordChangeType.values().length))
                    .save(any(PasswordHistory.class));
        }

        @Test
        @DisplayName("Powinno uruchomic cleanup po zapisie")
        void shouldTriggerCleanupAfterRecord() {
            when(passwordHistoryRepository.save(any(PasswordHistory.class)))
                    .thenAnswer(i -> i.getArguments()[0]);
            when(passwordHistoryRepository.countByUserId(1L)).thenReturn(15L);

            passwordHistoryService.recordPasswordChange(
                    testUser, "$2a$12$hash", "testuser", PasswordChangeType.SELF);

            verify(passwordHistoryRepository).deleteOldEntries(1L, PasswordHistory.MAX_HISTORY_SIZE);
        }
    }

    // ========================================================================
    // cleanupOldEntries - utrzymanie limitu historii
    // ========================================================================

    @Nested
    @DisplayName("cleanupOldEntries - utrzymanie limitu")
    class CleanupTests {

        @Test
        @DisplayName("Powinno usunac stare wpisy gdy przekroczono limit")
        void shouldDeleteOldEntriesWhenOverLimit() {
            when(passwordHistoryRepository.countByUserId(1L)).thenReturn(15L);

            passwordHistoryService.cleanupOldEntries(1L);

            verify(passwordHistoryRepository).deleteOldEntries(1L, PasswordHistory.MAX_HISTORY_SIZE);
        }

        @Test
        @DisplayName("Nie powinno usuwac wpisow gdy nie przekroczono limitu")
        void shouldNotDeleteWhenUnderLimit() {
            when(passwordHistoryRepository.countByUserId(1L)).thenReturn(5L);

            passwordHistoryService.cleanupOldEntries(1L);

            verify(passwordHistoryRepository, never()).deleteOldEntries(anyLong(), anyInt());
        }

        @Test
        @DisplayName("Nie powinno usuwac wpisow gdy dokladnie na limicie")
        void shouldNotDeleteWhenExactlyAtLimit() {
            when(passwordHistoryRepository.countByUserId(1L)).thenReturn(12L);

            passwordHistoryService.cleanupOldEntries(1L);

            verify(passwordHistoryRepository, never()).deleteOldEntries(anyLong(), anyInt());
        }
    }

    // ========================================================================
    // Stałe konfiguracyjne
    // ========================================================================

    @Test
    @DisplayName("MAX_HISTORY_SIZE powinno wynosic 12 (GxP best practice)")
    void maxHistorySizeShouldBe12() {
        assertEquals(12, PasswordHistory.MAX_HISTORY_SIZE,
                "GxP best practice wymaga przechowywania 12 ostatnich hasel");
    }
}
