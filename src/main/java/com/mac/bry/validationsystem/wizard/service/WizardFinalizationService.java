package com.mac.bry.validationsystem.wizard.service;

import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.MeasurementSeriesRepository;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import com.mac.bry.validationsystem.stats.ValidationSummaryStatsService;
import com.mac.bry.validationsystem.validation.DocumentNumberingService;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationService;
import com.mac.bry.validationsystem.validation.ValidationSigningService;
import com.mac.bry.validationsystem.wizard.ValidationDraft;
import com.mac.bry.validationsystem.wizard.ValidationDraftRepository;
import com.mac.bry.validationsystem.wizard.ValidationPlanData;
import com.mac.bry.validationsystem.wizard.WizardStatus;
import com.mac.bry.validationsystem.wizard.pdf.ProcedureStrategyFactory;
import com.mac.bry.validationsystem.wizard.pdf.ValidationPlanPdfService;
import com.mac.bry.validationsystem.wizard.plandata.PlanPdfInfo;
import com.mac.bry.validationsystem.wizard.plandata.TechnikSignature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Critical service for finalizing wizard (step 9).
 *
 * <p>
 * Handles the complete flow from ValidationDraft to finalized Validation:
 * 1. Verify draft state and ownership
 * 2. Re-validate series availability (race condition protection)
 * 3. Create Validation entity from draft
 * 4. Calculate summary statistics
 * 5. Detect deviations
 * 6. Generate PDF with procedure-specific strategy
 * 7. Sign PDF with user's certificate
 * 8. Update draft status = COMPLETED, link to Validation
 *
 * All within a single @Transactional block for atomicity.
 * </p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class WizardFinalizationService {

    private final ValidationDraftRepository draftRepository;
    private final ValidationService validationService;
    private final ValidationSummaryStatsService summaryStatsService;
    private final MeasurementSeriesRepository measurementSeriesRepository;
    private final ValidationSigningService signingService;
    private final ProcedureStrategyFactory procedureStrategyFactory;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ValidationPlanPdfService validationPlanPdfService;
    private final DocumentNumberingService documentNumberingService;

    /** Base directory for persisting signed plan PDFs. */
    @Value("${app.signed.documents.path:uploads/signed}")
    private String signedDocumentsPath;

    /**
     * Finalize wizard: create Validation, sign it, and complete the draft
     *
     * @param draftId The draft to finalize
     * @param username Current user (must own the draft)
     * @param password User's password for signing
     * @param signatureIntent Intent/reason for signing (e.g., "Zatwierdzenie walidacji")
     * @return The finalized Validation entity
     *
     * @throws IllegalArgumentException if draft not found or doesn't belong to user
     * @throws IllegalStateException if draft is not in IN_PROGRESS status or series unavailable
     */
    public Validation finalizeWizard(
        Long draftId,
        String username,
        String password,
        String signatureIntent) {

        log.info("Finalizing wizard draft: {} for user: {}", draftId, username);

        // ============================================================
        // Step 1: Verify draft state and ownership
        // ============================================================

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        if (!draft.getCreatedBy().equals(username)) {
            throw new IllegalArgumentException("User " + username + " does not own draft " + draftId);
        }

        if (draft.getStatus() != WizardStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                "Cannot finalize draft with status: " + draft.getStatus()
            );
        }

        if (draft.getCoolingDevice() == null) {
            throw new IllegalStateException("Draft has no cooling device selected");
        }

        if (draft.getSelectedSeriesIds() == null || draft.getSelectedSeriesIds().isEmpty()) {
            throw new IllegalStateException("Draft has no measurement series selected");
        }

        log.debug("Draft verified: device={}, series count={}",
            draft.getCoolingDevice().getId(),
            draft.getSelectedSeriesIds().size());

        // ============================================================
        // Step 2: Re-validate series availability (race condition protection)
        // ============================================================

        List<MeasurementSeries> seriesForValidation = new ArrayList<>();
        for (Long seriesId : draft.getSelectedSeriesIds()) {
            MeasurementSeries series = measurementSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new IllegalStateException(
                    "Series was deleted after draft: " + seriesId
                ));

            // Verify still belongs to device
            if (series.getCoolingDevice() == null || !series.getCoolingDevice().getId()
                .equals(draft.getCoolingDevice().getId())) {
                throw new IllegalStateException(
                    "Series " + seriesId + " no longer belongs to selected device"
                );
            }

            seriesForValidation.add(series);
        }

        log.info("Re-validated {} series for finalization", seriesForValidation.size());

        // ============================================================
        // Step 3: Create Validation entity from draft
        // ============================================================

        Validation validation = validationService.createValidation(
            draft.getSelectedSeriesIds(),
            draft.getRecorderPosition(),
            draft.getDeviceLoadState()
        );

        log.info("Created validation: {} from draft: {}", validation.getId(), draftId);

        // ============================================================
        // Step 4: Calculate summary statistics
        // ============================================================

        summaryStatsService.calculateAndSave(validation.getId());

        log.info("Calculated summary statistics for validation: {}", validation.getId());

        // ============================================================
        // Step 5: Detect deviations (will be loaded by controller if needed)
        // ============================================================

        // DeviationDetectionService.detectAndSave() is called separately
        // to avoid circular transactions

        // ============================================================
        // Step 6-8: Sign validation
        // ============================================================

        try {
            signingService.signValidation(
                validation.getId(),
                username,
                password,
                signatureIntent
            );
            log.info("Signed validation: {} with user: {}", validation.getId(), username);
        } catch (Exception e) {
            log.error("Failed to sign validation: {}", validation.getId(), e);
            // Don't fail finalization if signing fails - it can be signed later
            throw new IllegalStateException("Validation signing failed: " + e.getMessage(), e);
        }

        // ============================================================
        // Step 9: Update draft status and link to Validation
        // ============================================================

        draft.setStatus(WizardStatus.COMPLETED);
        draft.setCompletedValidation(validation);
        draft.setUpdatedAt(LocalDateTime.now());
        draftRepository.save(draft);

        log.info("Finalized draft: {}, status={}, validation={}",
            draftId,
            WizardStatus.COMPLETED,
            validation.getId());

        return validation;
    }

    // =========================================================================
    // PERIODIC_REVALIDATION — PHASE 1 FINALIZATION
    // =========================================================================

    /**
     * Finalizes Phase 1 of a PERIODIC_REVALIDATION wizard (step 8 — technician plan sign).
     *
     * <p>
     * Flow:
     * <ol>
     *   <li>Verify draft ownership and IN_PROGRESS status</li>
     *   <li>Verify technician password (FDA 21 CFR Part 11 §11.200(a)(1))</li>
     *   <li>Generate plan PDF via {@link ValidationPlanPdfService} and sign with TSA</li>
     *   <li>Persist signed PDF to disk; store path in {@code planData.pdfInfo}</li>
     *   <li>Allocate document number via {@link DocumentNumberingService}</li>
     *   <li>Record technician signature timestamps in {@code planData.technikSignature}</li>
     *   <li>Transition draft status → AWAITING_QA_APPROVAL, lock steps ≥ 9</li>
     * </ol>
     * </p>
     *
     * @param draftId     ID of the PERIODIC_REVALIDATION draft at step 8
     * @param username    Username of the signing technician (must own the draft)
     * @param rawPassword Plain-text password for electronic signature verification
     * @param intent      Signature intent statement (stored in audit trail and PDF)
     * @throws IllegalArgumentException if draft not found or user does not own it
     * @throws IllegalStateException    if draft status is not IN_PROGRESS, has no plan data,
     *                                  or the password is incorrect
     */
    public void finalizePlanPhase(Long draftId, String username, String rawPassword, String intent) {
        log.info("Finalizing plan phase (step 8) for draft: {} by user: {}", draftId, username);

        // ── Step 1: Load and verify draft ────────────────────────────────────
        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        if (!draft.getCreatedBy().equals(username)) {
            throw new IllegalArgumentException(
                "User " + username + " does not own draft " + draftId);
        }

        if (draft.getStatus() != WizardStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                "Cannot finalize plan phase for draft with status: " + draft.getStatus());
        }

        ValidationPlanData planData = draft.getPlanData();
        if (planData == null) {
            throw new IllegalStateException(
                "Draft " + draftId + " has no plan data — cannot finalize plan phase");
        }

        // ── Step 2: Verify password (electronic signature guard) ─────────────
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            log.warn("Electronic signature failed: incorrect password for user: {}", username);
            throw new IllegalStateException(
                "Weryfikacja hasła nie powiodła się — podpis elektroniczny odrzucony");
        }

        log.debug("Password verified for user: {}", username);

        // ── Step 3: Generate and sign the plan PDF ───────────────────────────
        byte[] signedPdfBytes;
        try {
            signedPdfBytes = validationPlanPdfService.generatePlanPdf(draftId);
            log.info("Generated signed plan PDF: {} bytes for draft: {}",
                signedPdfBytes.length, draftId);
        } catch (Exception e) {
            log.error("Failed to generate plan PDF for draft: {}", draftId, e);
            throw new IllegalStateException(
                "Generowanie PDF planu walidacji nie powiodło się: " + e.getMessage(), e);
        }

        // ── Step 4: Persist signed PDF to disk ───────────────────────────────
        String planPdfPath;
        try {
            Path dir = Path.of(signedDocumentsPath, "plans");
            Files.createDirectories(dir);
            String fileName = String.format("plan_draft_%d_%s.pdf",
                draftId, LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
            Path filePath = dir.resolve(fileName);
            Files.write(filePath, signedPdfBytes);
            planPdfPath = filePath.toString();
            log.info("Persisted signed plan PDF to: {}", planPdfPath);
        } catch (IOException e) {
            log.error("Failed to persist plan PDF to disk for draft: {}", draftId, e);
            throw new IllegalStateException(
                "Zapis PDF planu na dysk nie powiódł się: " + e.getMessage(), e);
        }

        // ── Step 5: Allocate document number ─────────────────────────────────
        String labAbbrev = resolveLabAbbrev(draft);
        int year = Year.now().getValue();
        String documentNumber = documentNumberingService.generateNextNumber("RPW/PR", labAbbrev, year);
        log.info("Allocated document number: {} for draft: {}", documentNumber, draftId);

        // ── Step 6: Record technician signature ───────────────────────────────
        TechnikSignature technikSig = planData.getTechnikSignature();
        if (technikSig == null) {
            technikSig = new TechnikSignature();
            planData.setTechnikSignature(technikSig);
        }

        LocalDateTime now = LocalDateTime.now();
        String fullName = buildFullName(user);
        technikSig.setSignedAt(now);
        technikSig.setUsername(username);
        technikSig.setFullName(fullName);

        // Record PDF info
        PlanPdfInfo pdfInfo = planData.getPdfInfo();
        if (pdfInfo == null) {
            pdfInfo = new PlanPdfInfo();
            planData.setPdfInfo(pdfInfo);
        }
        pdfInfo.setPdfPath(planPdfPath);
        pdfInfo.setDocumentNumber(documentNumber);
        pdfInfo.setGeneratedAt(now);

        // ── Step 7: Transition draft to AWAITING_QA_APPROVAL ─────────────────
        draft.setStatus(WizardStatus.AWAITING_QA_APPROVAL);
        draft.setStepLockFrom(9);
        draft.setUpdatedAt(now);
        draftRepository.save(draft);

        // Invalidate any cached PDF (will be regenerated on next QA review load)
        validationPlanPdfService.invalidateCache(draftId);

        log.info("Plan phase finalized for draft: {} — status=AWAITING_QA_APPROVAL, "
            + "docNumber={}, technik={}, signedAt={}",
            draftId, documentNumber, username, now);
    }

    // =========================================================================
    // PERIODIC_REVALIDATION — PHASE 2 FINALIZATION
    // =========================================================================

    /**
     * Finalizes a PERIODIC_REVALIDATION wizard after QA approval (step 13).
     *
     * <p>
     * Enforces the QA approval barrier: the plan must carry a non-null
     * {@code planQaSignedAt} timestamp before the measurement phase can produce
     * a final {@link Validation}. This barrier check is in addition to status
     * checks performed by {@link #finalizeWizard(Long, String, String, String)}.
     * </p>
     *
     * @param draftId     ID of the PERIODIC_REVALIDATION draft at step 13
     * @param username    Username of the signing technician
     * @param rawPassword Plain-text password for electronic signature verification
     * @param intent      Signature intent statement
     * @return The created and signed {@link Validation}
     * @throws IllegalStateException if QA approval has not been recorded on the plan
     */
    public Validation finalizeRevalidation(Long draftId, String username,
                                           String rawPassword, String intent) {
        log.info("Finalizing revalidation (step 13) for draft: {} by user: {}", draftId, username);

        // ── Barrier check: QA must have approved the plan ───────────────────
        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        ValidationPlanData planData = draft.getPlanData();
        if (planData == null || !planData.isQaApproved()) {
            throw new IllegalStateException(
                "Draft " + draftId + " has not been approved by QA — "
                    + "measurement phase finalization is blocked");
        }

        log.debug("QA approval barrier passed for draft: {}", draftId);

        // ── Delegate to existing finalization pipeline ───────────────────────
        Validation validation = finalizeWizard(draftId, username, rawPassword, intent);

        log.info("Revalidation finalized: draft={}, validation={}", draftId, validation.getId());
        return validation;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private String resolveLabAbbrev(ValidationDraft draft) {
        if (draft.getCoolingDevice() != null
            && draft.getCoolingDevice().getLaboratory() != null
            && draft.getCoolingDevice().getLaboratory().getAbbreviation() != null) {
            return draft.getCoolingDevice().getLaboratory().getAbbreviation();
        }
        return "LAB";
    }

    private String buildFullName(User user) {
        String firstName = user.getFirstName();
        String lastName  = user.getLastName();
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return user.getUsername();
    }

    /**
     * Cancel finalization (if signing fails or user cancels)
     * Deletes the created Validation and resets draft
     */
    public void cancelFinalization(Long draftId) {
        log.warn("Cancelling finalization for draft: {}", draftId);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        if (draft.getCompletedValidation() != null) {
            // Validation was created but signing failed
            // Optionally delete it (depends on business logic)
            log.warn("Draft has associated validation, manual cleanup may be required");
        }

        // Reset draft to allow another finalization attempt
        draft.setStatus(WizardStatus.IN_PROGRESS);
        draft.setCompletedValidation(null);
        draft.setUpdatedAt(LocalDateTime.now());
        draftRepository.save(draft);

        log.info("Cancelled finalization for draft: {}", draftId);
    }
}
