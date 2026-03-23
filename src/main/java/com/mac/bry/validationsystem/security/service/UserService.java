package com.mac.bry.validationsystem.security.service;

import com.mac.bry.validationsystem.security.PasswordHistory;
import com.mac.bry.validationsystem.security.PasswordHistory.PasswordChangeType;
import com.mac.bry.validationsystem.security.PasswordResetToken;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.PasswordResetTokenRepository;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordHistoryService passwordHistoryService;

    @Transactional
    public User createUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + user.getUsername());
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + user.getEmail());
        }

        validatePassword(user.getPassword());
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        // Nowi użytkownicy muszą zmienić hasło przy pierwszym logowaniu
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(null); // Hasło zostanie "zmienione" dopiero przy pierwszym logowaniu

        User savedUser = userRepository.save(user);

        // FDA 21 CFR Part 11 Sec. 11.300(b): zapisz inicjalne hasło do historii
        passwordHistoryService.recordPasswordChange(
                savedUser, encodedPassword, "SYSTEM", PasswordChangeType.INITIAL);

        return savedUser;
    }

    @Transactional
    public void changePassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        validatePassword(newPassword);

        // FDA 21 CFR Part 11: sprawdź historię haseł
        validatePasswordNotInHistory(userId, newPassword);

        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);

        // Po resecie przez administratora użytkownik musi zmienić hasło
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(null); // Reset timestamp

        userRepository.save(user);

        // Zapisz do historii haseł
        passwordHistoryService.recordPasswordChange(
                user, encodedPassword, "ADMIN", PasswordChangeType.ADMIN_RESET);
    }

    @Transactional
    public void setUserEnabled(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setEnabled(enabled);
        userRepository.save(user);
    }

    @Transactional
    public void setUserLocked(Long userId, boolean locked) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setLocked(locked);
        if (!locked) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
        userRepository.save(user);
    }

    @Transactional
    public void recordFailedLoginAttempt(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= 5) {
            user.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(15));
            // GxP SECURITY: Do not set 'locked = true' for temporary 15min lockout, 
            // so it can auto-unlock after time passes.
        }
        userRepository.save(user);
    }

    @Transactional
    public void resetFailedLoginAttempts(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLocked(false);
        userRepository.save(user);
    }

    @Transactional
    public void changePasswordSelf(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Użytkownik nie został znaleziony"));

        // Weryfikacja aktualnego hasła
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Aktualne hasło jest nieprawidłowe");
        }

        // Walidacja nowego hasła
        validatePassword(newPassword);

        // FDA 21 CFR Part 11: sprawdź historię haseł
        validatePasswordNotInHistory(userId, newPassword);

        // Zapisanie nowego hasła
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);

        // Aktualizuj śledzenie zmian hasła
        updatePasswordChangedAt(user);

        userRepository.save(user);

        // Zapisz do historii haseł
        passwordHistoryService.recordPasswordChange(
                user, encodedPassword, user.getUsername(), PasswordChangeType.SELF);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Password must contain at least one digit");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            throw new IllegalArgumentException("Password must contain at least one special character");
        }
    }

    /**
     * Weryfikuje czy hasło nie było wcześniej używane.
     * FDA 21 CFR Part 11 Sec. 11.300(b): hasła muszą być unikalne.
     * Sprawdza ostatnie 12 haseł (GxP best practice).
     *
     * @param userId      ID użytkownika
     * @param rawPassword nowe hasło w postaci jawnej
     * @throws IllegalArgumentException jeśli hasło było już używane
     */
    private void validatePasswordNotInHistory(Long userId, String rawPassword) {
        if (passwordHistoryService.isPasswordPreviouslyUsed(userId, rawPassword)) {
            log.warn("Odrzucono hasło dla użytkownika ID: {} - hasło było już używane " +
                    "(FDA 21 CFR Part 11 Sec. 11.300(b))", userId);
            throw new IllegalArgumentException(
                    "Hasło było już wcześniej używane. Ze względów bezpieczeństwa (FDA 21 CFR Part 11) " +
                    "nie można ponownie użyć żadnego z ostatnich " + PasswordHistory.MAX_HISTORY_SIZE + " haseł.");
        }
    }

    @Transactional
    public String generatePasswordResetToken(String emailOrUsername) {
        User user = userRepository.findByEmail(emailOrUsername)
                .orElseGet(() -> userRepository.findByUsername(emailOrUsername)
                        .orElse(null));

        if (user == null) {
            // Ze względów bezpieczeństwa nie informujemy, że użytkownik nie istnieje
            return null;
        }

        // Usuń stare tokeny
        passwordResetTokenRepository.deleteByUser(user);

        // Wygeneruj nowy
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(token, user);
        passwordResetTokenRepository.save(resetToken);

        return token;
    }

    public Optional<User> getByPasswordResetToken(String token) {
        return passwordResetTokenRepository.findByToken(token)
                .filter(rt -> !rt.isExpired())
                .map(PasswordResetToken::getUser);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Nieprawidłowy token resetowania hasła"));

        if (resetToken.isExpired()) {
            throw new IllegalArgumentException("Token resetowania hasła wygasł");
        }

        User user = resetToken.getUser();
        validatePassword(newPassword);

        // FDA 21 CFR Part 11: sprawdź historię haseł
        validatePasswordNotInHistory(user.getId(), newPassword);

        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);

        // Aktualizuj datę zmiany hasła i usuń flagę wymuszonej zmiany
        updatePasswordChangedAt(user);

        userRepository.save(user);

        // Usuń token po użyciu
        passwordResetTokenRepository.delete(resetToken);

        // Zapisz do historii haseł
        passwordHistoryService.recordPasswordChange(
                user, encodedPassword, user.getUsername(), PasswordChangeType.PASSWORD_RESET);
    }

    // ========================================================================
    // FORCED PASSWORD CHANGE METHODS
    // ========================================================================

    /**
     * Ustawia flagę wymuszonej zmiany hasła dla użytkownika
     */
    @Transactional
    public void setMustChangePassword(Long userId, boolean mustChange) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setMustChangePassword(mustChange);
        userRepository.save(user);
    }

    /**
     * Wymuszana zmiana hasła (bez weryfikacji obecnego hasła)
     * Używana przy pierwszym logowaniu lub reset przez administratora
     */
    @Transactional
    public void changePasswordForced(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Użytkownik nie został znaleziony"));

        // Walidacja nowego hasła
        validatePassword(newPassword);

        // FDA 21 CFR Part 11: sprawdź historię haseł
        validatePasswordNotInHistory(userId, newPassword);

        // Zapisanie nowego hasła
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);

        // Aktualizuj śledzenie zmian hasła
        updatePasswordChangedAt(user);

        userRepository.save(user);

        // Zapisz do historii haseł
        passwordHistoryService.recordPasswordChange(
                user, encodedPassword, user.getUsername(), PasswordChangeType.FORCED);
    }

    /**
     * Sprawdza czy użytkownik musi zmienić hasło
     */
    @Transactional(readOnly = true)
    public boolean mustChangePassword(Long userId) {
        return userRepository.findById(userId)
                .map(User::isMustChangePassword)
                .orElse(false);
    }

    /**
     * Sprawdza czy użytkownik musi zmienić hasło (wersja po username)
     */
    @Transactional(readOnly = true)
    public boolean mustChangePassword(String username) {
        return userRepository.findByUsername(username)
                .map(User::isMustChangePassword)
                .orElse(false);
    }

    /**
     * Aktualizuje datę zmiany hasła, usuwa flagę wymuszonej zmiany i ustawia nową datę wygaśnięcia
     */
    private void updatePasswordChangedAt(User user) {
        LocalDateTime now = LocalDateTime.now();
        user.setPasswordChangedAt(now);
        user.setMustChangePassword(false);

        // Ustaw nową datę wygaśnięcia hasła
        if (user.getPasswordExpiryDays() != null && user.getPasswordExpiryDays() > 0) {
            user.setPasswordExpiresAt(now.plusDays(user.getPasswordExpiryDays()));
        }
    }

    // ========================================================================
    // Password Expiry Management
    // ========================================================================

    /**
     * Sprawdza czy hasło użytkownika wygasło
     */
    public boolean isPasswordExpired(Long userId) {
        return userRepository.findById(userId)
                .map(User::isPasswordExpired)
                .orElse(false);
    }

    /**
     * Sprawdza czy hasło użytkownika wygasło po nazwie użytkownika
     */
    public boolean isPasswordExpired(String username) {
        return userRepository.findByUsername(username)
                .map(User::isPasswordExpired)
                .orElse(false);
    }

    /**
     * Znajdź użytkowników z wygasającymi hasłami w ciągu określonych dni
     */
    @Transactional(readOnly = true)
    public List<User> findUsersWithPasswordsExpiringInDays(int days) {
        LocalDateTime checkDate = LocalDateTime.now().plusDays(days);
        return userRepository.findByPasswordExpiresAtBetween(LocalDateTime.now(), checkDate);
    }

    /**
     * Znajdź użytkowników z wygasłymi hasłami
     */
    @Transactional(readOnly = true)
    public List<User> findUsersWithExpiredPasswords() {
        return userRepository.findByPasswordExpiresAtBefore(LocalDateTime.now());
    }

    /**
     * Przedłuż ważność hasła użytkownika o określoną liczbę dni
     */
    @Transactional
    public void extendPasswordExpiry(Long userId, int days) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.extendPasswordExpiry(days);
        userRepository.save(user);

        log.info("Przedłużono ważność hasła dla użytkownika {} o {} dni", user.getUsername(), days);
    }

    /**
     * Ustaw politykę wygaszania hasła dla użytkownika
     */
    @Transactional
    public void setPasswordExpiryPolicy(Long userId, Integer expiryDays) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setPasswordExpiryDays(expiryDays);

        // Jeśli ustawiamy nową politykę i hasło było ostatnio zmieniane, przelicz datę wygaśnięcia
        if (expiryDays != null && expiryDays > 0 && user.getPasswordChangedAt() != null) {
            user.setPasswordExpiresAt(user.getPasswordChangedAt().plusDays(expiryDays));
        } else if (expiryDays == null || expiryDays <= 0) {
            user.setPasswordExpiresAt(null); // Wyłącz wygaszanie
        }

        userRepository.save(user);

        log.info("Ustawiono politykę wygaszania hasła {} dni dla użytkownika {}",
                expiryDays, user.getUsername());
    }

    /**
     * Wymusz natychmiastową zmianę hasła z powodu wygaśnięcia
     */
    @Transactional
    public void forcePasswordChangeForExpiredUsers() {
        List<User> expiredUsers = findUsersWithExpiredPasswords();

        for (User user : expiredUsers) {
            user.setMustChangePassword(true);
            userRepository.save(user);
            log.warn("Wymuszono zmianę hasła dla użytkownika {} z powodu wygaśnięcia", user.getUsername());
        }

        if (!expiredUsers.isEmpty()) {
            log.info("Wymuszono zmianę hasła dla {} użytkowników z wygasłymi hasłami", expiredUsers.size());
        }
    }

    /**
     * Sprawdza czy użytkownik musi zmienić hasło (wymuszenie lub wygaśnięcie)
     */
    public boolean mustChangePasswordNow(Long userId) {
        return userRepository.findById(userId)
                .map(User::mustChangePasswordNow)
                .orElse(false);
    }

    /**
     * Sprawdza czy użytkownik musi zmienić hasło (wymuszenie lub wygaśnięcie) po nazwie użytkownika
     */
    public boolean mustChangePasswordNow(String username) {
        return userRepository.findByUsername(username)
                .map(User::mustChangePasswordNow)
                .orElse(false);
    }

    /**
     * Znajdź użytkownika po ID
     */
    @Transactional(readOnly = true)
    public User findById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }
}
