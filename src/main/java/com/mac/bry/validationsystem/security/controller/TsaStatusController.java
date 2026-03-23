package com.mac.bry.validationsystem.security.controller;

import com.mac.bry.validationsystem.security.service.TsaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;

/**
 * Controller dla monitoringu statusu TSA (Time Stamp Authority)
 *
 * GMP COMPLIANCE:
 * - Monitoring TSA availability zgodnie z FDA 21 CFR Part 11
 * - Real-time status dla audit i compliance purposes
 * - Admin-only access dla bezpieczeństwa
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class TsaStatusController {

    private final TsaService tsaService;

    /**
     * Strona statusu TSA dla administratorów
     * Accessible tylko dla ADMIN role zgodnie z security policy
     */
    @GetMapping("/admin/tsa/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'ADMIN')")
    public String tsaStatusPage(Model model) {
        log.info("📊 Admin TSA Status: Dostęp do strony statusu TSA");

        TsaService.TsaStatus status = tsaService.getTsaStatus();
        model.addAttribute("tsaStatus", status);
        model.addAttribute("currentTime", LocalDateTime.now());

        return "admin/tsa-status";
    }

    /**
     * JSON endpoint dla status TSA (dla AJAX polling)
     * Używane przez frontend dla real-time monitoring
     */
    @GetMapping("/api/admin/tsa/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'ADMIN')")
    @ResponseBody
    public TsaStatusResponse getTsaStatusJson() {
        log.debug("🔍 API TSA Status: Pobieranie statusu JSON");

        TsaService.TsaStatus status = tsaService.getTsaStatus();

        return TsaStatusResponse.builder()
                .enabled(status.isEnabled())
                .configured(status.isConfigured())
                .available(status.isAvailable())
                .fallbackEnabled(status.isFallbackEnabled())
                .url(status.getUrl())
                .timeoutMs(status.getTimeoutMs())
                .lastCheck(status.getLastCheck())
                .complianceStatus(determineComplianceStatus(status))
                .recommendations(generateRecommendations(status))
                .build();
    }

    /**
     * Test endpoint dla weryfikacji TSA connectivity
     * Wykonuje aktywny test połączenia z TSA
     */
    @GetMapping("/api/admin/tsa/test")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'ADMIN')")
    @ResponseBody
    public TsaTestResponse testTsaConnection() {
        log.info("🧪 Admin TSA Test: Rozpoczęcie testu połączenia TSA");

        long startTime = System.currentTimeMillis();
        boolean success = false;
        String error = null;

        try {
            success = tsaService.verifyTsaAvailability();
            if (!success) {
                error = "TSA niedostępne lub wyłączone";
            }
        } catch (Exception e) {
            error = e.getMessage();
            log.warn("⚠️ TSA Test Error: {}", e.getMessage());
        }

        long duration = System.currentTimeMillis() - startTime;

        TsaTestResponse response = TsaTestResponse.builder()
                .success(success)
                .duration(duration)
                .error(error)
                .timestamp(LocalDateTime.now())
                .build();

        log.info("📊 TSA Test Result: success={}, duration={}ms", success, duration);

        return response;
    }

    /**
     * Określa compliance status na podstawie TSA configuration
     */
    private String determineComplianceStatus(TsaService.TsaStatus status) {
        if (!status.isEnabled()) {
            return "NON_COMPLIANT";
        }

        if (!status.isConfigured()) {
            return "CONFIGURATION_REQUIRED";
        }

        if (!status.isAvailable()) {
            return status.isFallbackEnabled() ? "DEGRADED" : "NON_COMPLIANT";
        }

        return "COMPLIANT";
    }

    /**
     * Generuje rekomendacje na podstawie stanu TSA
     */
    private String[] generateRecommendations(TsaService.TsaStatus status) {
        if (!status.isEnabled()) {
            return new String[]{
                "Włącz TSA w konfiguracji (app.tsa.enabled=true)",
                "Skonfiguruj TSA URL dla production compliance",
                "TSA jest wymagane dla FDA 21 CFR Part 11 compliance"
            };
        }

        if (!status.isConfigured()) {
            return new String[]{
                "Skonfiguruj TSA URL w application.properties",
                "Dodaj credentials jeśli TSA wymaga uwierzytelniania",
                "Ustaw odpowiedni timeout dla środowiska production"
            };
        }

        if (!status.isAvailable()) {
            return new String[]{
                "Sprawdź connectivność z TSA server",
                "Zweryfikuj credentials TSA jeśli używane",
                "Rozważ backup TSA provider dla high availability"
            };
        }

        return new String[]{
            "TSA skonfigurowane i działające poprawnie",
            "Monitor TSA availability regularnie",
            "Backup TSA provider zalecany dla production"
        };
    }

    /**
     * Response class dla TSA status API
     */
    @lombok.Builder
    @lombok.Data
    public static class TsaStatusResponse {
        private boolean enabled;
        private boolean configured;
        private boolean available;
        private boolean fallbackEnabled;
        private String url;
        private int timeoutMs;
        private LocalDateTime lastCheck;
        private String complianceStatus;
        private String[] recommendations;
    }

    /**
     * Response class dla TSA test API
     */
    @lombok.Builder
    @lombok.Data
    public static class TsaTestResponse {
        private boolean success;
        private long duration;
        private String error;
        private LocalDateTime timestamp;
    }
}