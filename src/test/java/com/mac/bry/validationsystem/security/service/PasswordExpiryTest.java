package com.mac.bry.validationsystem.security.service;

import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.PasswordResetTokenRepository;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordExpiryTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordHistoryService passwordHistoryService;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("encodedPassword");
        testUser.setPasswordExpiryDays(90);
        testUser.setPasswordChangedAt(LocalDateTime.now().minusDays(30));
        testUser.setPasswordExpiresAt(LocalDateTime.now().plusDays(60)); // 90 - 30 = 60 dni do wygaśnięcia
    }

    @Test
    void isPasswordExpired_ShouldReturnFalseForNonExpiredPassword() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        boolean result = userService.isPasswordExpired(1L);

        // Then
        assertFalse(result);
    }

    @Test
    void isPasswordExpired_ShouldReturnTrueForExpiredPassword() {
        // Given
        testUser.setPasswordExpiresAt(LocalDateTime.now().minusDays(1)); // Wygasłe wczoraj
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        boolean result = userService.isPasswordExpired(1L);

        // Then
        assertTrue(result);
    }

    @Test
    void isPasswordExpired_ShouldReturnFalseForUserWithoutExpiry() {
        // Given
        testUser.setPasswordExpiresAt(null); // Brak wygaśnięcia
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        boolean result = userService.isPasswordExpired(1L);

        // Then
        assertFalse(result);
    }

    @Test
    void isPasswordExpired_ByUsername_ShouldReturnCorrectStatus() {
        // Given
        testUser.setPasswordExpiresAt(LocalDateTime.now().minusDays(1)); // Wygasłe
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // When
        boolean result = userService.isPasswordExpired("testuser");

        // Then
        assertTrue(result);
    }

    @Test
    void findUsersWithPasswordsExpiringInDays_ShouldReturnCorrectUsers() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        User expiring3Days = new User();
        expiring3Days.setPasswordExpiresAt(now.plusDays(3));

        User expiring5Days = new User();
        expiring5Days.setPasswordExpiresAt(now.plusDays(5));

        List<User> mockUsers = Arrays.asList(expiring3Days, expiring5Days);
        when(userRepository.findByPasswordExpiresAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockUsers);

        // When
        List<User> result = userService.findUsersWithPasswordsExpiringInDays(7);

        // Then
        assertEquals(2, result.size());
        verify(userRepository).findByPasswordExpiresAtBetween(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void findUsersWithExpiredPasswords_ShouldReturnExpiredUsers() {
        // Given
        User expiredUser1 = new User();
        expiredUser1.setPasswordExpiresAt(LocalDateTime.now().minusDays(1));
        User expiredUser2 = new User();
        expiredUser2.setPasswordExpiresAt(LocalDateTime.now().minusDays(5));

        List<User> expiredUsers = Arrays.asList(expiredUser1, expiredUser2);
        when(userRepository.findByPasswordExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(expiredUsers);

        // When
        List<User> result = userService.findUsersWithExpiredPasswords();

        // Then
        assertEquals(2, result.size());
        verify(userRepository).findByPasswordExpiresAtBefore(any(LocalDateTime.class));
    }

    @Test
    void extendPasswordExpiry_ShouldExtendExpiryDate() {
        // Given
        LocalDateTime originalExpiry = testUser.getPasswordExpiresAt();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        userService.extendPasswordExpiry(1L, 30);

        // Then
        assertEquals(originalExpiry.plusDays(30), testUser.getPasswordExpiresAt());
        verify(userRepository).save(testUser);
    }

    @Test
    void setPasswordExpiryPolicy_ShouldUpdateUserPolicy() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        userService.setPasswordExpiryPolicy(1L, 180);

        // Then
        assertEquals(180, testUser.getPasswordExpiryDays());
        // Powinna również zaktualizować datę wygaśnięcia
        LocalDateTime expectedExpiry = testUser.getPasswordChangedAt().plusDays(180);
        assertEquals(expectedExpiry, testUser.getPasswordExpiresAt());
        verify(userRepository).save(testUser);
    }

    @Test
    void setPasswordExpiryPolicy_ShouldDisableExpiryWhenSetToNull() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        userService.setPasswordExpiryPolicy(1L, null);

        // Then
        assertNull(testUser.getPasswordExpiryDays());
        assertNull(testUser.getPasswordExpiresAt());
        verify(userRepository).save(testUser);
    }

    @Test
    void forcePasswordChangeForExpiredUsers_ShouldSetMustChangePasswordFlag() {
        // Given
        User expiredUser1 = new User();
        expiredUser1.setId(1L);
        expiredUser1.setPasswordExpiresAt(LocalDateTime.now().minusDays(1));

        User expiredUser2 = new User();
        expiredUser2.setId(2L);
        expiredUser2.setPasswordExpiresAt(LocalDateTime.now().minusDays(5));

        List<User> expiredUsers = Arrays.asList(expiredUser1, expiredUser2);
        when(userRepository.findByPasswordExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(expiredUsers);

        // When
        userService.forcePasswordChangeForExpiredUsers();

        // Then
        assertTrue(expiredUser1.isMustChangePassword());
        assertTrue(expiredUser2.isMustChangePassword());
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void mustChangePasswordNow_ShouldReturnTrueForExpiredPassword() {
        // Given
        testUser.setPasswordExpiresAt(LocalDateTime.now().minusDays(1)); // Wygasłe
        testUser.setMustChangePassword(false); // Nie wymuszane przez admina
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        boolean result = userService.mustChangePasswordNow(1L);

        // Then
        assertTrue(result); // Powinno zwrócić true przez wygaśnięcie
    }

    @Test
    void mustChangePasswordNow_ShouldReturnTrueForForcedPassword() {
        // Given
        testUser.setMustChangePassword(true); // Wymuszane przez admina
        testUser.setPasswordExpiresAt(LocalDateTime.now().plusDays(30)); // Nie wygasłe
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        boolean result = userService.mustChangePasswordNow(1L);

        // Then
        assertTrue(result); // Powinno zwrócić true przez wymuszenie
    }

    @Test
    void mustChangePasswordNow_ShouldReturnFalseForValidPassword() {
        // Given
        testUser.setMustChangePassword(false); // Nie wymuszane
        testUser.setPasswordExpiresAt(LocalDateTime.now().plusDays(30)); // Nie wygasłe
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        boolean result = userService.mustChangePasswordNow(1L);

        // Then
        assertFalse(result);
    }

    @Test
    void changePasswordForced_ShouldUpdateExpiryDate() {
        // Given
        String newPassword = "NewPassword123!";
        String encodedPassword = "newEncodedPassword";

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(newPassword)).thenReturn(encodedPassword);

        // When
        userService.changePasswordForced(1L, newPassword);

        // Then
        assertEquals(encodedPassword, testUser.getPassword());
        assertFalse(testUser.isMustChangePassword());
        assertNotNull(testUser.getPasswordChangedAt());
        assertNotNull(testUser.getPasswordExpiresAt());
        // Sprawdź czy nowa data wygaśnięcia to passwordChangedAt + passwordExpiryDays
        LocalDateTime expectedExpiry = testUser.getPasswordChangedAt().plusDays(testUser.getPasswordExpiryDays());
        assertEquals(expectedExpiry, testUser.getPasswordExpiresAt());

        verify(passwordEncoder).encode(newPassword);
        verify(userRepository).save(testUser);
    }
}