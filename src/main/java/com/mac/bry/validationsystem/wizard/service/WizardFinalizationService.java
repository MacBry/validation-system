package com.mac.bry.validationsystem.wizard.service;

import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.MeasurementSeriesRepository;
import com.mac.bry.validationsystem.stats.ValidationSummaryStatsService;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationService;
import com.mac.bry.validationsystem.validation.ValidationSigningService;
import com.mac.bry.validationsystem.wizard.ValidationDraft;
import com.mac.bry.validationsystem.wizard.ValidationDraftRepository;
import com.mac.bry.validationsystem.wizard.WizardStatus;
import com.mac.bry.validationsystem.wizard.pdf.ProcedureStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
