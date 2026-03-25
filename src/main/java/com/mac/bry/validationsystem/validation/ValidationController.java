package com.mac.bry.validationsystem.validation;

import com.mac.bry.validationsystem.audit.EnversRevisionService;
import com.mac.bry.validationsystem.audit.FieldDiffDto;
import com.mac.bry.validationsystem.audit.RevisionInfoDto;
import com.mac.bry.validationsystem.deviation.*;
import com.mac.bry.validationsystem.measurement.RecorderPosition;
import com.mac.bry.validationsystem.stats.ValidationSummaryStatsService;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import com.mac.bry.validationsystem.security.service.AuditService;
import com.mac.bry.validationsystem.security.util.UrlValidator;
import com.mac.bry.validationsystem.company.CompanyService;
import com.mac.bry.validationsystem.department.DepartmentService;
import com.mac.bry.validationsystem.laboratory.LaboratoryService;
import com.mac.bry.validationsystem.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kontroler do zarządzania walidacjami
 */
@Slf4j
@Controller
@RequestMapping("/validations")
@RequiredArgsConstructor
public class ValidationController {

    private final ValidationService validationService;
    private final ValidationDocumentService documentService;
    private final ValidationPackageService packageService;
    private final ValidationRepository validationRepository;
    private final ValidationVisualizationService visualizationService;
    private final AuditService auditService;
    private final ValidationDocumentTrackingService trackingService;
    private final ValidationSigningService signingService;
    private final ValidationSignatureRepository signatureRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final EnversRevisionService enversRevisionService;
    private final ValidationSummaryStatsService summaryStatsService;
    private final DeviationDetectionService deviationDetectionService;
    private final DeviationEventRepository deviationEventRepository;
    private final DeviationAnalysisRepository deviationAnalysisRepository;
    private final ValidationProtocolPdfService protocolPdfService;
    private final RecorderSummaryPdfService recorderSummaryPdfService;
    private final SecurityService securityService;
    private final DepartmentService departmentService;
    private final CompanyService companyService;
    private final LaboratoryService laboratoryService;

    /**
     * Lista wszystkich walidacji z opcjonalnym filtrowaniem po statusie
     */
    @GetMapping
    public String listValidations(
            @RequestParam(required = false) ValidationStatus status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long laboratoryId,
            Model model) {
        log.debug("Wyświetlanie listy walidacji (filtry: status={}, company={}, dept={}, lab={})",
                status, companyId, departmentId, laboratoryId);

        List<Validation> validations = validationService.getAllAccessibleValidations(
                status, companyId, departmentId, laboratoryId);

        model.addAttribute("validations", validations);
        model.addAttribute("allStatuses", ValidationStatus.values());
        model.addAttribute("selectedStatus", status);

        // Filtry lokalizacji
        model.addAttribute("companyId", companyId);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("laboratoryId", laboratoryId);

        // Dane do dropdownów filtrów
        model.addAttribute("companies", companyService.getAllowedCompanies(securityService.getAllowedCompanyIds()));

        if (companyId != null) {
            model.addAttribute("departments", departmentService.getDepartmentsByCompany(companyId));
        } else {
            model.addAttribute("departments", departmentService.getAllowedDepartments(
                    securityService.getDepartmentIdsWithImplicitAccess(), securityService.getAllowedCompanyIds()));
        }

        if (departmentId != null) {
            model.addAttribute("laboratories", laboratoryService.getLaboratoriesByDepartment(departmentId));
        }

        return "validation/list";
    }

    /**
     * Tworzy nową walidację z zaznaczonych serii pomiarowych
     */
    @PreAuthorize("@securityService.canManageAllMeasurementSeries(#seriesIds)")
    @PostMapping("/create")
    public String createValidation(
            @RequestParam(value = "seriesIds", required = false) List<Long> seriesIds,
            @RequestParam(value = "controlSensorPosition", required = false) String controlSensorPositionStr,
            @RequestParam(value = "deviceLoadState", required = false) String deviceLoadStateStr,
            RedirectAttributes redirectAttributes) {

        log.info("Otrzymano żądanie utworzenia walidacji z {} seriami, czujnik kontrolujący: {}, stan urządzenia: {}",
                seriesIds != null ? seriesIds.size() : 0, controlSensorPositionStr, deviceLoadStateStr);

        if (seriesIds == null || seriesIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "Należy zaznaczyć co najmniej jedną serię pomiarową");
            return "redirect:/measurements";
        }

