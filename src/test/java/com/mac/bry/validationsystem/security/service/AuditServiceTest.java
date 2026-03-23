package com.mac.bry.validationsystem.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mac.bry.validationsystem.security.AuditLog;
import com.mac.bry.validationsystem.security.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class AuditServiceTest {

    @Autowired
    private AuditService auditService;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @SpyBean
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Mockito.reset(auditLogRepository, objectMapper);
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    // =========================================================
    // Istniejące testy
    // =========================================================

    @Test
    void auditLogIsAsync() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            Thread.sleep(200); // Symulacja wolnej bazy
            latch.countDown();
            return invocation.getArgument(0);
        });

        long start = System.currentTimeMillis();
        auditService.logOperation("User", 1L, "UPDATE", "oldData", "newData");
        long duration = System.currentTimeMillis() - start;

        assertTrue(duration < 50, "Główny wątek zablokowany: " + duration + "ms");

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Async task did not complete in time");
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void shouldSerializeToJSON() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        var oldObj = new Object() {
            public String name = "Old Name";
            public int value = 10;
        };

        var newObj = new Object() {
            public String name = "New Name";
            public int value = 20;
        };

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog savedLog = invocation.getArgument(0);
            try {
                assertTrue(savedLog.getOldValueJson().has("name"));
                assertTrue(savedLog.getOldValueJson().has("value"));
                assertEquals("Old Name", savedLog.getOldValueJson().get("name").asText());

                assertTrue(savedLog.getNewValueJson().has("name"));
                assertEquals("New Name", savedLog.getNewValueJson().get("name").asText());
            } finally {
                latch.countDown();
            }
            return savedLog;
        });

        auditService.logOperation("TestEntity", 99L, "UPDATE", oldObj, newObj);

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Async task did not complete in time");
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    // =========================================================
    // Test 1: Brak uwierzytelnienia (użytkownik anonimowy)
    // =========================================================

    @Test
    void shouldHandleNullAuthentication() throws InterruptedException {
        // SecurityContextHolder.clearContext() w setUp() — brak Authentication
        CountDownLatch latch = new CountDownLatch(1);

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog saved = invocation.getArgument(0);
            try {
                assertNull(saved.getUserId(),
                        "userId powinien być null gdy użytkownik nie jest zalogowany");
                assertNull(saved.getUsername(),
                        "username powinien być null gdy użytkownik nie jest zalogowany");
            } finally {
                latch.countDown();
            }
            return saved;
        });

        auditService.logOperation("Device", 5L, "CREATE", null, "newData");

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Async task did not complete in time");
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    // =========================================================
    // Test 2: Null old i new value
    // =========================================================

    @Test
    void shouldHandleNullOldAndNewValues() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog saved = invocation.getArgument(0);
            try {
                assertNull(saved.getOldValueJson(),
                        "oldValueJson powinien być null gdy oldValueObj=null");
                assertNull(saved.getNewValueJson(),
                        "newValueJson powinien być null gdy newValueObj=null");
            } finally {
                latch.countDown();
            }
            return saved;
        });

        auditService.logOperation("Calibration", 42L, "DELETE", null, null);

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Async task did not complete in time");
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    // =========================================================
    // Test 3: Obiekt nie do serializacji → fallback do toString()
    // =========================================================

    @Test
    void shouldFallbackToStringWhenSerializationFails() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Object problematicObj = new Object() {
            @Override
            public String toString() {
                return "problematic-value";
            }
        };

        // Wymuszamy wyjątek Jackson dla konkretnej referencji obiektu
        doThrow(new JsonProcessingException("Simulated serialization failure") {})
                .when(objectMapper).writeValueAsString(same(problematicObj));

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog saved = invocation.getArgument(0);
            try {
                assertNotNull(saved.getOldValueJson(),
                        "Fallback JSON powinien zostać zapisany zamiast null");
                assertTrue(saved.getOldValueJson().has("fallback"),
                        "Fallback JSON powinien zawierać klucz 'fallback'");
                assertEquals("problematic-value",
                        saved.getOldValueJson().get("fallback").asText(),
                        "Wartość 'fallback' powinna być wynikiem toString()");
            } finally {
                latch.countDown();
            }
            return saved;
        });

        auditService.logOperation("TestEntity", 1L, "UPDATE", problematicObj, null);

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Async task did not complete in time");
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    // =========================================================
    // Test 4: Brak kontekstu webowego → "system-internal" / "internal"
    // =========================================================

    @Test
    void shouldUseSystemInternalFallbackWhenNoWebContext() throws InterruptedException {
        // RequestContextHolder.resetRequestAttributes() w setUp() — brak kontekstu HTTP
        CountDownLatch latch = new CountDownLatch(1);

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog saved = invocation.getArgument(0);
            try {
                assertEquals("system-internal", saved.getIpAddress(),
                        "IP powinien być 'system-internal' bez kontekstu HTTP");
                assertEquals("internal", saved.getSessionId(),
                        "SessionId powinien być 'internal' bez kontekstu HTTP");
            } finally {
                latch.countDown();
            }
            return saved;
        });

        auditService.logOperation("Validation", 10L, "APPROVE", null, null);

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Async task did not complete in time");
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    // =========================================================
    // Test 5: IP z nagłówka X-Forwarded-For (chain proxy)
    // =========================================================

    @Test
    void shouldExtractFirstIPFromXForwardedForHeader() throws InterruptedException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.1, 172.16.0.5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        CountDownLatch latch = new CountDownLatch(1);

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog saved = invocation.getArgument(0);
            try {
                assertEquals("10.0.0.1", saved.getIpAddress(),
                        "Powinien być wybrany pierwszy adres IP z X-Forwarded-For");
            } finally {
                latch.countDown();
            }
            return saved;
        });

        auditService.logOperation("Company", 1L, "UPDATE", null, null);

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Async task did not complete in time");
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    // =========================================================
    // Test 6: getLogsForEntity() — delegacja do repozytorium
    // =========================================================

    @Test
    void shouldDelegateGetLogsForEntityToRepository() {
        AuditLog log = new AuditLog();
        log.setEntityType("CoolingDevice");
        log.setEntityId(7L);
        log.setAction("UPDATE");

        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc("CoolingDevice", 7L))
                .thenReturn(List.of(log));

        List<AuditLog> result = auditService.getLogsForEntity("CoolingDevice", 7L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("CoolingDevice", result.get(0).getEntityType());
        assertEquals(7L, result.get(0).getEntityId());
        verify(auditLogRepository, times(1))
                .findByEntityTypeAndEntityIdOrderByTimestampDesc("CoolingDevice", 7L);
    }

    // =========================================================
    // Test 7: getRelatedLogsForUser() — delegacja do repozytorium
    // =========================================================

    @Test
    void shouldDelegateGetRelatedLogsForUserToRepository() {
        AuditLog logAsActor = new AuditLog();
        logAsActor.setUserId(3L);
        logAsActor.setEntityType("Validation");

        AuditLog logAsSubject = new AuditLog();
        logAsSubject.setEntityType("User");
        logAsSubject.setEntityId(3L);

        when(auditLogRepository.findAllRelatedToUser(3L))
                .thenReturn(List.of(logAsActor, logAsSubject));

        List<AuditLog> result = auditService.getRelatedLogsForUser(3L);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(auditLogRepository, times(1)).findAllRelatedToUser(3L);
    }

    // =========================================================
    // Test 8: Błąd zapisu DB — wyjątek połykany, żądanie HTTP nieuszkodzone
    // =========================================================

    @Test
    void shouldSwallowExceptionWhenRepositorySaveFails() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            latch.countDown();
            throw new RuntimeException("DB connection lost");
        });

        assertDoesNotThrow(
                () -> auditService.logOperation("Measurement", 99L, "UPLOAD", null, "data"),
                "Błąd bazy danych nie powinien propagować do wątku żądania HTTP"
        );

        boolean asyncFired = latch.await(2, TimeUnit.SECONDS);
        assertTrue(asyncFired, "Wątek asynchroniczny powinien się uruchomić mimo błędu");
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }
}
