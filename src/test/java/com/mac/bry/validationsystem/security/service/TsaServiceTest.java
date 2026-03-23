package com.mac.bry.validationsystem.security.service;

import com.itextpdf.signatures.ITSAClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests dla TsaService - TSA (Time Stamp Authority) functionality
 *
 * GMP COMPLIANCE TESTING:
 * - Walidacja TSA configuration zgodnie z FDA 21 CFR Part 11
 * - Test fallback behavior dla graceful degradation
 * - Weryfikacja audit trail dla TSA operations
 */
@ExtendWith(MockitoExtension.class)
class TsaServiceTest {

    private TsaService tsaService;

    @BeforeEach
    void setUp() {
        tsaService = new TsaService();
    }

    @Test
    void createTsaClient_WhenTsaDisabled_ShouldReturnNull() {
        // Given: TSA wyłączone w konfiguracji
        ReflectionTestUtils.setField(tsaService, "tsaEnabled", false);

        // When: Tworzenie TSA client
        ITSAClient result = tsaService.createTsaClient();

        // Then: Zwracany jest null
        assertNull(result, "TSA client powinien być null gdy TSA wyłączone");
    }

    @Test
    void createTsaClient_WhenTsaEnabledButNoUrl_WithFallbackEnabled_ShouldReturnNull() {
        // Given: TSA włączone ale brak URL, fallback włączony
        ReflectionTestUtils.setField(tsaService, "tsaEnabled", true);
        ReflectionTestUtils.setField(tsaService, "tsaUrl", "");
        ReflectionTestUtils.setField(tsaService, "fallbackEnabled", true);

        // When: Tworzenie TSA client
        ITSAClient result = tsaService.createTsaClient();

        // Then: Zwracany jest null (graceful degradation)
        assertNull(result, "TSA client powinien być null przy fallback enabled");
    }

    @Test
    void createTsaClient_WhenTsaEnabledButNoUrl_WithFallbackDisabled_ShouldThrowException() {
        // Given: TSA włączone ale brak URL, fallback wyłączony
        ReflectionTestUtils.setField(tsaService, "tsaEnabled", true);
        ReflectionTestUtils.setField(tsaService, "tsaUrl", "");
        ReflectionTestUtils.setField(tsaService, "fallbackEnabled", false);

        // When & Then: Powinna być rzucona exception
        TsaService.TsaException exception = assertThrows(
            TsaService.TsaException.class,
            () -> tsaService.createTsaClient(),
            "Powinna być rzucona TsaException gdy brak URL i fallback wyłączony"
        );

        assertTrue(exception.getMessage().contains("TSA URL nie jest skonfigurowany"));
    }

    @Test
    void createTsaClient_WhenValidConfiguration_WithoutCredentials_ShouldReturnClient() {
        // Given: Poprawna konfiguracja bez credentials
        ReflectionTestUtils.setField(tsaService, "tsaEnabled", true);
        ReflectionTestUtils.setField(tsaService, "tsaUrl", "http://timestamp.digicert.com");
        ReflectionTestUtils.setField(tsaService, "tsaUsername", "");
        ReflectionTestUtils.setField(tsaService, "tsaPassword", "");
        ReflectionTestUtils.setField(tsaService, "fallbackEnabled", true);
        ReflectionTestUtils.setField(tsaService, "timeoutMs", 30000);

        // When: Tworzenie TSA client
        ITSAClient result = tsaService.createTsaClient();

        // Then: TSA client zostaje utworzony
        assertNotNull(result, "TSA client powinien być utworzony dla poprawnej konfiguracji");
    }

    @Test
    void createTsaClient_WhenValidConfiguration_WithCredentials_ShouldReturnClient() {
        // Given: Poprawna konfiguracja z credentials
        ReflectionTestUtils.setField(tsaService, "tsaEnabled", true);
        ReflectionTestUtils.setField(tsaService, "tsaUrl", "http://timestamp.digicert.com");
        ReflectionTestUtils.setField(tsaService, "tsaUsername", "testuser");
        ReflectionTestUtils.setField(tsaService, "tsaPassword", "testpass");
        ReflectionTestUtils.setField(tsaService, "fallbackEnabled", true);
        ReflectionTestUtils.setField(tsaService, "timeoutMs", 30000);

        // When: Tworzenie TSA client
        ITSAClient result = tsaService.createTsaClient();

        // Then: TSA client zostaje utworzony
        assertNotNull(result, "TSA client powinien być utworzony dla konfiguracji z credentials");
    }

    @Test
    void verifyTsaAvailability_WhenTsaDisabled_ShouldReturnFalse() {
        // Given: TSA wyłączone
        ReflectionTestUtils.setField(tsaService, "tsaEnabled", false);

        // When: Weryfikacja availability
        boolean result = tsaService.verifyTsaAvailability();

        // Then: TSA niedostępne
        assertFalse(result, "TSA availability powinno być false gdy TSA wyłączone");
    }