        RecorderPosition controlSensorPosition = null;
        if (controlSensorPositionStr != null && !controlSensorPositionStr.isBlank()) {
            try {
                controlSensorPosition = RecorderPosition.valueOf(controlSensorPositionStr);
            } catch (IllegalArgumentException e) {
                log.warn("Nieznana pozycja czujnika kontrolującego: {}", controlSensorPositionStr);
            }
        }

        DeviceLoadState deviceLoadState = null;
        if (deviceLoadStateStr != null && !deviceLoadStateStr.isBlank()) {
            try {
                deviceLoadState = DeviceLoadState.valueOf(deviceLoadStateStr);
            } catch (IllegalArgumentException e) {
                log.warn("Nieznany stan załadowania urządzenia: {}", deviceLoadStateStr);
                redirectAttributes.addFlashAttribute("error",
                        "Nieprawidłowy stan urządzenia. Wybierz pełne, puste lub częściowo załadowane.");
                return "redirect:/measurements";
            }
        }

        try {
            Validation validation = validationService.createValidation(seriesIds, controlSensorPosition,
                    deviceLoadState);

            // Oblicz statystyki zbiorcze (Tabela A) zaraz po utworzeniu
            try {
                summaryStatsService.calculateAndSave(validation.getId());
                log.info("Obliczono statystyki zbiorcze dla walidacji ID: {}", validation.getId());
            } catch (Exception statsEx) {
                log.warn("Nie udało się obliczyć statystyk zbiorczych dla walidacji {}: {}",
                        validation.getId(), statsEx.getMessage());
            }

            // Wykryj naruszenia temperaturowe (Tabela G)
            try {
                var deviations = deviationDetectionService.detectAndSave(validation.getId());
                log.info("Wykryto {} naruszeń temperaturowych dla walidacji ID: {}",
                        deviations.size(), validation.getId());
            } catch (Exception devEx) {
                log.warn("Nie udało się wykryć naruszeń dla walidacji {}: {}",
                        validation.getId(), devEx.getMessage());
            }

            // Rejestracja audytu
            auditService.logOperation("Validation", validation.getId(), "CREATE", null,
                    Map.of("status", validation.getStatus()));

            redirectAttributes.addFlashAttribute("success",
                    String.format("Utworzono walidację z %d seriami pomiarowymi (ID: %d)",
                            validation.getMeasurementSeries().size(), validation.getId()));

            return "redirect:/measurements";

        } catch (Exception e) {
            log.error("Błąd podczas tworzenia walidacji: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Błąd podczas tworzenia walidacji: " + e.getMessage());
            return "redirect:/measurements";
        }
    }

    /**
     * Pobiera wypełniony dokument Word dla walidacji
     */
    @PreAuthorize("@securityService.canManageValidation(#id)")
    @GetMapping("/{id}/schemat")
    public ResponseEntity<byte[]> downloadValidationDocument(@PathVariable Long id) {
        log.info("Pobieranie dokumentu walidacji dla ID: {}", id);

        try {
            Validation validation = validationRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono walidacji o ID: " + id));

            byte[] documentBytes = documentService.generateValidationDocument(validation);

            // Rejestracja generacji dokumentu
            try {
                trackingService.trackGeneration(validation, DocumentType.RAPORT_WORD, null);
            } catch (Exception ex) {
                log.warn("Nie udało się zarejestrować generacji dokumentu Word: {}", ex.getMessage());
            }

            // Przygotuj nazwę pliku (ASCII-safe dla kompatybilności)
            String filename = String.format("Schemat_walidacji_%d_%s.docx",
                    id,
                    validation.getCreatedDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            headers.setContentLength(documentBytes.length);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

            return new ResponseEntity<>(documentBytes, headers, org.springframework.http.HttpStatus.OK);

        } catch (Exception e) {
            log.error("Błąd podczas generowania dokumentu: {}", e.getMessage(), e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Pobiera paczkę ZIP z wszystkimi dokumentami walidacji
     */
    @PreAuthorize("@securityService.canManageValidation(#id)")
    @GetMapping("/{id}/package")
    public ResponseEntity<byte[]> downloadValidationPackage(@PathVariable Long id) {
        log.info("Pobieranie paczki dokumentów walidacji dla ID: {}", id);

        try {
            Validation validation = validationRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono walidacji o ID: " + id));

            byte[] packageBytes = packageService.generateValidationPackage(validation);

            // Rejestracja generacji paczki ZIP (hash sha256 pierwszej wersji)
            try {
                trackingService.trackGeneration(validation, DocumentType.ZIP_PACKAGE, packageBytes);
            } catch (Exception ex) {
                log.warn("Nie udało się zarejestrować generacji paczki ZIP: {}", ex.getMessage());
            }

            // Przygotuj nazwę pliku (ASCII-safe)
            String filename = String.format("Walidacja_%d_%s.zip",
                    id,
                    validation.getCreatedDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.parseMediaType("application/zip"));
            headers.setContentLength(packageBytes.length);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

            return new ResponseEntity<>(packageBytes, headers, org.springframework.http.HttpStatus.OK);

        } catch (Exception e) {
            log.error("Błąd podczas generowania paczki dokumentów: {}", e.getMessage(), e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generuje i serwuje animację 3D rozkładu temperatur
     */
    @PreAuthorize("@securityService.canManageValidation(#id)")
    @GetMapping("/{id}/animation")
    public ResponseEntity<byte[]> downloadAnimation(@PathVariable Long id) {
        log.info("Generowanie animacji 3D dla walidacji ID: {}", id);

        try {
            Validation validation = validationRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono walidacji o ID: " + id));

            byte[] htmlBytes = visualizationService.generate3DAnimation(validation);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.TEXT_HTML);
            headers.setContentLength(htmlBytes.length);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"animation.html\"");

            return new ResponseEntity<>(htmlBytes, headers, org.springframework.http.HttpStatus.OK);

        } catch (Exception e) {
            log.error("Błąd podczas generowania animacji 3D: {}", e.getMessage(), e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Wyświetla szczegóły walidacji
     */
    @PreAuthorize("@securityService.canManageValidation(#id)")
    @GetMapping("/{id}")
    public String validationDetails(@PathVariable Long id,
            @RequestParam(value = "from", required = false) String from,
            org.springframework.ui.Model model,
            RedirectAttributes redirectAttributes) {
        log.info("Wyświetlanie szczegółów walidacji ID: {}", id);

        return validationRepository.findById(id)
                .map(validation -> {
                    model.addAttribute("validation", validation);
                    model.addAttribute("allStatuses", ValidationStatus.values());
                    model.addAttribute("returnUrl", from != null ? from : "/validations");
                    model.addAttribute("documents", trackingService.getDocumentsForValidation(id));
                    model.addAttribute("signature", signatureRepository.findByValidationId(id).orElse(null));
                    // Statystyki zbiorcze Tabela A (obliczone przy tworzeniu lub on-demand)
                    var foundStats = summaryStatsService.findByValidationId(id);
                    if (foundStats.isEmpty()) {
                        try {
                            summaryStatsService.calculateAndSave(validation.getId());
                            foundStats = summaryStatsService.findByValidationId(id);
                            log.info("Obliczono statystyki zbiorcze on-demand dla walidacji ID: {}", id);
                        } catch (Exception statsEx) {
                            log.warn("Nie udało się obliczyć statystyk zbiorczych on-demand dla walidacji {}: {}",
                                    id, statsEx.getMessage());
                            model.addAttribute("summaryStatsError", true);
                            model.addAttribute("summaryStatsErrorMessage", statsEx.getMessage());
                        }
                    }
                    foundStats.ifPresent(stats -> model.addAttribute("summaryStats", stats));
                    // Tabela G — Analiza odchyleń
                    model.addAttribute("deviations",
                            deviationDetectionService.findByValidationId(id));
                    return "validation/details";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Nie znaleziono walidacji");
                    return "redirect:/measurements";
                });
    }

    @PreAuthorize("@securityService.canManageValidation(#id)")
    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
            @RequestParam ValidationStatus status,
            @RequestParam String password,
            @RequestParam(value = "returnUrl", required = false) String returnUrl,
            Authentication auth,
            RedirectAttributes redirectAttributes) {
        log.info("Aktualizacja statusu walidacji {} na {}", id, status);

        String redirectTarget = (returnUrl != null && !returnUrl.isEmpty() && UrlValidator.isSafeInternalUrl(returnUrl))
                ? "redirect:" + returnUrl
                : "redirect:/validations/" + id;

        // Weryfikacja hasła użytkownika
        var user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            log.warn("Nieprawidłowe hasło przy zmianie statusu walidacji {} przez {}", id, auth.getName());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Nieprawidłowe hasło. Status walidacji nie został zmieniony.");
            return redirectTarget;
        }

        Validation validation = validationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Walidacja nie znaleziona: " + id));

        if (validation.getStatus() == ValidationStatus.COMPLETED) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Podpisana walidacja nie może zmieniać statusu.");
            return redirectTarget;
        }
        if (status == ValidationStatus.COMPLETED) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Status 'Zakończona' można nadać tylko przez podpisanie elektroniczne.");
            return redirectTarget;
        }

        ValidationStatus oldStatus = validation.getStatus();
        validation.setStatus(status);
        validationRepository.save(validation);

        // Rejestracja audytu po pomyślnym zaktualizowaniu statusu
        auditService.logOperation("Validation", validation.getId(), "UPDATE_STATUS",
                Map.of("status", oldStatus),
                Map.of("status", status));

        redirectAttributes.addFlashAttribute("successMessage",
                "Status walidacji został zmieniony na: " + status.getDisplayName());

        return redirectTarget;
    }

    /**
     * Podpisuje walidację elektronicznie — weryfikuje hasło, generuje podpisany
     * PDF,
     * zmienia status na COMPLETED.
     */
    @PreAuthorize("@securityService.canManageValidation(#id)")
    @PostMapping("/{id}/sign")
    public String signValidation(@PathVariable Long id,
            @RequestParam String password,
            @RequestParam String signingIntent,
            Authentication auth,
            RedirectAttributes redirectAttributes) {
        log.info("Próba podpisania walidacji {} przez {}", id, auth.getName());
        try {
            signingService.signValidation(id, auth.getName(), password, signingIntent);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Walidacja została podpisana elektronicznie i zakończona.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Błąd podpisu: " + e.getMessage());
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Błąd techniczny podczas podpisywania walidacji {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Błąd techniczny podczas podpisywania. Spróbuj ponownie.");
        }
        return "redirect:/validations/" + id;
    }

    /**
     * Envers: lista rewizji dla walidacji (AJAX)
     */
    @PreAuthorize("@securityService.canManageValidation(#id)")
    @GetMapping("/{id}/history")
    @ResponseBody
    public List<RevisionInfoDto> getValidationRevisions(@PathVariable Long id) {
        log.debug("Pobieranie historii Envers dla walidacji ID: {}", id);
        return enversRevisionService.getRevisionHistory(Validation.class, id);
    }

    /**
     * Envers: diff pól między rewizją N a N-1 (AJAX)
     */
    @PreAuthorize("@securityService.canManageValidation(#id)")
    @GetMapping("/{id}/history/{revNum}")
    @ResponseBody
    public List<FieldDiffDto> getValidationRevisionDiff(@PathVariable Long id, @PathVariable int revNum) {
        log.debug("Pobieranie diff rewizji {} dla walidacji ID: {}", revNum, id);
        Map<String, String> labels = Map.of(
                "validationPlanNumber", "Numer planu walidacji",
                "status", "Status",
                "averageDeviceTemperature", "Średnia temp. urządzenia (\u00b0C)",
                "controlSensorPosition", "Pozycja czujnika kontrolującego",
                "coolingDevice.name", "Urządzenie");
        return enversRevisionService.getDetailedDiff(Validation.class, id, revNum, labels);
    }

    /**
     * Pobiera podpisany PDF walidacji (dostępny po złożeniu podpisu
     * elektronicznego).
     */
    @PreAuthorize("@securityService.canManageValidation(#id)")
    @GetMapping("/{id}/signed-pdf")
    public ResponseEntity<byte[]> downloadSignedPdf(@PathVariable Long id) {
        log.info("Pobieranie podpisanego PDF dla walidacji ID: {}", id);
        try {
            ValidationSignature sig = signatureRepository.findByValidationId(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Brak podpisanego dokumentu dla walidacji ID: " + id));

            byte[] bytes = Files.readAllBytes(Path.of(sig.getSignedPdfPath()));

            String filename = "validation_" + id + "_signed.pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Błąd podczas pobierania podpisanego PDF dla walidacji {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generuje kompletny Protokół Walidacji w formacie PDF
     * (metadane + sekcje A–G + wnioski).
     */
    @PreAuthorize("@securityService.canManageValidation(#id)")
    @GetMapping("/{id}/protocol-pdf")
    public ResponseEntity<byte[]> downloadProtocolPdf(@PathVariable Long id) {
        log.info("Generowanie Protokołu Walidacji PDF dla ID: {}", id);

        try {
            Validation validation = validationRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Nie znaleziono walidacji o ID: " + id));

            var stats = summaryStatsService.findByValidationId(id).orElse(null);
            var deviations = deviationDetectionService.findByValidationId(id);

            byte[] pdfBytes = protocolPdfService.generateProtocolPdf(validation, stats, deviations);

            String filename = String.format("Protokol_walidacji_%d_%s.pdf",
                    id,
                    validation.getCreatedDate().format(
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Błąd podczas generowania Protokołu Walidacji PDF: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generuje raport PDF z zestawieniem wszystkich rejestratorów użytych w
     * walidacji
     * (dane kalibracji, położenie, temperatury).
     */
    @PreAuthorize("@securityService.canManageValidation(#id)")
    @GetMapping("/{id}/recorders-pdf")
    public ResponseEntity<byte[]> downloadRecordersPdf(@PathVariable Long id) {
        log.info("Generowanie raportu rejestratorów PDF dla ID: {}", id);

        try {
            Validation validation = validationRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Nie znaleziono walidacji o ID: " + id));

            byte[] pdfBytes = recorderSummaryPdfService.generateRecorderSummaryPdf(validation);

            String filename = String.format("Rejestratory_walidacja_%d_%s.pdf",
                    id,
                    validation.getCreatedDate().format(
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Błąd podczas generowania raportu rejestratorów PDF: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // =========================================================================
    // TABELA G — Deviation Analysis AJAX
    // =========================================================================

    /**
     * Zapisuje / aktualizuje analizę odchylenia (root cause, impact, CAPA).
     */
    @PreAuthorize("@securityService.canManageValidation(#id)")
    @PostMapping("/{id}/deviations/{eventId}/analysis")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveDeviationAnalysis(
            @PathVariable Long id,
            @PathVariable Long eventId,
            @RequestParam String rootCause,
            @RequestParam String productImpact,
            @RequestParam String correctiveAction,
            Authentication auth) {

        log.info("Zapis analizy odchylenia eventId={} dla walidacji {}", eventId, id);

        DeviationEvent event = deviationEventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Nie znaleziono naruszenia ID: " + eventId));

        // Waliduj przynależność do walidacji
        if (!event.getValidation().getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Naruszenie nie należy do tej walidacji");
        }

        DeviationAnalysis analysis = deviationAnalysisRepository
                .findByDeviationEventId(eventId)
                .orElse(DeviationAnalysis.builder()
                        .deviationEvent(event)
                        .build());

        analysis.setRootCause(rootCause);
        analysis.setProductImpact(productImpact);
        analysis.setCorrectiveAction(correctiveAction);
        analysis.setAnalyzedBy(auth.getName());
        analysis.setAnalyzedAt(java.time.LocalDateTime.now());

        deviationAnalysisRepository.save(analysis);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "analyzedBy", auth.getName(),
                "analyzedAt", analysis.getAnalyzedAt().toString()));
    }
}
