package com.mac.bry.validationsystem.wizard;

import com.mac.bry.validationsystem.device.CoolingDeviceService;
import com.mac.bry.validationsystem.measurement.MeasurementSeriesDto;
import com.mac.bry.validationsystem.measurement.MeasurementSeriesService;
import com.mac.bry.validationsystem.measurement.RecorderPosition;
import com.mac.bry.validationsystem.measurement.UploadResult;
import com.mac.bry.validationsystem.security.service.AuditService;
import com.mac.bry.validationsystem.validation.DeviceLoadState;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.wizard.criteria.CustomAcceptanceCriterionDto;
import com.mac.bry.validationsystem.wizard.service.ValidationDraftService;
import com.mac.bry.validationsystem.wizard.service.WizardFinalizationService;
import com.mac.bry.validationsystem.wizard.oq.OqTestResult;
import com.mac.bry.validationsystem.wizard.oq.OqTestService;
import com.mac.bry.validationsystem.wizard.pq.PqChecklistItem;
import com.mac.bry.validationsystem.wizard.pq.PqChecklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Controller for the multi-step validation wizard (9 steps).
 *
 * <p>
 * Endpoints:
 * - GET /wizard/ → List active drafts
 * - GET /wizard/new → Create new draft
 * - GET /wizard/{id}/step/{n} → Show step n
 * - POST /wizard/{id}/step/{n} → Save step n
 * - GET /wizard/{id}/back/{n} → Navigate back to step n
 * - GET /wizard/{id}/preview-pdf → Preview PDF in browser
 * - POST /wizard/{id}/finalize → Finalize and sign (step 9)
 * - POST /wizard/{id}/abandon → Abandon draft
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

    /**
     * List all active wizard drafts for current user
     */
    @GetMapping("/")
    public String listDrafts(Model model, Authentication authentication) {
        String username = authentication.getName();
        log.info("Listing wizard drafts for user: {}", username);

        List<ValidationDraft> drafts = draftService.findActiveDraftsForUser(username);
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
     * Show a specific step
     */
    @GetMapping("/{id}/step/{step}")
    public String showStep(
        @PathVariable Long id,
        @PathVariable int step,
        Authentication authentication,
        Model model) {

        String username = authentication.getName();
        log.info("Showing step {} of draft {} for user: {}", step, id, username);

        ValidationDraft draft = draftService.getDraft(id, username)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found or access denied: " + id));

        // Validate step is in range
        if (step < 1 || step > 9) {
            throw new IllegalArgumentException("Invalid step: " + step);
        }

        model.addAttribute("draft", draft);
        model.addAttribute("currentStep", step);

        // Add data models depending on step
        switch (step) {
            case 1:
                return "wizard/step1";
            case 2:
                model.addAttribute("devices", coolingDeviceService.getAllAccessibleDevices());
                return "wizard/step2";
            case 3:
                // Load existing criteria
                return "wizard/step3";
            case 4:
                model.addAttribute("loadStates", DeviceLoadState.values());
                model.addAttribute("positions", RecorderPosition.values());
                return "wizard/step4";
            case 5:
                // Return template based on procedure type
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
                // Load measurement series for device
                if (draft.getCoolingDevice() != null) {
                    model.addAttribute("series", seriesService.getUnusedSeriesByDevice(draft.getCoolingDevice().getId()));
                }
                return "wizard/step6";
            case 7:
                if (draft.getSelectedSeriesIds() != null && !draft.getSelectedSeriesIds().isEmpty()) {
                    model.addAttribute("selectedSeries", draft.getSelectedSeriesIds().stream()
                        .map(seriesService::getSeriesById)
                        .collect(java.util.stream.Collectors.toList()));
                }
                return "wizard/step7";
            case 8:
                if (draft.getSelectedSeriesIds() != null && !draft.getSelectedSeriesIds().isEmpty()) {
                    model.addAttribute("selectedSeries", draft.getSelectedSeriesIds().stream()
                        .map(seriesService::getSeriesById)
                        .collect(java.util.stream.Collectors.toList()));
                }
                return "wizard/step8";
            case 9:
                return "wizard/step9";
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

        ValidationDraft draft = draftService.getDraft(id, username)
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

        ValidationDraft draft = draftService.getDraft(id, username)
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

        ValidationDraft draft = draftService.getDraft(id, username)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found or access denied: " + id));

        // TODO: Generate PDF preview (inline in iframe)
        // This would be a separate endpoint returning PDF bytes

        model.addAttribute("draft", draft);
        return "wizard/step9";
    }

    /**
     * Finalize wizard: create Validation, sign it, and complete draft
     * This is the critical step 9 finalization endpoint
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
            // Verify draft ownership
            ValidationDraft draft = draftService.getDraft(id, username)
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

        ValidationDraft draft = draftService.getDraft(id, username)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found or access denied: " + id));

        draftService.abandonDraft(id);

        attrs.addFlashAttribute("info", "Szkic walidacji został porzucony.");
        return "redirect:/wizard/";
    }

    // ========================================================
    // Step-specific AJAX/form endpoints (for saving individual steps)
    // ========================================================

    /**
     * AJAX: Save step 1 (procedure type selection)
     */
    @PostMapping("/{id}/api/step1")
    @ResponseBody
    public ValidationDraft apiSaveStep1(
        @PathVariable Long id,
        @RequestParam ValidationProcedureType procedureType,
        Authentication authentication) {

        String username = authentication.getName();
        return draftService.getDraft(id, username)
            .map(draft -> draftService.saveStep1(id, procedureType))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));
    }

    /**
     * AJAX: Save step 2 (device selection)
     */
    @PostMapping("/{id}/api/step2")
    @ResponseBody
    public ValidationDraft apiSaveStep2(
        @PathVariable Long id,
        @RequestParam Long coolingDeviceId,
        Authentication authentication) {

        String username = authentication.getName();
        return draftService.getDraft(id, username)
            .map(draft -> draftService.saveStep2(id, coolingDeviceId))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));
    }

    /**
     * AJAX: Save step 3 (custom acceptance criteria)
     */
    @PostMapping("/{id}/api/step3")
    @ResponseBody
    public ValidationDraft apiSaveStep3(
        @PathVariable Long id,
        @RequestBody List<CustomAcceptanceCriterionDto> criteria,
        Authentication authentication) {

        String username = authentication.getName();
        return draftService.getDraft(id, username)
            .map(draft -> draftService.saveStep3(id, criteria))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));
    }

    /**
     * AJAX: Save step 4 (load state and position)
     */
    @PostMapping("/{id}/api/step4")
    @ResponseBody
    public ValidationDraft apiSaveStep4(
        @PathVariable Long id,
        @RequestParam DeviceLoadState loadState,
        @RequestParam RecorderPosition position,
        Authentication authentication) {

        String username = authentication.getName();
        return draftService.getDraft(id, username)
            .map(draft -> draftService.saveStep4(id, loadState, position))
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));
    }

    /**
     * AJAX: Save OQ test results (Step 5)
     */
    @PostMapping("/{id}/api/step5/oq")
    @ResponseBody
    public OqTestResult apiSaveStep5Oq(
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
        
        return oqTestService.findByDraftId(id).orElseThrow();
    }

    /**
     * AJAX: Save PQ checklist item (Step 5)
     */
    @PostMapping("/{id}/api/step5/pq")
    @ResponseBody
    public PqChecklistItem apiSaveStep5Pq(
        @PathVariable Long id,
        @RequestParam Long itemId,
        @RequestParam Boolean passed,
        @RequestParam(required = false) String comment,
        Authentication authentication) {

        String username = authentication.getName();
        return pqChecklistService.saveAnswer(itemId, passed, comment, username);
    }

    /**
     * AJAX: Save step 6 (series selection)
     */
    @PostMapping("/{id}/api/step6")
    @ResponseBody
    public ValidationDraft apiSaveStep6(
        @PathVariable Long id,
        @RequestBody List<Long> seriesIds,
        Authentication authentication) {

        String username = authentication.getName();
        return draftService.getDraft(id, username)
            .map(draft -> draftService.saveStep6(id, seriesIds))
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
        ValidationDraft draft = draftService.getDraft(id, username)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));

        if (draft.getCoolingDevice() == null) {
            throw new IllegalStateException("Należy najpierw wybrać urządzenie w kroku 2");
        }

        Long deviceId = draft.getCoolingDevice().getId();
        log.info("Wizard Step 6: Uploading {} files for device {}", files.length, deviceId);

        try {
            // Perform upload
            UploadResult result = seriesService.uploadVi2Files(files, recorderPosition, deviceId, isReferenceRecorder);

            // Map and Audit results (similar to MeasurementSeriesController)
            for (var series : result.getUploadedSeries()) {
                String positionName = isReferenceRecorder ? "REFERENCE" :
                        (recorderPosition != null ? recorderPosition.name() : "NONE");
                auditService.logOperation("MeasurementSeries", series.getId(), "CREATE", null,
                        Map.of("fileName", series.getOriginalFilename(), "deviceId", deviceId,
                                "position", positionName, "wizardDraftId", id));
            }

            // Return updated list of available series for this device
            return seriesService.getUnusedSeriesByDevice(deviceId);
        } catch (Exception e) {
            log.error("Error during wizard upload: {}", e.getMessage());
            throw new IllegalArgumentException(e.getMessage());
        }
    }
}
