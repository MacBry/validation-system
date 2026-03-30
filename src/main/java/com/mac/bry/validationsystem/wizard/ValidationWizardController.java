package com.mac.bry.validationsystem.wizard;

import com.mac.bry.validationsystem.device.CoolingDeviceService;
import com.mac.bry.validationsystem.measurement.MeasurementSeriesDto;
import com.mac.bry.validationsystem.measurement.MeasurementSeriesService;
import com.mac.bry.validationsystem.measurement.RecorderPosition;
import com.mac.bry.validationsystem.measurement.UploadResult;
import com.mac.bry.validationsystem.security.service.AuditService;
import com.mac.bry.validationsystem.validation.DeviceLoadState;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.wizard.criteria.AcceptanceCriterionField;
import com.mac.bry.validationsystem.wizard.criteria.CustomAcceptanceCriterionDto;
import com.mac.bry.validationsystem.wizard.dto.DeviationProceduresDto;
import com.mac.bry.validationsystem.wizard.dto.MappingStatusDto;
import com.mac.bry.validationsystem.wizard.dto.PlanCriteriaDto;
import com.mac.bry.validationsystem.wizard.dto.ValidationPlanDataDto;
import com.mac.bry.validationsystem.wizard.service.ValidationDraftService;
import com.mac.bry.validationsystem.wizard.service.ValidationPlanDataService;
import com.mac.bry.validationsystem.wizard.service.WizardFinalizationService;
import com.mac.bry.validationsystem.wizard.oq.OqTestResult;
import com.mac.bry.validationsystem.wizard.oq.OqTestService;
import com.mac.bry.validationsystem.wizard.pq.PqChecklistItem;
import com.mac.bry.validationsystem.wizard.pq.PqChecklistService;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Controller for the multi-step validation wizard.
 *
 * <p>
 * Supports two wizard flows:
 * - OQ/PQ/MAPPING: 9-step wizard (steps 1-9)
 * - PERIODIC_REVALIDATION: 13-step wizard (steps 1-13) with QA approval barrier at step 8-9
 * </p>
 *
 * <p>
 * Standard Endpoints:
 * - GET /wizard/ - List active drafts
 * - GET /wizard/new - Create new draft
 * - GET /wizard/{id}/step/{n} - Show step n
 * - POST /wizard/{id}/step/{n} - Save step n
 * - POST /wizard/{id}/back/{n} - Navigate back to step n
 * - GET /wizard/{id}/preview-pdf - Preview PDF in browser
 * - POST /wizard/{id}/finalize - Finalize and sign (step 9 or 13)
 * - POST /wizard/{id}/abandon - Abandon draft
 * </p>
 *
 * <p>
 * PERIODIC_REVALIDATION Endpoints:
 * - POST /wizard/{id}/api/step3-pr - Save plan details
 * - POST /wizard/{id}/api/step4-pr - Save mapping status acknowledgement
 * - POST /wizard/{id}/api/step5-pr - Save acceptance criteria and load state
 * - POST /wizard/{id}/api/step7-pr - Save deviation (CAPA) procedures
 * - POST /wizard/{id}/finalize-plan - Technik signs plan (step 8)
 * - GET  /wizard/{id}/awaiting-approval - Status page for technik
 * - GET  /wizard/{id}/plan-review - QA review page (ROLE_QA)
 * - POST /wizard/{id}/plan-approve - QA approves plan (ROLE_QA)
 * - POST /wizard/{id}/plan-reject - QA rejects plan with reason (ROLE_QA)
 * </p>
 */
