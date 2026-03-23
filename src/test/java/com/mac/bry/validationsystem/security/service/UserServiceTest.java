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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

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
        testUser.setEmail("test@example.com");
        testUser.setPassword("Valid1Password!");
        testUser.setFailedLoginAttempts(0);
        testUser.setLocked(false);
    }

    @Test
    void shouldCreateUserWithBCryptPassword() {
        when(userRepository.existsByUsername(testUser.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(testUser.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(testUser.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        User created = userService.createUser(testUser);

        assertNotNull(created);
        assertEquals("encodedPassword", created.getPassword());
        verify(passwordEncoder, times(1)).encode("Valid1Password!");
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void shouldThrowExceptionForDuplicateUsername() {
        when(userRepository.existsByUsername(testUser.getUsername())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.createUser(testUser);
        });

        assertTrue(exception.getMessage().contains("Username already exists"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldValidatePasswordPolicy() {
        testUser.setPassword("weak");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.createUser(testUser);
        });

        assertTrue(exception.getMessage().contains("8 characters long"));

        testUser.setPassword("8charslong");
        exception = assertThrows(IllegalArgumentException.class, () -> userService.createUser(testUser));
        assertTrue(exception.getMessage().contains("uppercase"));

        testUser.setPassword("8CHARSLONG");
        exception = assertThrows(IllegalArgumentException.class, () -> userService.createUser(testUser));
        assertTrue(exception.getMessage().contains("lowercase"));

        testUser.setPassword("NoDigitsHere");
        exception = assertThrows(IllegalArgumentException.class, () -> userService.createUser(testUser));
        assertTrue(exception.getMessage().contains("digit"));

        testUser.setPassword("8CharsL0ng");
        exception = assertThrows(IllegalArgumentException.class, () -> userService.createUser(testUser));
        assertTrue(exception.getMessage().contains("special character"));
    }

    @Test
    void shouldRecordFailedLoginAttempts() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        userService.recordFailedLoginAttempt(testUser.getId());

        assertEquals(1, testUser.getFailedLoginAttempts());
        assertFalse(testUser.isLocked());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void shouldLockAccountAfter5Failures() {
        testUser.setFailedLoginAttempts(4);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        userService.recordFailedLoginAttempt(testUser.getId());

        assertEquals(5, testUser.getFailedLoginAttempts());
        assertTrue(testUser.isLocked());
        assertNotNull(testUser.getLockedUntil());
        verify(userRepository, times(1)).save(testUser);
    }
}