    @Test
    void verifyTsaAvailability_WhenTsaEnabledAndConfigured_ShouldReturnTrue() {
        // Given: TSA włączone i skonfigurowane
        ReflectionTestUtils.setField(tsaService, "tsaEnabled", true);
        ReflectionTestUtils.setField(tsaService, "tsaUrl", "http://timestamp.digicert.com");
        ReflectionTestUtils.setField(tsaService, "fallbackEnabled", true);
        ReflectionTestUtils.setField(tsaService, "timeoutMs", 30000);

        // When: Weryfikacja availability
        boolean result = tsaService.verifyTsaAvailability();

        // Then: TSA dostępne
        assertTrue(result, "TSA availability powinno być true dla poprawnej konfiguracji");
    }

    @Test
    void getTsaStatus_ShouldReturnCompleteStatus() {
        // Given: Konfiguracja TSA
        ReflectionTestUtils.setField(tsaService, "tsaEnabled", true);
        ReflectionTestUtils.setField(tsaService, "tsaUrl", "http://timestamp.digicert.com");
        ReflectionTestUtils.setField(tsaService, "fallbackEnabled", true);
        ReflectionTestUtils.setField(tsaService, "timeoutMs", 30000);

        // When: Pobieranie statusu
        TsaService.TsaStatus status = tsaService.getTsaStatus();

        // Then: Status zawiera wszystkie informacje
        assertNotNull(status, "Status nie powinien być null");
        assertTrue(status.isEnabled(), "TSA powinno być enabled");
        assertTrue(status.isConfigured(), "TSA powinno być configured");
        assertTrue(status.isFallbackEnabled(), "Fallback powinien być enabled");
        assertEquals(30000, status.getTimeoutMs(), "Timeout powinien być 30000ms");
        assertNotNull(status.getUrl(), "URL nie powinien być null");
        assertNotNull(status.getLastCheck(), "LastCheck nie powinien być null");
    }

    @Test
    void getTimestamp_WhenTsaDisabled_ShouldReturnNull() {
        // Given: TSA wyłączone
        ReflectionTestUtils.setField(tsaService, "tsaEnabled", false);

        // When: Pobieranie timestamp
        TsaService.TsaTimestamp result = tsaService.getTimestamp("test-hash");

        // Then: Zwracany jest null
        assertNull(result, "Timestamp powinien być null gdy TSA wyłączone");
    }

    @Test
    void getTimestamp_WhenTsaEnabledAndConfigured_ShouldReturnTimestamp() {
        // Given: TSA włączone i skonfigurowane
        ReflectionTestUtils.setField(tsaService, "tsaEnabled", true);
        ReflectionTestUtils.setField(tsaService, "tsaUrl", "http://timestamp.digicert.com");
        ReflectionTestUtils.setField(tsaService, "fallbackEnabled", true);
        ReflectionTestUtils.setField(tsaService, "timeoutMs", 30000);

        // When: Pobieranie timestamp
        TsaService.TsaTimestamp result = tsaService.getTimestamp("test-document-hash");

        // Then: Timestamp zostaje utworzony
        assertNotNull(result, "Timestamp nie powinien być null");
        assertTrue(result.isSuccessful(), "Timestamp powinien być successful");
        assertEquals("test-document-hash", result.getDocumentHash(), "Document hash powinien być zachowany");
        assertNotNull(result.getTimestamp(), "Timestamp time nie powinien być null");
        assertNotNull(result.getTsaSerial(), "TSA serial nie powinien być null");
        assertNotNull(result.getTsaUrl(), "TSA URL nie powinien być null");
    }

    @Test
    void tsaStatus_ToString_ShouldContainKeyInfo() {
        // Given: TSA status
        TsaService.TsaStatus status = TsaService.TsaStatus.builder()
                .enabled(true)
                .configured(true)
                .available(true)
                .url("http://timestamp.digicert.com")
                .build();

        // When: toString
        String result = status.toString();

        // Then: Zawiera kluczowe informacje
        assertTrue(result.contains("enabled=true"), "ToString powinien zawierać enabled");
        assertTrue(result.contains("configured=true"), "ToString powinien zawierać configured");
        assertTrue(result.contains("available=true"), "ToString powinien zawierać available");
        assertTrue(result.contains("http://timestamp.digicert.com"), "ToString powinien zawierać URL");
    }

    @Test
    void tsaTimestamp_ToString_ShouldContainKeyInfo() {
        // Given: TSA timestamp
        TsaService.TsaTimestamp timestamp = TsaService.TsaTimestamp.builder()
                .tsaSerial("TSA123456")
                .tsaUrl("http://timestamp.digicert.com")
                .build();

        // When: toString
        String result = timestamp.toString();

        // Then: Zawiera kluczowe informacje
        assertTrue(result.contains("TSA123456"), "ToString powinien zawierać serial");
        assertTrue(result.contains("http://timestamp.digicert.com"), "ToString powinien zawierać URL");
    }

    @Test
    void tsaException_ShouldPreserveCauseAndMessage() {
        // Given: Exception z cause
        RuntimeException cause = new RuntimeException("Root cause");

        // When: Tworzenie TsaException
        TsaService.TsaException exception = new TsaService.TsaException("TSA error", cause);

        // Then: Message i cause są zachowane
        assertEquals("TSA error", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void tsaException_SimpleMessage_ShouldWork() {
        // When: Tworzenie TsaException z prostym message
        TsaService.TsaException exception = new TsaService.TsaException("Simple TSA error");

        // Then: Message jest zachowany
        assertEquals("Simple TSA error", exception.getMessage());
        assertNull(exception.getCause());
    }
}