package com.mac.bry.validationsystem.security.service;

import com.mac.bry.validationsystem.security.LoginHistory;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.LoginHistoryRepository;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.context.annotation.Import;
import com.mac.bry.validationsystem.config.TestMailConfig;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestMailConfig.class)
class SecurityIntegrationTest {

    @Autowired
    private LoginHistoryService loginHistoryService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        loginHistoryRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User();
        testUser.setUsername("integration_test_user");
        testUser.setPassword("password123");
        testUser.setEmail("itest@example.com");
        testUser.setFirstName("Int");
        testUser.setLastName("Test");
        testUser.setEnabled(true);
        testUser.setLocked(false);
        testUser.setFailedLoginAttempts(0);

        testUser = userRepository.save(testUser);
        rateLimiterService.resetLoginAttempts("10.1.1.1");
    }

    @Test
    @Transactional
    void loginFlowWithAuditAndHistory() {
        String ip = "10.1.1.1";

        // Symulacja Spring Security Authentication Filter z rate limitingiem
        assertTrue(rateLimiterService.allowLoginAttempt(ip));

        // 1. Sukces logowania
        loginHistoryService.recordLogin(testUser, true, ip, "Mozilla/5.0", null);

        // Znajdź historię logowania w bazie
        List<LoginHistory> histories = loginHistoryRepository.findAll();
        assertEquals(1, histories.size());
        assertEquals(ip, histories.get(0).getIpAddress());
        assertTrue(histories.get(0).isSuccess());

        // 2. Weryfikacja konta (powinno być nadal odblokowane)
        User dbUser = userRepository.findById(testUser.getId()).get();
        assertFalse(dbUser.isLocked());
        assertEquals(0, dbUser.getFailedLoginAttempts());
    }

    @Test
    @Transactional
    void accountLocksAfter5FailedAttempts() {
        String ip = "10.1.1.2";

        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiterService.allowLoginAttempt(ip));
            loginHistoryService.recordLogin(testUser, false, ip, "Bot/1.0", "Bad credentials");
        }

        User dbUser = userRepository.findById(testUser.getId()).get();
        assertTrue(dbUser.isLocked());
        assertNotNull(dbUser.getLockedUntil());
        assertEquals(5, dbUser.getFailedLoginAttempts());
    }
}
