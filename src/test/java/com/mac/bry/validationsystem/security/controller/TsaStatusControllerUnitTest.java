package com.mac.bry.validationsystem.security.controller;

import com.mac.bry.validationsystem.security.service.TsaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests dla TsaStatusController - TSA monitoring interface (direct unit test)
 *
 * GMP COMPLIANCE TESTING:
 * - Test business logic TSA status reporting
 * - Weryfikacja compliance status determination
 * - Test API response data structures
 */
@ExtendWith(MockitoExtension.class)
class TsaStatusControllerUnitTest {

    @Mock
    private TsaService tsaService;

    @Mock
    private Model model;

    @InjectMocks
    private TsaStatusController tsaStatusController;

    private TsaService.TsaStatus mockTsaStatus;

    @BeforeEach
    void setUp() {
        mockTsaStatus = TsaService.TsaStatus.builder()
                .enabled(true)
                .configured(true)
                .available(true)
                .fallbackEnabled(true)
                .url("http://timestamp.digicert.com")
                .timeoutMs(30000)
                .lastCheck(LocalDateTime.of(2026, 3, 7, 14, 30, 0))
                .build();
    }

    @Test
    void tsaStatusPage_ShouldReturnViewNameAndSetModelAttributes() {
        // Given: Mock TSA status
        when(tsaService.getTsaStatus()).thenReturn(mockTsaStatus);

        // When: Wywołanie metody kontrolera
        String viewName = tsaStatusController.tsaStatusPage(model);

        // Then: Zwracana jest poprawna nazwa widoku i ustawiane są atrybuty modelu
        assertEquals("admin/tsa-status", viewName);

        verify(tsaService).getTsaStatus();
        verify(model).addAttribute("tsaStatus", mockTsaStatus);
        verify(model).addAttribute(eq("currentTime"), any(LocalDateTime.class));
    }

    @Test
    void getTsaStatusJson_WithCompliantStatus_ShouldReturnCompliantResponse() {
        // Given: TSA fully compliant
        when(tsaService.getTsaStatus()).thenReturn(mockTsaStatus);

        // When: API call
        TsaStatusController.TsaStatusResponse response = tsaStatusController.getTsaStatusJson();

        // Then: Response zawiera compliant status
        assertNotNull(response);
        assertTrue(response.isEnabled());
        assertTrue(response.isConfigured());
        assertTrue(response.isAvailable());
        assertTrue(response.isFallbackEnabled());
        assertEquals("http://timestamp.digicert.com", response.getUrl());
        assertEquals(30000, response.getTimeoutMs());
        assertEquals("COMPLIANT", response.getComplianceStatus());

        // Recommendations powinny być pozytywne dla compliant status
        assertNotNull(response.getRecommendations());
        assertTrue(response.getRecommendations().length > 0);

        verify(tsaService).getTsaStatus();
    }

    @Test
    void getTsaStatusJson_WithDisabledTsa_ShouldReturnNonCompliant() {
        // Given: TSA wyłączone
        TsaService.TsaStatus disabledStatus = TsaService.TsaStatus.builder()
                .enabled(false)
                .configured(false)
                .available(false)
                .fallbackEnabled(true)
                .url(null)
                .timeoutMs(30000)
                .lastCheck(LocalDateTime.now())
                .build();

        when(tsaService.getTsaStatus()).thenReturn(disabledStatus);

        // When: API call
        TsaStatusController.TsaStatusResponse response = tsaStatusController.getTsaStatusJson();

        // Then: Non-compliant status
        assertNotNull(response);
        assertFalse(response.isEnabled());
        assertFalse(response.isConfigured());
        assertFalse(response.isAvailable());
        assertEquals("NON_COMPLIANT", response.getComplianceStatus());

        // Recommendations powinny zawierać kroki naprawcze
        assertNotNull(response.getRecommendations());
        assertTrue(response.getRecommendations().length > 0);
        assertTrue(String.join(" ", response.getRecommendations()).contains("Włącz TSA"));
    }

    @Test
    void getTsaStatusJson_WithEnabledButNotConfigured_ShouldReturnConfigurationRequired() {
        // Given: TSA włączone ale nie skonfigurowane
        TsaService.TsaStatus notConfiguredStatus = TsaService.TsaStatus.builder()
                .enabled(true)
                .configured(false)
                .available(false)
                .fallbackEnabled(true)
                .url("")
                .timeoutMs(30000)
                .lastCheck(LocalDateTime.now())
                .build();

        when(tsaService.getTsaStatus()).thenReturn(notConfiguredStatus);

        // When: API call
        TsaStatusController.TsaStatusResponse response = tsaStatusController.getTsaStatusJson();

        // Then: Configuration required status
        assertNotNull(response);
        assertTrue(response.isEnabled());
        assertFalse(response.isConfigured());
        assertEquals("CONFIGURATION_REQUIRED", response.getComplianceStatus());

        // Recommendations powinny zawierać kroki konfiguracji
        assertNotNull(response.getRecommendations());
        assertTrue(response.getRecommendations().length > 0);
        assertTrue(String.join(" ", response.getRecommendations()).contains("Skonfiguruj TSA URL"));
    }

