package com.mac.bry.validationsystem.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
        // Injecting properties manually for the unit test context
        ReflectionTestUtils.setField(rateLimiterService, "loginCapacity", 10);
        ReflectionTestUtils.setField(rateLimiterService, "loginRefillTokens", 10);
        ReflectionTestUtils.setField(rateLimiterService, "loginRefillDurationSeconds", 60);
        ReflectionTestUtils.setField(rateLimiterService, "auditCapacity", 100);
        ReflectionTestUtils.setField(rateLimiterService, "auditRefillTokens", 100);
        ReflectionTestUtils.setField(rateLimiterService, "auditRefillDurationSeconds", 60);
    }

    @Test
    void rateLimiterBlocksAfter10Attempts() {
        String ip = "192.168.1.100";

        // Pierwsze 10 prób powinno przejść
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiterService.allowLoginAttempt(ip), "Attempt " + (i + 1) + " should be allowed");
        }

        // 11 próba zostaje zablokowana
        assertFalse(rateLimiterService.allowLoginAttempt(ip), "Attempt 11 should be blocked");
    }

    @Test
    void rateLimiterAllowsAuditLogsUpTo100Attempts() {
        String ip = "10.0.0.1";

        // Sprawdzamy przy 100 logach audytu
        for (int i = 0; i < 100; i++) {
            assertTrue(rateLimiterService.allowAuditLog(ip));
        }

        // 101 wpis już utnie API
        assertFalse(rateLimiterService.allowAuditLog(ip));
    }

    @Test
    void rateLimiterResetsCorrectly() {
        String ip = "172.16.0.1";

        // Wyczerpujemy limit
        for (int i = 0; i < 10; i++) {
            rateLimiterService.allowLoginAttempt(ip);
        }
        assertFalse(rateLimiterService.allowLoginAttempt(ip));

        // Resetujemy
        rateLimiterService.resetLoginAttempts(ip);

        // Powinno znowu działać
        assertTrue(rateLimiterService.allowLoginAttempt(ip));
    }
}
