package com.mac.bry.validationsystem.security.service;

import com.mac.bry.validationsystem.security.PasswordHistory;
import com.mac.bry.validationsystem.security.PasswordHistory.PasswordChangeType;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.PasswordHistoryRepository;
import com.mac.bry.validationsystem.security.repository.PasswordResetTokenRepository;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testy integracyjne UserService + PasswordHistoryService.
 *
 * Weryfikuje pelny flow zmiany hasla z walidacja historii
 * zgodnie z FDA 21 CFR Part 11 Sec. 11.300(b).
 */
@ExtendWith(MockitoExtension.class)
class PasswordHistoryIntegrationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;

    private PasswordEncoder passwordEncoder;
    private PasswordHistoryService passwordHistoryService;
    private UserService userService;

    @Captor
    private ArgumentCaptor<PasswordHistory> historyCaptor;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Uzyj prawdziwego BCryptPasswordEncoder (strength 4 dla szybkosci testow)
        passwordEncoder = new BCryptPasswordEncoder(4);

        passwordHistoryService = new PasswordHistoryService(
                passwordHistoryRepository, passwordEncoder);

        userService = new UserService(
                userRepository, passwordResetTokenRepository,
                passwordEncoder, passwordHistoryService);

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword(passwordEncoder.encode("OldP@ssw0rd!"));
        testUser.setPasswordExpiryDays(90);
    }

    @Test
    @DisplayName("createUser powinno zapisac inicjalne haslo do historii")
    void createUserShouldRecordInitialPassword() {
        // given
        User newUser = new User();
        newUser.setUsername("newuser");
        newUser.setEmail("new@example.com");
        newUser.setPassword("Valid1P@ss!");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = (User) i.getArguments()[0];
            u.setId(2L);
            return u;
        });
        when(passwordHistoryRepository.save(any(PasswordHistory.class)))
                .thenAnswer(i -> i.getArguments()[0]);
        when(passwordHistoryRepository.countByUserId(2L)).thenReturn(1L);

        // when
        User created = userService.createUser(newUser);

        // then
        verify(passwordHistoryRepository).save(historyCaptor.capture());
        PasswordHistory recorded = historyCaptor.getValue();
        assertEquals(PasswordChangeType.INITIAL, recorded.getChangeType());
        assertEquals("SYSTEM", recorded.getChangedBy());
        assertTrue(passwordEncoder.matches("Valid1P@ss!", recorded.getPasswordHash()));
    }

    @Test
    @DisplayName("changePasswordSelf powinno odrzucic haslo z historii")
    void changePasswordSelfShouldRejectHistoricalPassword() {
        // given
        String oldPassword = "OldP@ssw0rd!";
        String oldHash = passwordEncoder.encode(oldPassword);
        testUser.setPassword(oldHash);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        PasswordHistory oldEntry = new PasswordHistory(
                testUser, oldHash, "testuser", PasswordChangeType.SELF);
        when(passwordHistoryRepository.findTopNByUserIdOrderByChangedAtDesc(eq(1L), eq(12)))
                .thenReturn(List.of(oldEntry));

        // when / then - proba uzycia tego samego hasla
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                userService.changePasswordSelf(1L, oldPassword, oldPassword));

        assertTrue(exception.getMessage().contains("FDA 21 CFR Part 11"));
        assertTrue(exception.getMessage().contains("12"));
    }

    @Test
    @DisplayName("changePasswordSelf powinno zaakceptowac nowe unikalne haslo")
    void changePasswordSelfShouldAcceptNewUniquePassword() {
        // given
        String currentPassword = "OldP@ssw0rd!";
        String currentHash = passwordEncoder.encode(currentPassword);
        testUser.setPassword(currentHash);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        PasswordHistory oldEntry = new PasswordHistory(
                testUser, currentHash, "testuser", PasswordChangeType.SELF);
        when(passwordHistoryRepository.findTopNByUserIdOrderByChangedAtDesc(eq(1L), eq(12)))
                .thenReturn(List.of(oldEntry));
        when(passwordHistoryRepository.save(any(PasswordHistory.class)))
                .thenAnswer(i -> i.getArguments()[0]);
        when(passwordHistoryRepository.countByUserId(1L)).thenReturn(2L);

        // when
        String newPassword = "NewUniqueP@ss1";
        assertDoesNotThrow(() ->
                userService.changePasswordSelf(1L, currentPassword, newPassword));

        // then
        verify(passwordHistoryRepository).save(historyCaptor.capture());
        PasswordHistory recorded = historyCaptor.getValue();
        assertEquals(PasswordChangeType.SELF, recorded.getChangeType());
        assertTrue(passwordEncoder.matches(newPassword, recorded.getPasswordHash()));
    }

    @Test
    @DisplayName("changePasswordForced powinno odrzucic haslo z historii")
    void changePasswordForcedShouldRejectHistoricalPassword() {
        // given
        String reusedPassword = "Reused1P@ss!";
        String reusedHash = passwordEncoder.encode(reusedPassword);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        PasswordHistory oldEntry = new PasswordHistory(
                testUser, reusedHash, "testuser", PasswordChangeType.INITIAL);
        when(passwordHistoryRepository.findTopNByUserIdOrderByChangedAtDesc(eq(1L), eq(12)))
                .thenReturn(List.of(oldEntry));

        // when / then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                userService.changePasswordForced(1L, reusedPassword));

        assertTrue(exception.getMessage().contains("FDA 21 CFR Part 11"));
    }

    @Test
    @DisplayName("changePassword (admin) powinno zapisac do historii jako ADMIN_RESET")
    void adminChangePasswordShouldRecordAsAdminReset() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);
        when(passwordHistoryRepository.findTopNByUserIdOrderByChangedAtDesc(eq(1L), eq(12)))
                .thenReturn(java.util.Collections.emptyList());
        when(passwordHistoryRepository.save(any(PasswordHistory.class)))
                .thenAnswer(i -> i.getArguments()[0]);
        when(passwordHistoryRepository.countByUserId(1L)).thenReturn(1L);

        // when
        userService.changePassword(1L, "AdminReset1P@ss!");

        // then
        verify(passwordHistoryRepository).save(historyCaptor.capture());
        assertEquals(PasswordChangeType.ADMIN_RESET, historyCaptor.getValue().getChangeType());
        assertEquals("ADMIN", historyCaptor.getValue().getChangedBy());
    }
}