    @Test
    void getTsaStatusJson_WithUnavailableButFallback_ShouldReturnDegraded() {
        // Given: TSA niedostępne ale fallback enabled
        TsaService.TsaStatus degradedStatus = TsaService.TsaStatus.builder()
                .enabled(true)
                .configured(true)
                .available(false)
                .fallbackEnabled(true)
                .url("http://timestamp.digicert.com")
                .timeoutMs(30000)
                .lastCheck(LocalDateTime.now())
                .build();

        when(tsaService.getTsaStatus()).thenReturn(degradedStatus);

        // When: API call
        TsaStatusController.TsaStatusResponse response = tsaStatusController.getTsaStatusJson();

        // Then: Degraded status
        assertNotNull(response);
        assertTrue(response.isEnabled());
        assertTrue(response.isConfigured());
        assertFalse(response.isAvailable());
        assertTrue(response.isFallbackEnabled());
        assertEquals("DEGRADED", response.getComplianceStatus());

        // Recommendations powinny zawierać kroki naprawy connectivity
        assertNotNull(response.getRecommendations());
        assertTrue(response.getRecommendations().length > 0);
        assertTrue(String.join(" ", response.getRecommendations()).contains("connectivność"));
    }

    @Test
    void getTsaStatusJson_WithUnavailableAndNoFallback_ShouldReturnNonCompliant() {
        // Given: TSA niedostępne i fallback wyłączony
        TsaService.TsaStatus criticalStatus = TsaService.TsaStatus.builder()
                .enabled(true)
                .configured(true)
                .available(false)
                .fallbackEnabled(false)
                .url("http://timestamp.digicert.com")
                .timeoutMs(30000)
                .lastCheck(LocalDateTime.now())
                .build();

        when(tsaService.getTsaStatus()).thenReturn(criticalStatus);

        // When: API call
        TsaStatusController.TsaStatusResponse response = tsaStatusController.getTsaStatusJson();

        // Then: Non-compliant status (critical - no fallback)
        assertNotNull(response);
        assertTrue(response.isEnabled());
        assertTrue(response.isConfigured());
        assertFalse(response.isAvailable());
        assertFalse(response.isFallbackEnabled());
        assertEquals("NON_COMPLIANT", response.getComplianceStatus());
    }

    @Test
    void testTsaConnection_WithSuccessfulTsa_ShouldReturnSuccess() {
        // Given: TSA dostępne
        when(tsaService.verifyTsaAvailability()).thenReturn(true);

        // When: Test call
        TsaStatusController.TsaTestResponse response = tsaStatusController.testTsaConnection();

        // Then: Successful test result
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertTrue(response.getDuration() >= 0);
        assertNull(response.getError());
        assertNotNull(response.getTimestamp());

        verify(tsaService).verifyTsaAvailability();
    }

    @Test
    void testTsaConnection_WithFailedTsa_ShouldReturnFailure() {
        // Given: TSA niedostępne
        when(tsaService.verifyTsaAvailability()).thenReturn(false);

        // When: Test call
        TsaStatusController.TsaTestResponse response = tsaStatusController.testTsaConnection();

        // Then: Failed test result
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getDuration() >= 0);
        assertEquals("TSA niedostępne lub wyłączone", response.getError());
        assertNotNull(response.getTimestamp());

        verify(tsaService).verifyTsaAvailability();
    }

    @Test
    void testTsaConnection_WithException_ShouldReturnError() {
        // Given: TSA service rzuca exception
        String errorMessage = "TSA connection timeout";
        when(tsaService.verifyTsaAvailability()).thenThrow(new RuntimeException(errorMessage));

        // When: Test call
        TsaStatusController.TsaTestResponse response = tsaStatusController.testTsaConnection();

        // Then: Error test result
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getDuration() >= 0);
        assertEquals(errorMessage, response.getError());
        assertNotNull(response.getTimestamp());

        verify(tsaService).verifyTsaAvailability();
    }

    @Test
    void tsaStatusResponse_BuilderPattern_ShouldWork() {
        // When: Tworzenie response przez builder
        TsaStatusController.TsaStatusResponse response = TsaStatusController.TsaStatusResponse.builder()
                .enabled(true)
                .configured(true)
                .available(false)
                .fallbackEnabled(true)
                .url("http://test-tsa.com")
                .timeoutMs(15000)
                .lastCheck(LocalDateTime.now())
                .complianceStatus("DEGRADED")
                .recommendations(new String[]{"Fix connectivity"})
                .build();

        // Then: Wszystkie wartości są poprawnie ustawione
        assertNotNull(response);
        assertTrue(response.isEnabled());
        assertTrue(response.isConfigured());
        assertFalse(response.isAvailable());
        assertTrue(response.isFallbackEnabled());
        assertEquals("http://test-tsa.com", response.getUrl());
        assertEquals(15000, response.getTimeoutMs());
        assertEquals("DEGRADED", response.getComplianceStatus());
        assertEquals(1, response.getRecommendations().length);
        assertEquals("Fix connectivity", response.getRecommendations()[0]);
    }

    @Test
    void tsaTestResponse_BuilderPattern_ShouldWork() {
        // When: Tworzenie test response przez builder
        LocalDateTime testTime = LocalDateTime.now();
        TsaStatusController.TsaTestResponse response = TsaStatusController.TsaTestResponse.builder()
                .success(false)
                .duration(5000L)
                .error("Connection failed")
                .timestamp(testTime)
                .build();

        // Then: Wszystkie wartości są poprawnie ustawione
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals(5000L, response.getDuration());
        assertEquals("Connection failed", response.getError());
        assertEquals(testTime, response.getTimestamp());
    }
}