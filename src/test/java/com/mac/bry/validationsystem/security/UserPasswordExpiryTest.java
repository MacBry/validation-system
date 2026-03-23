package com.mac.bry.validationsystem.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy metod wygaśnięcia hasła w encji User
 */
class UserPasswordExpiryTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("testuser");
        user.setPasswordExpiryDays(90);
    }

    @Test
    void isPasswordExpired_ShouldReturnFalseWhenNoExpirySet() {
        // Given
        user.setPasswordExpiresAt(null);

        // When
        boolean result = user.isPasswordExpired();

        // Then
        assertFalse(result);
    }

    @Test
    void isPasswordExpired_ShouldReturnFalseWhenPasswordNotExpired() {
        // Given
        user.setPasswordExpiresAt(LocalDateTime.now().plusDays(10));

        // When
        boolean result = user.isPasswordExpired();

        // Then
        assertFalse(result);
    }

    @Test
    void isPasswordExpired_ShouldReturnTrueWhenPasswordExpired() {
        // Given
        user.setPasswordExpiresAt(LocalDateTime.now().minusDays(1));

        // When
        boolean result = user.isPasswordExpired();

        // Then
        assertTrue(result);
    }

    @Test
    void isPasswordExpiringInDays_ShouldReturnFalseWhenNoExpirySet() {
        // Given
        user.setPasswordExpiresAt(null);

        // When
        boolean result = user.isPasswordExpiringInDays(7);

        // Then
        assertFalse(result);
    }

    @Test
    void isPasswordExpiringInDays_ShouldReturnTrueWhenExpiringWithinDays() {
        // Given
        user.setPasswordExpiresAt(LocalDateTime.now().plusDays(5));

        // When
        boolean result = user.isPasswordExpiringInDays(7);

        // Then
        assertTrue(result);
    }

    @Test
    void isPasswordExpiringInDays_ShouldReturnFalseWhenNotExpiringWithinDays() {
        // Given
        user.setPasswordExpiresAt(LocalDateTime.now().plusDays(10));

        // When
        boolean result = user.isPasswordExpiringInDays(7);

        // Then
        assertFalse(result);
    }

    @Test
    void mustChangePasswordNow_ShouldReturnTrueWhenForcedByAdmin() {
        // Given
        user.setMustChangePassword(true);
        user.setPasswordExpiresAt(LocalDateTime.now().plusDays(30)); // Nie wygasłe

        // When
        boolean result = user.mustChangePasswordNow();

        // Then
        assertTrue(result);
    }

    @Test
    void mustChangePasswordNow_ShouldReturnTrueWhenPasswordExpired() {
        // Given
        user.setMustChangePassword(false);
        user.setPasswordExpiresAt(LocalDateTime.now().minusDays(1)); // Wygasłe

        // When
        boolean result = user.mustChangePasswordNow();

        // Then
        assertTrue(result);
    }

    @Test
    void mustChangePasswordNow_ShouldReturnFalseWhenNoChangeRequired() {
        // Given
        user.setMustChangePassword(false);
        user.setPasswordExpiresAt(LocalDateTime.now().plusDays(30)); // Nie wygasłe

        // When
        boolean result = user.mustChangePasswordNow();

        // Then
        assertFalse(result);
    }

    @Test
    void getDaysUntilPasswordExpiry_ShouldReturnNullWhenNoExpirySet() {
        // Given
        user.setPasswordExpiresAt(null);

        // When
        Long result = user.getDaysUntilPasswordExpiry();

        // Then
        assertNull(result);
    }

    @Test
    void getDaysUntilPasswordExpiry_ShouldReturnCorrectDays() {
        // Given
        user.setPasswordExpiresAt(LocalDateTime.now().plusDays(15));

        // When
        Long result = user.getDaysUntilPasswordExpiry();

        // Then
        assertEquals(15L, result);
    }

    @Test
    void getDaysUntilPasswordExpiry_ShouldReturnNegativeForExpiredPassword() {
        // Given
        user.setPasswordExpiresAt(LocalDateTime.now().minusDays(5));

        // When
        Long result = user.getDaysUntilPasswordExpiry();

        // Then
        assertEquals(-5L, result);
    }

    @Test
    void extendPasswordExpiry_ShouldExtendExistingExpiry() {
        // Given
        LocalDateTime originalExpiry = LocalDateTime.now().plusDays(10);
        user.setPasswordExpiresAt(originalExpiry);

        // When
        user.extendPasswordExpiry(30);

        // Then
        assertEquals(originalExpiry.plusDays(30), user.getPasswordExpiresAt());
    }

    @Test
    void extendPasswordExpiry_ShouldSetExpiryWhenNoneExists() {
        // Given
        user.setPasswordExpiresAt(null);
        user.setPasswordExpiryDays(90);

        // When
        user.extendPasswordExpiry(30);

        // Then
        assertNotNull(user.getPasswordExpiresAt());
        // Powinno być około 120 dni od teraz (90 + 30)
        long daysFromNow = java.time.temporal.ChronoUnit.DAYS.between(
                LocalDateTime.now(), user.getPasswordExpiresAt());
        assertTrue(daysFromNow >= 119 && daysFromNow <= 121); // Tolerancja na czas wykonania testu
    }

    @Test
    void extendPasswordExpiry_ShouldDoNothingWhenNoExpiryAndNoPolicySet() {
        // Given
        user.setPasswordExpiresAt(null);
        user.setPasswordExpiryDays(null);

        // When
        user.extendPasswordExpiry(30);

        // Then
        assertNull(user.getPasswordExpiresAt());
    }

    @Test
    void passwordExpiryIntegrationTest_FullLifecycle() {
        // Given - Nowy użytkownik
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setPasswordExpiryDays(90);
        user.setPasswordExpiresAt(LocalDateTime.now().plusDays(90));

        // When - Sprawdź initial state
        assertFalse(user.isPasswordExpired());
        assertFalse(user.isPasswordExpiringInDays(7));
        assertTrue(user.isPasswordExpiringInDays(95));
        assertEquals(90L, user.getDaysUntilPasswordExpiry());

        // When - Symuluj upływ czasu (hasło wygasa za 5 dni)
        user.setPasswordExpiresAt(LocalDateTime.now().plusDays(5));

        // Then
        assertFalse(user.isPasswordExpired());
        assertTrue(user.isPasswordExpiringInDays(7));
        long daysUntil = user.getDaysUntilPasswordExpiry();
        assertTrue(daysUntil == 5L || daysUntil == 4L, "Days until expiry should be 5 or 4 depending on timing (was " + daysUntil + ")");

        // When - Symuluj wygaśnięcie
        user.setPasswordExpiresAt(LocalDateTime.now().minusDays(1));

        // Then
        assertTrue(user.isPasswordExpired());
        assertTrue(user.mustChangePasswordNow());
        assertEquals(-1L, user.getDaysUntilPasswordExpiry());
    }
}