package com.mac.bry.validationsystem.security.service;

import com.mac.bry.validationsystem.security.LoginHistory;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.LoginHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginHistoryService {

    private final LoginHistoryRepository loginHistoryRepository;
    private final RateLimiterService rateLimiterService;
    private final UserService userService;

    @Transactional
    public void recordLogin(User user, boolean success, String ipAddress, String userAgent, String failureReason) {
        // Zapis do BD
        LoginHistory history = new LoginHistory();
        if (user != null) {
            history.setUserId(user.getId());
            history.setUsername(user.getUsername());
        }
        history.setLoginTime(LocalDateTime.now());
        history.setIpAddress(ipAddress);
        history.setUserAgent(userAgent);
        history.setSuccess(success);
        history.setFailureReason(failureReason);

        loginHistoryRepository.save(history);

        // Operacje z powiązaniem na użytkowniku (RateLimit)
        if (success) {
            rateLimiterService.resetLoginAttempts(ipAddress);
            if (user != null) {
                userService.resetFailedLoginAttempts(user.getId());
            }
        } else {
            if (user != null) {
                userService.recordFailedLoginAttempt(user.getId());
            }
        }
    }

    @Transactional
    public void recordAnonymousFailedLogin(String username, String ipAddress, String userAgent, String failureReason) {
        // Tabela obecnie wymaga `userId`, więc dla logowań kompletnie anonimowych
        // gdzie użytkownik nie istnieje, możemy wstawić `null`
        LoginHistory history = new LoginHistory();
        history.setUsername(username);
        history.setLoginTime(LocalDateTime.now());
        history.setIpAddress(ipAddress);
        history.setUserAgent(userAgent);
        history.setSuccess(false);
        history.setFailureReason(failureReason);

        try {
            // Jeśli baza na to pozwala:
            loginHistoryRepository.save(history);
        } catch (Exception e) {
            log.warn("Could not save anonymous failed login. Error: {}", e.getMessage());
        }
    }
}