@Slf4j
@Controller
@RequestMapping("/wizard")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ValidationWizardController {

    private final ValidationDraftService draftService;
    private final WizardFinalizationService finalizationService;
    private final CoolingDeviceService coolingDeviceService;
    private final OqTestService oqTestService;
    private final PqChecklistService pqChecklistService;
    private final MeasurementSeriesService seriesService;
    private final AuditService auditService;
    private final ValidationPlanDataService planDataService;
    private final MappingStatusService mappingStatusService;
    private final com.mac.bry.validationsystem.wizard.pdf.ValidationPlanPdfService planPdfService;

    // ========================================================
    // Private helpers
    // ========================================================

    /**
     * Returns true when the draft uses the PERIODIC_REVALIDATION procedure type.
     *
     * @param draft the wizard draft to check
     * @return true if procedure type is PERIODIC_REVALIDATION
     */
    private boolean isPeriodicRevalidation(ValidationDraft draft) {
        return draft.getProcedureType() == ValidationProcedureType.PERIODIC_REVALIDATION;
    }

    /**
     * Prepare model attributes for PERIODIC_REVALIDATION wizard navigation.
     * Simplifies the template by pre-computing step status and plan approval state.
     *
     * @param draft the validation draft
     * @param model the Spring model to populate
     */
    private void preparePeriodicRevalidationNavigation(ValidationDraft draft, Model model) {
        // Pass step lock boundary to template (blocks steps until QA approves)
        Integer stepLockFrom = draft.getStepLockFrom();
        model.addAttribute("stepLockFrom", stepLockFrom);

        // Check if QA has approved the plan (allows access to measurement phase at step 9)
        boolean planApproved = draft.getPlanData() != null && draft.getPlanData().isQaApproved();
        model.addAttribute("planApproved", planApproved);
    }

    /**
     * Extracts roles from the current authentication.
     */
    private java.util.Collection<String> getRoles(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(org.springframework.security.core.GrantedAuthority::getAuthority)
            .collect(java.util.stream.Collectors.toList());
    }

    // ========================================================
    // Standard wizard navigation
    // ========================================================

    /**
     * List all active wizard drafts for current user
     */
    @GetMapping("/")
    public String listDrafts(Model model, Authentication authentication) {
        String username = authentication.getName();
        java.util.Collection<String> roles = getRoles(authentication);

        log.info("Listing wizard drafts for user: {} with roles: {}", username, roles);

        List<ValidationDraft> drafts = draftService.findActiveDraftsForUser(username, roles);
        model.addAttribute("drafts", drafts);

        return "wizard/list";
    }

    /**
     * Create a new wizard draft
     */
    @GetMapping("/new")
    public String createNewDraft(Authentication authentication, RedirectAttributes attrs) {
        String username = authentication.getName();
        log.info("Creating new wizard draft for user: {}", username);

        ValidationDraft draft = draftService.createDraft(username);

        attrs.addAttribute("id", draft.getId());
        return "redirect:/wizard/{id}/step/1";
    }

    /**
     * Show a specific wizard step.
     *
     * <p>
     * Steps 1-9 are shared across all procedure types.
     * Steps 10-13 are exclusive to the PERIODIC_REVALIDATION flow
     * and cover the measurement phase after QA plan approval.
     * The upper bound is 13 to accommodate the longer PR flow.
     * </p>
     */
    @GetMapping("/{id}/step/{step}")
    public String showStep(
        @PathVariable Long id,
        @PathVariable int step,
        Authentication authentication,
        Model model) {

        String username = authentication.getName();
        log.info("Showing step {} of draft {} for user: {}", step, id, username);

        ValidationDraft draft = draftService.getDraft(id, username, getRoles(authentication))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found or access denied: " + id));

        // PERIODIC_REVALIDATION has 13 steps; all other flows have 9
        if (step < 1 || step > 13) {
            throw new IllegalArgumentException("Invalid step: " + step);
        }

        model.addAttribute("draft", draft);
        model.addAttribute("currentStep", step);

        switch (step) {
            case 1:
                return "wizard/step1";

            case 2:
                model.addAttribute("devices", coolingDeviceService.getAllAccessibleDevices());
                return "wizard/step2";

            case 3:
                if (isPeriodicRevalidation(draft)) {
                    // PR step 3: plan details (reason, previous validation reference)
                    model.addAttribute("planData", planDataService.initializePlanData(id));
                    preparePeriodicRevalidationNavigation(draft, model);
                    return "wizard/step3-pr";
                }
                return "wizard/step3";

            case 4:
                if (isPeriodicRevalidation(draft)) {
                    // PR step 4: mapping status check (auto-computed from DB)
                    if (draft.getCoolingDevice() != null) {
                        MappingStatusDto mappingStatus =
                            mappingStatusService.computeMappingStatus(draft.getCoolingDevice().getId());
                        model.addAttribute("mappingStatus", mappingStatus);
                    }
                    model.addAttribute("planData", planDataService.initializePlanData(id));
                    preparePeriodicRevalidationNavigation(draft, model);
                    return "wizard/step4-pr";
                }
                model.addAttribute("loadStates", DeviceLoadState.values());
                model.addAttribute("positions", RecorderPosition.values());
                return "wizard/step4";

            case 5:
                if (isPeriodicRevalidation(draft)) {
                    // PR step 5: acceptance criteria and device load state
                    model.addAttribute("planData", planDataService.initializePlanData(id));
                    model.addAttribute("loadStates", DeviceLoadState.values());
                    preparePeriodicRevalidationNavigation(draft, model);
                    return "wizard/step5-pr";
                }
                if (draft.getProcedureType() == ValidationProcedureType.OQ) {
                    model.addAttribute("oqResult", oqTestService.getOrCreateOqTestResult(id, username));
                    return "wizard/step5-oq";
                } else if (draft.getProcedureType() == ValidationProcedureType.PQ) {
                    pqChecklistService.initializeDefaultChecklist(id, username);
                    model.addAttribute("pqItems", pqChecklistService.findByDraftId(id));
                    return "wizard/step5-pq";
                } else {
                    return "wizard/step5-mapping";
                }

            case 6:
                if (isPeriodicRevalidation(draft)) {
                    // PR step 6: custom acceptance criteria (optional extra thresholds)
                    model.addAttribute("planData", planDataService.initializePlanData(id));
                    model.addAttribute("criterionFields", AcceptanceCriterionField.values());
                    preparePeriodicRevalidationNavigation(draft, model);
                    return "wizard/step6-pr";
                }
                if (draft.getCoolingDevice() != null) {
                    model.addAttribute("series", seriesService.getUnusedSeriesByDevice(
                        draft.getCoolingDevice().getId()));
                }
                return "wizard/step6";

            case 7:
                if (isPeriodicRevalidation(draft)) {
                    // PR step 7: deviation (CAPA) procedures
                    model.addAttribute("planData", planDataService.initializePlanData(id));
                    preparePeriodicRevalidationNavigation(draft, model);
                    return "wizard/step7-pr";
                }
                if (draft.getSelectedSeriesIds() != null && !draft.getSelectedSeriesIds().isEmpty()) {
                    model.addAttribute("selectedSeries", draft.getSelectedSeriesIds().stream()
                        .map(seriesService::getSeriesById)
                        .collect(java.util.stream.Collectors.toList()));
                }
                return "wizard/step7";

            case 8:
                if (isPeriodicRevalidation(draft)) {
                    // PR step 8: plan PDF preview and technik signature
                    model.addAttribute("planData", planDataService.initializePlanData(id));
                    preparePeriodicRevalidationNavigation(draft, model);
                    return "wizard/step8-pr";
                }
                if (draft.getSelectedSeriesIds() != null && !draft.getSelectedSeriesIds().isEmpty()) {
                    model.addAttribute("selectedSeries", draft.getSelectedSeriesIds().stream()
                        .map(seriesService::getSeriesById)
                        .collect(java.util.stream.Collectors.toList()));
                }
                return "wizard/step8";

            case 9:
                if (isPeriodicRevalidation(draft)) {
                    // PR step 9: measurement series upload (measurement phase, post-QA-approval)
                    if (draft.getCoolingDevice() != null) {
                        model.addAttribute("series", seriesService.getUnusedSeriesByDevice(
                            draft.getCoolingDevice().getId()));
                    }
                    model.addAttribute("planData", planDataService.initializePlanData(id));
                    preparePeriodicRevalidationNavigation(draft, model);
                    return "wizard/step9-pr";
                }
                return "wizard/step9";

            case 10:
                // PR only: review statistics
                if (draft.getSelectedSeriesIds() != null && !draft.getSelectedSeriesIds().isEmpty()) {
                    model.addAttribute("selectedSeries", draft.getSelectedSeriesIds().stream()
                        .map(seriesService::getSeriesById)
                        .collect(java.util.stream.Collectors.toList()));
                }
                preparePeriodicRevalidationNavigation(draft, model);
                return "wizard/step10-pr";

            case 11:
                // PR only: review criteria compliance
                planDataService.findByDraftId(id)
                    .ifPresent(pd -> model.addAttribute("planData", pd));
                preparePeriodicRevalidationNavigation(draft, model);
                return "wizard/step11-pr";

            case 12:
                // PR only: deviations and CAPA summary
                planDataService.findByDraftId(id)
                    .ifPresent(pd -> model.addAttribute("planData", pd));
                preparePeriodicRevalidationNavigation(draft, model);
                return "wizard/step12-pr";

            case 13:
                // PR only: final report preview and electronic signature
                planDataService.findByDraftId(id)
                    .ifPresent(pd -> model.addAttribute("planData", pd));
                preparePeriodicRevalidationNavigation(draft, model);
                return "wizard/step13-pr";

            default:
                throw new IllegalArgumentException("Unknown step: " + step);
        }
    }

    /**
     * Save a step and move to next step
     */
    @PostMapping("/{id}/step/{step}")
    public String saveStep(
        @PathVariable Long id,
        @PathVariable int step,
        Authentication authentication,
        RedirectAttributes attrs) {

        String username = authentication.getName();
        log.info("Saving step {} of draft {} for user: {}", step, id, username);

        ValidationDraft draft = draftService.getDraft(id, username, getRoles(authentication))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found or access denied: " + id));

        // Save step-specific data (bound from form in subclasses or AJAX)
        // This is delegated to service methods called from step-specific endpoints

        // Move to next step
        draftService.moveToNextStep(id);

        attrs.addAttribute("id", id);
        attrs.addAttribute("step", step + 1);
        return "redirect:/wizard/{id}/step/{step}";
    }

    /**
     * Navigate back to a previous step (with lock checking)
     */
    @PostMapping("/{id}/back/{step}")
    public String navigateBack(
        @PathVariable Long id,
        @PathVariable int step,
        Authentication authentication,
        RedirectAttributes attrs) {

        String username = authentication.getName();
        log.info("Navigating back to step {} of draft {} for user: {}", step, id, username);

        ValidationDraft draft = draftService.getDraft(id, username, getRoles(authentication))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found or access denied: " + id));

        // Check if navigation is allowed
        if (!draftService.canNavigateBack(draft, step)) {
            attrs.addFlashAttribute("error",
                "Nie można wrócić do kroku " + step + ". Kroki 2-3 są zablokowane po zdefiniowaniu kryteriów akceptacji.");
            attrs.addAttribute("id", id);
            attrs.addAttribute("step", draft.getCurrentStep());
            return "redirect:/wizard/{id}/step/{step}";
        }

        draftService.navigateToStep(id, step);

        attrs.addAttribute("id", id);
        attrs.addAttribute("step", step);
        return "redirect:/wizard/{id}/step/{step}";
    }

    /**
     * Preview PDF in browser before signing
     */
    @GetMapping("/{id}/preview-pdf")
    public String previewPdf(
        @PathVariable Long id,
        Authentication authentication,
        Model model) {

        String username = authentication.getName();
        log.info("Previewing PDF for draft {} of user: {}", id, username);

        ValidationDraft draft = draftService.getDraft(id, username, getRoles(authentication))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found or access denied: " + id));

        // TODO: Generate PDF preview (inline in iframe)
        // This would be a separate endpoint returning PDF bytes

        model.addAttribute("draft", draft);
        return "wizard/step9";
    }

    /**
     * Finalize wizard: create Validation, sign it, and complete draft.
     * This is the critical step 9 (OQ/PQ/MAPPING) or step 13 (PERIODIC_REVALIDATION)
     * finalization endpoint.
     */
    @PostMapping("/{id}/finalize")
    public String finalize(
        @PathVariable Long id,
        @RequestParam String password,
        @RequestParam(required = false, defaultValue = "Zatwierdzenie walidacji") String intent,
        Authentication authentication,
        RedirectAttributes attrs) {

        String username = authentication.getName();
        log.info("Finalizing wizard draft {} for user: {}", id, username);

        try {
            ValidationDraft draft = draftService.getDraft(id, username, getRoles(authentication))
                .orElseThrow(() -> new IllegalArgumentException("Draft not found or access denied: " + id));

            // Finalize: create Validation, sign, update draft
            Validation validation = finalizationService.finalizeWizard(id, username, password, intent);

            attrs.addFlashAttribute("success",
                "Walidacja nr " + validation.getId() + " została pomyślnie utworzona i podpisana.");
            return "redirect:/validations/" + validation.getId();

        } catch (IllegalStateException | IllegalArgumentException e) {
            log.error("Finalization failed for draft {}: {}", id, e.getMessage());
            attrs.addFlashAttribute("error", "Błąd finalizacji: " + e.getMessage());
            attrs.addAttribute("id", id);
            attrs.addAttribute("step", 9);
            return "redirect:/wizard/{id}/step/{step}";
        } catch (Exception e) {
            log.error("Unexpected error during finalization of draft {}", id, e);
            attrs.addFlashAttribute("error", "Nieoczekiwany błąd: " + e.getMessage());
            attrs.addAttribute("id", id);
            attrs.addAttribute("step", 9);
            return "redirect:/wizard/{id}/step/{step}";
        }
    }

    /**
     * Abandon the wizard draft
     */
    @PostMapping("/{id}/abandon")
    public String abandon(
        @PathVariable Long id,
        Authentication authentication,
        RedirectAttributes attrs) {

        String username = authentication.getName();
        log.info("Abandoning wizard draft {} for user: {}", id, username);

        ValidationDraft draft = draftService.getDraft(id, username, getRoles(authentication))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found or access denied: " + id));

        draftService.abandonDraft(id);

        attrs.addFlashAttribute("info", "Szkic walidacji został porzucony.");
        return "redirect:/wizard/";
    }

    // ========================================================
    // Step-specific AJAX/form endpoints (OQ / PQ / MAPPING)
    // ========================================================

    /**
     * AJAX: Save step 1 (procedure type selection)
     */
    @PostMapping("/{id}/api/step1")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiSaveStep1(
        @PathVariable Long id,
        @RequestParam ValidationProcedureType procedureType,
        Authentication authentication) {

        String username = authentication.getName();
        return draftService.getDraft(id, username, getRoles(authentication))
            .map(draft -> {
                draftService.saveStep1(id, procedureType);
                return ResponseEntity.ok(Map.<String, Object>of("success", true, "id", id, "procedureType", procedureType));
            })
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));
    }

    /**
     * AJAX: Save step 2 (device selection)
     */
    @PostMapping("/{id}/api/step2")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiSaveStep2(
        @PathVariable Long id,
        @RequestParam Long coolingDeviceId,
        Authentication authentication) {

        String username = authentication.getName();
        return draftService.getDraft(id, username, getRoles(authentication))
            .map(draft -> {
                draftService.saveStep2(id, coolingDeviceId);
                return ResponseEntity.ok(Map.<String, Object>of("success", true, "id", id, "coolingDeviceId", coolingDeviceId));
            })
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));
    }

    /**
     * AJAX: Save step 3 (custom acceptance criteria — OQ/PQ/MAPPING only)
     */
    @PostMapping("/{id}/api/step3")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiSaveStep3(
        @PathVariable Long id,
        @RequestBody List<CustomAcceptanceCriterionDto> criteria,
        Authentication authentication) {

        String username = authentication.getName();
        return draftService.getDraft(id, username, getRoles(authentication))
            .map(draft -> {
                draftService.saveStep3(id, criteria);
                return ResponseEntity.ok(Map.<String, Object>of("success", true, "id", id, "criteriaCount", criteria.size()));
            })
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));
    }

    /**
     * AJAX: Save step 4 (load state and recorder position — OQ/PQ/MAPPING only)
     */
    @PostMapping("/{id}/api/step4")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiSaveStep4(
        @PathVariable Long id,
        @RequestParam DeviceLoadState loadState,
        @RequestParam RecorderPosition position,
        Authentication authentication) {

        String username = authentication.getName();
        return draftService.getDraft(id, username, getRoles(authentication))
            .map(draft -> {
                draftService.saveStep4(id, loadState, position);
                return ResponseEntity.ok(Map.<String, Object>of("success", true, "id", id, "loadState", loadState, "position", position));
            })
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));
    }

    /**
     * AJAX: Save OQ test results (Step 5)
     */
    @PostMapping("/{id}/api/step5/oq")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiSaveStep5Oq(
        @PathVariable Long id,
        @RequestParam(required = false) Boolean powerFailure,
        @RequestParam(required = false) String powerNotes,
        @RequestParam(required = false) Boolean alarmTest,
        @RequestParam(required = false) String alarmNotes,
        @RequestParam(required = false) Boolean doorTest,
        @RequestParam(required = false) String doorNotes,
        Authentication authentication) {

        String username = authentication.getName();
        oqTestService.getOrCreateOqTestResult(id, username);

        if (powerFailure != null) oqTestService.savePowerFailureTest(id, powerFailure, powerNotes, username);
        if (alarmTest != null) oqTestService.saveAlarmTest(id, alarmTest, alarmNotes, username);
        if (doorTest != null) oqTestService.saveDoorTest(id, doorTest, doorNotes, username);

        return ResponseEntity.ok(Map.<String, Object>of("success", true, "id", id));
    }

    /**
     * AJAX: Save PQ checklist item (Step 5)
     */
    @PostMapping("/{id}/api/step5/pq")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiSaveStep5Pq(
        @PathVariable Long id,
        @RequestParam Long itemId,
        @RequestParam Boolean passed,
        @RequestParam(required = false) String comment,
        Authentication authentication) {

        String username = authentication.getName();
        pqChecklistService.saveAnswer(itemId, passed, comment, username);
        return ResponseEntity.ok(Map.<String, Object>of("success", true, "itemId", itemId));
    }

    /**
     * AJAX: Save step 6 (series selection)
     */
    @PostMapping("/{id}/api/step6")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiSaveStep6(
        @PathVariable Long id,
        @RequestBody List<Long> seriesIds,
        Authentication authentication) {

        String username = authentication.getName();
        return draftService.getDraft(id, username, getRoles(authentication))
            .map(draft -> {
                draftService.saveStep6(id, seriesIds);
                return ResponseEntity.ok(Map.<String, Object>of("success", true, "id", id, "seriesCount", seriesIds.size()));
            })
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));
    }

    /**
     * AJAX: Upload .vi2 files for step 6 (direct upload)
     */
    @PostMapping("/{id}/api/step6/upload")
    @ResponseBody
    public List<MeasurementSeriesDto> apiUploadStep6(
        @PathVariable Long id,
        @RequestParam("files") MultipartFile[] files,
        @RequestParam(required = false) RecorderPosition recorderPosition,
        @RequestParam(defaultValue = "false") boolean isReferenceRecorder,
        Authentication authentication) throws Exception {

        String username = authentication.getName();
        ValidationDraft draft = draftService.getDraft(id, username, getRoles(authentication))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));

        if (draft.getCoolingDevice() == null) {
            throw new IllegalStateException("Należy najpierw wybrać urządzenie w kroku 2");
        }

        Long deviceId = draft.getCoolingDevice().getId();
        log.info("Wizard Step 6: Uploading {} files for device {}", files.length, deviceId);

        try {
            UploadResult result = seriesService.uploadVi2Files(files, recorderPosition, deviceId, isReferenceRecorder);

            for (var series : result.getUploadedSeries()) {
                String positionName = isReferenceRecorder ? "REFERENCE" :
                        (recorderPosition != null ? recorderPosition.name() : "NONE");
                auditService.logOperation("MeasurementSeries", series.getId(), "CREATE", null,
                        Map.of("fileName", series.getOriginalFilename(), "deviceId", deviceId,
                                "position", positionName, "wizardDraftId", id));
            }

            return seriesService.getUnusedSeriesByDevice(deviceId);
        } catch (Exception e) {
            log.error("Error during wizard upload: {}", e.getMessage());
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    // ========================================================
    // PERIODIC_REVALIDATION plan phase endpoints (steps 3-8)
    // ========================================================

    /**
     * AJAX: Save plan details for PERIODIC_REVALIDATION (step 3-PR).
     *
     * <p>Persists the revalidation reason and optional previous validation reference.
     * Requires PERIODIC_REVALIDATION procedure type — returns 409 Conflict otherwise.</p>
     */
    @PostMapping("/{id}/api/step3-pr")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiSaveStep3Pr(
        @PathVariable Long id,
        @Valid @RequestBody ValidationPlanDataDto dto,
        BindingResult bindingResult,
        Authentication authentication) {

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors on step3-pr for draft {}: {}", id,
                bindingResult.getAllErrors());
            return ResponseEntity.badRequest().body(Map.<String, Object>of(
                "success", false,
                "message", bindingResult.getAllErrors().get(0).getDefaultMessage()));
        }

        String username = authentication.getName();
        ValidationDraft draft = draftService.getDraft(id, username, getRoles(authentication))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));

        if (!isPeriodicRevalidation(draft)) {
            log.warn("step3-pr called on non-PR draft {} by user {}", id, username);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.<String, Object>of(
                "success", false,
                "message", "Endpoint dostępny tylko dla Rewalidacji Okresowej"));
        }

        log.info("Saving PR plan details for draft {} by user: {}", id, username);
        planDataService.savePlanDetails(id, dto);
        return ResponseEntity.ok(Map.<String, Object>of("success", true, "draftId", id));
    }

    /**
     * AJAX: Save mapping status acknowledgement for PERIODIC_REVALIDATION (step 4-PR).
     *
     * <p>Persists the mapping status DTO that was computed on the client from the
     * {@link MappingStatusService#computeMappingStatus} result, including any overdue
     * acknowledgement flag required when status is OVERDUE or NEVER.
     * Returns 409 Conflict for non-PR drafts.</p>
     */
    @PostMapping("/{id}/api/step4-pr")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiSaveStep4Pr(
        @PathVariable Long id,
        @RequestBody MappingStatusDto mappingStatusDto,
        Authentication authentication) {

        String username = authentication.getName();
        ValidationDraft draft = draftService.getDraft(id, username, getRoles(authentication))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));

        if (!isPeriodicRevalidation(draft)) {
            log.warn("step4-pr called on non-PR draft {} by user {}", id, username);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.<String, Object>of("success", false, "message", "Endpoint tylko dla PR"));
        }

        log.info("Saving mapping status for draft {} by user: {}", id, username);
        planDataService.saveMappingStatus(id, mappingStatusDto);
        return ResponseEntity.ok(Map.<String, Object>of("success", true, "draftId", id));
    }

    /**
     * AJAX: Save acceptance criteria and device load state for PERIODIC_REVALIDATION (step 5-PR).
     *
     * <p>Persists the load state and temperature acceptance criteria (nominal, min/max,
     * MKT threshold, uniformity delta, drift max). These criteria define the pass/fail
     * boundaries evaluated during the measurement phase.
     * Returns 409 Conflict for non-PR drafts.</p>
     */
    @PostMapping("/{id}/api/step5-pr")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiSaveStep5Pr(
        @PathVariable Long id,
        @Valid @RequestBody PlanCriteriaDto criteriaDto,
        BindingResult bindingResult,
        Authentication authentication) {

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors on step5-pr for draft {}: {}", id,
                bindingResult.getAllErrors());
            return ResponseEntity.badRequest().body(Map.<String, Object>of("success", false, "message", "Błędne dane kryteriów"));
        }

        String username = authentication.getName();
        ValidationDraft draft = draftService.getDraft(id, username, getRoles(authentication))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));

        if (!isPeriodicRevalidation(draft)) {
            log.warn("step5-pr called on non-PR draft {} by user {}", id, username);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.<String, Object>of("success", false, "message", "Endpoint tylko dla PR"));
        }

        log.info("Saving PR acceptance criteria for draft {} by user: {}", id, username);
        planDataService.savePlanCriteria(id, criteriaDto);
        return ResponseEntity.ok(Map.<String, Object>of("success", true, "draftId", id));
    }

    /**
     * AJAX: Save deviation (CAPA) procedures for PERIODIC_REVALIDATION (step 7-PR).
     *
     * <p>Persists corrective and preventive action texts for critical, major, and minor
     * deviations. Required by GMP Annex 15 §10 to be defined before the measurement
     * phase begins. Returns 409 Conflict for non-PR drafts.</p>
     */
    @PostMapping("/{id}/api/step7-pr")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiSaveStep7Pr(
        @PathVariable Long id,
        @Valid @RequestBody DeviationProceduresDto proceduresDto,
        BindingResult bindingResult,
        Authentication authentication) {

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors on step7-pr for draft {}: {}", id,
                bindingResult.getAllErrors());
            return ResponseEntity.badRequest().body(Map.<String, Object>of("success", false, "message", "Błędne dane procedur"));
        }

        String username = authentication.getName();
        ValidationDraft draft = draftService.getDraft(id, username, getRoles(authentication))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));

        if (!isPeriodicRevalidation(draft)) {
            log.warn("step7-pr called on non-PR draft {} by user {}", id, username);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.<String, Object>of("success", false, "message", "Endpoint tylko dla PR"));
        }

        log.info("Saving PR deviation procedures for draft {} by user: {}", id, username);
        planDataService.saveDeviationProcedures(id, proceduresDto);
        return ResponseEntity.ok(Map.<String, Object>of("success", true, "draftId", id));
    }

    /**
     * POST: Technik electronically signs the validation plan (step 8-PR).
     *
     * <p>Verifies the technician's password as an electronic signature (FDA 21 CFR Part 11),
     * records the signature on the plan, and transitions the draft status to
     * {@link WizardStatus#AWAITING_QA_APPROVAL}. The wizard is blocked for the technik
     * until a QA user approves or rejects the plan via {@code /plan-approve} or
     * {@code /plan-reject}.</p>
     */
    @PostMapping("/{id}/finalize-plan")
    public String finalizePlan(
        @PathVariable Long id,
        @RequestParam String password,
        Authentication authentication,
        RedirectAttributes attrs) {

        String username = authentication.getName();
        log.info("Technik {} signing validation plan for draft {}", username, id);

        try {
            ValidationDraft draft = draftService.getDraft(id, username, getRoles(authentication))
                .orElseThrow(() -> new IllegalArgumentException("Draft not found or access denied: " + id));

            if (!isPeriodicRevalidation(draft)) {
                log.warn("finalize-plan called on non-PR draft {} by user {}", id, username);
                attrs.addFlashAttribute("error",
                    "Podpisanie planu dostępne tylko dla procedury Rewalidacji Okresowej.");
                return "redirect:/wizard/" + id + "/step/8";
            }

            planDataService.signPlanAsTechnician(id, username, password);

            log.info("Validation plan signed by technik {} for draft {}. Status -> AWAITING_QA_APPROVAL.", username, id);
            attrs.addFlashAttribute("info",
                "Plan walidacji został podpisany elektronicznie. Oczekiwanie na zatwierdzenie przez QA.");
            return "redirect:/wizard/" + id + "/awaiting-approval";

        } catch (IllegalStateException | IllegalArgumentException e) {
            log.error("Plan signing failed for draft {} by user {}: {}", id, username, e.getMessage());
            attrs.addFlashAttribute("error", "Błąd podpisania planu: " + e.getMessage());
            return "redirect:/wizard/" + id + "/step/8";
        } catch (Exception e) {
            log.error("Unexpected error during plan signing of draft {} by user {}", id, username, e);
            attrs.addFlashAttribute("error", "Nieoczekiwany błąd podczas podpisywania planu.");
            return "redirect:/wizard/" + id + "/step/8";
        }
    }

    /**
     * GET: Status holding page shown to the technik while the plan awaits QA approval.
     *
     * <p>Displays the current draft status ({@link WizardStatus#AWAITING_QA_APPROVAL})
     * and a summary of the signed plan. No further wizard actions are available
     * until QA acts on the plan.</p>
     */
    @GetMapping("/{id}/awaiting-approval")
    public String awaitingApproval(
        @PathVariable Long id,
        Authentication authentication,
        Model model) {

        String username = authentication.getName();
        log.info("Showing awaiting-approval page for draft {} to user: {}", id, username);

        ValidationDraft draft = draftService.getDraft(id, username, getRoles(authentication))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found or access denied: " + id));

        model.addAttribute("draft", draft);
        planDataService.findByDraftId(id).ifPresent(pd -> model.addAttribute("planData", pd));

        return "wizard/awaiting-approval";
    }

    /**
     * GET: QA review page for the validation plan (ROLE_QA only).
     *
     * <p>Presents the full plan summary — details, mapping status, acceptance criteria,
     * and deviation procedures — for the QA user to review before approving or rejecting.
     * Access is restricted to principals holding {@code ROLE_QA}.</p>
     */
    @GetMapping("/{id}/plan-review")
    @PreAuthorize("hasRole('QA')")
    public String planReview(
        @PathVariable Long id,
        Authentication authentication,
        Model model) {

        String qaUsername = authentication.getName();
        log.info("QA user {} opening plan-review for draft {}", qaUsername, id);

        ValidationDraft draft = draftService.getDraftById(id)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));

        planDataService.findByDraftId(id)
            .orElseThrow(() -> new IllegalArgumentException("Plan data not found for draft: " + id));

        planDataService.findByDraftId(id).ifPresent(pd -> model.addAttribute("planData", pd));
        model.addAttribute("draft", draft);

        return "wizard/plan-review";
    }

    /**
     * POST: QA electronically approves the validation plan (ROLE_QA only).
     *
     * <p>Records the QA electronic signature (FDA 21 CFR Part 11 co-signature),
     * marks the plan as approved, and transitions the draft back to
     * {@link WizardStatus#IN_PROGRESS} at step 9 (measurement phase).
     * Access is restricted to principals holding {@code ROLE_QA}.</p>
     */
    @PostMapping("/{id}/plan-approve")
    @PreAuthorize("hasRole('QA')")
    public String planApprove(
        @PathVariable Long id,
        @RequestParam String password,
        Authentication authentication,
        RedirectAttributes attrs) {

        String qaUsername = authentication.getName();
        log.info("QA user {} approving validation plan for draft {}", qaUsername, id);

        try {
            planDataService.approvePlanAsQa(id, qaUsername, password);

            log.info("Plan approved by QA {} for draft {}. QA redirected to wizard list.", qaUsername, id);
            attrs.addFlashAttribute("success",
                "Plan walidacji został zatwierdzony. Rewalidacja przechodzi do fazy pomiarowej.");
            return "redirect:/wizard/";

        } catch (IllegalStateException | IllegalArgumentException e) {
            log.error("QA approval failed for draft {} by {}: {}", id, qaUsername, e.getMessage());
            attrs.addFlashAttribute("error", "Błąd zatwierdzenia planu: " + e.getMessage());
            return "redirect:/wizard/" + id + "/plan-review";
        } catch (Exception e) {
            log.error("Unexpected error during QA approval of draft {} by {}", id, qaUsername, e);
            attrs.addFlashAttribute("error", "Nieoczekiwany błąd podczas zatwierdzania planu.");
            return "redirect:/wizard/" + id + "/plan-review";
        }
    }

    /**
     * GET: Download the printable validation plan PDF.
     *
     * <p>Generates (or retrieves from cache) the signed PDF of the validation plan.
     * Used for printing and "wet" signatures by external QA.</p>
     */
    @GetMapping("/{id}/plan-download")
    public ResponseEntity<byte[]> planDownload(
        @PathVariable Long id,
        Authentication authentication) {

        String username = authentication.getName();
        log.info("User {} downloading plan PDF for draft {}", username, id);

        try {
            // Permission check: creator or QA
            ValidationDraft draft = draftService.getDraft(id, username, getRoles(authentication))
                .orElseThrow(() -> new IllegalArgumentException("Draft not found or access denied: " + id));

            byte[] pdfBytes = planPdfService.getPlanPdfWithCache(id);

            String filename = "Plan_Walidacji_PR_" + id + ".pdf";

            return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
                .body(pdfBytes);

        } catch (Exception e) {
            log.error("Failed to generate plan PDF for download (draft {}): {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * POST: Technician uploads a manual signature scan (External QA path).
     *
     * <p>Processes the uploaded PDF scan of a wet-signed validation plan.
     * Marks the plan as approved via external path and transitions the draft
     * to step 9 (measurement phase).</p>
     */
    @PostMapping("/{id}/plan-approve-external")
    public String planApproveExternal(
        @PathVariable Long id,
        @RequestParam("scan") org.springframework.web.multipart.MultipartFile scan,
        Authentication authentication,
        RedirectAttributes attrs) {

        String technicianUsername = authentication.getName();
        log.info("Technician {} uploading external scan for draft {}", technicianUsername, id);

        try {
            planDataService.approvePlanExternal(id, technicianUsername, scan);

            log.info("Plan approved via manual scan for draft {}. Draft proceeds to measurement phase.", id);
            attrs.addFlashAttribute("success",
                "Skan podpisu QA został przesłany. Rewalidacja przechodzi do fazy pomiarowej.");
            return "redirect:/wizard/" + id + "/step/9";

        } catch (Exception e) {
            log.error("External QA approval failed for draft {}: {}", id, e.getMessage(), e);
            attrs.addFlashAttribute("error", "Błąd przesłania skanu: " + e.getMessage());
            return "redirect:/wizard/" + id + "/awaiting-approval";
        }
    }

    /**
     * POST: QA rejects the validation plan with a mandatory reason (ROLE_QA only).
     *
     * <p>Records the rejection reason in the audit trail (GMP Annex 11 §14),
     * returns the draft to {@link WizardStatus#IN_PROGRESS} at step 8 for technik
     * revision, and redirects QA back to the wizard list.
     * Access is restricted to principals holding {@code ROLE_QA}.</p>
     */
    @PostMapping("/{id}/plan-reject")
    @PreAuthorize("hasRole('QA')")
    public String planReject(
        @PathVariable Long id,
        @RequestParam String rejectionReason,
        Authentication authentication,
        RedirectAttributes attrs) {

        String qaUsername = authentication.getName();
        log.info("QA user {} rejecting plan for draft {} — reason: {}", qaUsername, id, rejectionReason);

        try {
            planDataService.rejectPlan(id, qaUsername, rejectionReason);

            log.info("Plan rejected by QA {} for draft {}. Returned to technik for revision.", qaUsername, id);
            attrs.addFlashAttribute("warning",
                "Plan walidacji został odrzucony. Technik otrzymał powiadomienie i musi poprawić plan.");
            return "redirect:/wizard/";

        } catch (IllegalStateException | IllegalArgumentException e) {
            log.error("QA rejection failed for draft {} by {}: {}", id, qaUsername, e.getMessage());
            attrs.addFlashAttribute("error", "Błąd odrzucenia planu: " + e.getMessage());
            return "redirect:/wizard/" + id + "/plan-review";
        } catch (Exception e) {
            log.error("Unexpected error during QA rejection of draft {} by {}", id, qaUsername, e);
            attrs.addFlashAttribute("error", "Nieoczekiwany błąd podczas odrzucania planu.");
            return "redirect:/wizard/" + id + "/plan-review";
        }
    }
}
