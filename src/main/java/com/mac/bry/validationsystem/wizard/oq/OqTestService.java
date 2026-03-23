package com.mac.bry.validationsystem.wizard.oq;

import com.mac.bry.validationsystem.wizard.ValidationDraft;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for managing OQ test results in wizard step 5.
 *
 * <p>
 * Handles:
 * - Power failure test (Awaria zasilania)
 * - Alarm verification (Weryfikacja alarmu)
 * - Door lock test (Test drzwi)
 * </p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OqTestService {

    private final OqTestResultRepository oqTestResultRepository;

    /**
     * Get or create OQ test result for a draft
     * @param draftId Draft ID
     * @param createdBy Username
     * @return OQ test result (created if not exists)
     */
    public OqTestResult getOrCreateOqTestResult(Long draftId, String createdBy) {
        log.debug("Getting or creating OQ test result for draft: {}", draftId);

        Optional<OqTestResult> existing = oqTestResultRepository.findByValidationDraftId(draftId);

        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new result
        ValidationDraft draft = new ValidationDraft();
        draft.setId(draftId);

        OqTestResult result = OqTestResult.builder()
            .validationDraft(draft)
            .createdBy(createdBy)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        return oqTestResultRepository.save(result);
    }

    /**
     * Find OQ test result by draft
     */
    public Optional<OqTestResult> findByDraftId(Long draftId) {
        return oqTestResultRepository.findByValidationDraftId(draftId);
    }

    /**
     * Save power failure test result
     */
    public OqTestResult savePowerFailureTest(Long draftId, Boolean passed, String notes, String username) {
        log.debug("Saving power failure test for draft: {}", draftId);

        OqTestResult result = findByDraftId(draftId)
            .orElseThrow(() -> new IllegalArgumentException("OQ test result not found for draft: " + draftId));

        result.setPowerFailureTestPassed(passed);
        result.setPowerFailureNotes(notes);
        result.setUpdatedAt(LocalDateTime.now());

        return oqTestResultRepository.save(result);
    }

    /**
     * Save alarm verification test result
     */
    public OqTestResult saveAlarmTest(Long draftId, Boolean passed, String notes, String username) {
        log.debug("Saving alarm test for draft: {}", draftId);

        OqTestResult result = findByDraftId(draftId)
            .orElseThrow(() -> new IllegalArgumentException("OQ test result not found for draft: " + draftId));

        result.setAlarmTestPassed(passed);
        result.setAlarmTestNotes(notes);
        result.setUpdatedAt(LocalDateTime.now());

        return oqTestResultRepository.save(result);
    }

    /**
     * Save door lock test result
     */
    public OqTestResult saveDoorTest(Long draftId, Boolean passed, String notes, String username) {
        log.debug("Saving door test for draft: {}", draftId);

        OqTestResult result = findByDraftId(draftId)
            .orElseThrow(() -> new IllegalArgumentException("OQ test result not found for draft: " + draftId));

        result.setDoorTestPassed(passed);
        result.setDoorTestNotes(notes);
        result.setUpdatedAt(LocalDateTime.now());

        return oqTestResultRepository.save(result);
    }

    /**
     * Check if all OQ tests are completed
     */
    public boolean areAllTestsCompleted(Long draftId) {
        Optional<OqTestResult> result = findByDraftId(draftId);
        if (!result.isPresent()) {
            return false;
        }
        return result.get().allTestsCompleted();
    }

    /**
     * Check if all OQ tests passed
     */
    public boolean haveAllTestsPassed(Long draftId) {
        Optional<OqTestResult> result = findByDraftId(draftId);
        if (!result.isPresent()) {
            return false;
        }
        return result.get().allTestsPassed();
    }
}
