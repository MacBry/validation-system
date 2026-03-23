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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForcedPasswordChangeTest {

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
        testUser.setMustChangePassword(true);
        testUser.setPasswordChangedAt(null);
    }

    @Test
    void createUser_ShouldSetMustChangePasswordFlag() {
        // Given
        User newUser = new User();
        newUser.setUsername("newuser");
        newUser.setEmail("newuser@test.com");
        newUser.setPassword("Password123!");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        // When
        User result = userService.createUser(newUser);

        // Then
        assertTrue(result.isMustChangePassword(), "Nowy użytkownik powinien mieć flagę wymuszonej zmiany hasła");
        assertNull(result.getPasswordChangedAt(), "Data zmiany hasła powinna być null dla nowego użytkownika");

        verify(userRepository)
                .save(argThat(user -> user.isMustChangePassword() && user.getPasswordChangedAt() == null));
    }

    @Test
    void setMustChangePassword_ShouldUpdateFlag() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        userService.setMustChangePassword(1L, false);

        // Then
        assertFalse(testUser.isMustChangePassword());
        verify(userRepository).save(testUser);
    }

    @Test
    void setMustChangePassword_ShouldThrowExceptionForNonExistentUser() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.setMustChangePassword(1L, true));

        assertEquals("User not found: 1", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordForced_ShouldUpdatePasswordAndClearFlag() {
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
        assertTrue(testUser.getPasswordChangedAt().isAfter(LocalDateTime.now().minusSeconds(1)));

        verify(passwordEncoder).encode(newPassword);
        verify(userRepository).save(testUser);
    }

    @Test
    void changePasswordForced_ShouldValidatePasswordStrength() {
        // Given
        String weakPassword = "weak";
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.changePasswordForced(1L, weakPassword));

        assertTrue(exception.getMessage().contains("Password must be at least 8 characters long"));
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void mustChangePassword_ById_ShouldReturnCorrectStatus() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        boolean result = userService.mustChangePassword(1L);

        // Then
        assertTrue(result);
    }

    @Test
    void mustChangePassword_ById_ShouldReturnFalseForNonExistentUser() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // When
        boolean result = userService.mustChangePassword(1L);

        // Then
        assertFalse(result);
    }

    @Test
    void mustChangePassword_ByUsername_ShouldReturnCorrectStatus() {
        // Given
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // When
        boolean result = userService.mustChangePassword("testuser");

        // Then
        assertTrue(result);
    }

    @Test
    void mustChangePassword_ByUsername_ShouldReturnFalseForNonExistentUser() {
        // Given
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // When
        boolean result = userService.mustChangePassword("nonexistent");

        // Then
        assertFalse(result);
    }

    @Test
    void changePasswordSelf_ShouldUpdatePasswordChangedAt() {
        // Given
        String currentPassword = "CurrentPassword123!";
        String newPassword = "NewPassword123!";
        String encodedNewPassword = "newEncodedPassword";

        testUser.setMustChangePassword(false); // User who can change password normally
        testUser.setPasswordChangedAt(LocalDateTime.now().minusDays(30));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(currentPassword, testUser.getPassword())).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn(encodedNewPassword);

        // When
        userService.changePasswordSelf(1L, currentPassword, newPassword);

        // Then
        assertEquals(encodedNewPassword, testUser.getPassword());
        assertFalse(testUser.isMustChangePassword());
        assertNotNull(testUser.getPasswordChangedAt());
        assertTrue(testUser.getPasswordChangedAt().isAfter(LocalDateTime.now().minusSeconds(1)));

        verify(userRepository).save(testUser);
    }

    @Test
    void changePassword_AdminReset_ShouldSetMustChangePasswordFlag() {
        // Given
        String newPassword = "AdminResetPassword123!";
        String encodedPassword = "adminEncodedPassword";

        testUser.setMustChangePassword(false); // Initially false
        testUser.setPasswordChangedAt(LocalDateTime.now());

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(newPassword)).thenReturn(encodedPassword);

        // When
        userService.changePassword(1L, newPassword);

        // Then
        assertEquals(encodedPassword, testUser.getPassword());
        assertTrue(testUser.isMustChangePassword());
        assertNull(testUser.getPasswordChangedAt()); // Should be reset

        verify(userRepository).save(testUser);
    }
}