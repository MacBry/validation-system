package com.mac.bry.validationsystem.wizard.service;

import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.device.CoolingDeviceRepository;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.MeasurementSeriesRepository;
import com.mac.bry.validationsystem.measurement.RecorderPosition;
import com.mac.bry.validationsystem.validation.DeviceLoadState;
import com.mac.bry.validationsystem.wizard.ValidationDraft;
import com.mac.bry.validationsystem.wizard.ValidationDraftRepository;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.UserPermissionsCache;
import com.mac.bry.validationsystem.wizard.ValidationProcedureType;
import com.mac.bry.validationsystem.wizard.WizardStatus;
import com.mac.bry.validationsystem.wizard.criteria.CustomAcceptanceCriterion;
import com.mac.bry.validationsystem.wizard.criteria.CustomAcceptanceCriterionDto;
import com.mac.bry.validationsystem.wizard.criteria.CustomAcceptanceCriterionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of ValidationDraftService.
 *
 * <p>
 * Manages multi-step validation wizard workflow with proper state transitions,
 * step locking, and authorization checks.
 * </p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ValidationDraftServiceImpl implements ValidationDraftService {

    private final ValidationDraftRepository draftRepository;
    private final CustomAcceptanceCriterionRepository criterionRepository;
    private final CoolingDeviceRepository coolingDeviceRepository;
    private final MeasurementSeriesRepository measurementSeriesRepository;
    private final UserRepository userRepository;

    @Override
    public ValidationDraft createDraft(String username) {
        log.info("Creating new validation draft for user: {}", username);

        ValidationDraft draft = ValidationDraft.builder()
            .createdBy(username)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .status(WizardStatus.IN_PROGRESS)
            .currentStep(1)
            .procedureType(ValidationProcedureType.MAPPING)
            .stepLockFrom(null)
            .selectedSeriesIds(new ArrayList<>())
            .build();

        ValidationDraft saved = draftRepository.save(draft);
        log.info("Created draft with ID: {} for user: {}", saved.getId(), username);
        return saved;
    }

    @Override
    public Optional<ValidationDraft> getDraft(Long draftId, String username, java.util.Collection<String> roles) {
        log.debug("Getting draft {} for user {} with roles: {}", draftId, username, roles);
        
        Optional<ValidationDraft> draftOpt = draftRepository.findById(draftId);
        if (draftOpt.isEmpty()) {
            return Optional.empty();
        }
        
        ValidationDraft draft = draftOpt.get();
        
        // 1. Właściciel zawsze ma dostęp
        if (draft.getCreatedBy().equals(username)) {
            return draftOpt;
        }
        
        // 2. QA ma dostęp do planów w swojej firmie (szczególnie tych oczekujących na zatwierdzenie lub po nim)
        if (roles.contains("ROLE_QA") || roles.contains("QA")) {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isPresent()) {
                UserPermissionsCache cache = userOpt.get().getPermissionsCache();
                if (cache != null && cache.getAllowedCompanyIds() != null) {
                    Long companyId = draft.getCoolingDevice().getDepartment().getCompany().getId();
                    if (cache.getAllowedCompanyIds().contains(companyId)) {
                        log.info("Access granted to draft {} for QA user {}", draftId, username);
                        return draftOpt;
                    }
                } else if (cache == null) {
                    // Admin/Global QA bez cache - dostęp do wszystkiego
                    log.info("Access granted to draft {} for QA/Admin user {} (no cache)", draftId, username);
                    return draftOpt;
                }
            }
        }
        
        log.warn("Access denied to draft {} for user {}", draftId, username);
        return Optional.empty();
    }

    @Override
    public Optional<ValidationDraft> getDraftById(Long draftId) {
        return draftRepository.findById(draftId);
    }

    @Override
    public List<ValidationDraft> findActiveDraftsForUser(String username, java.util.Collection<String> roles) {
        log.info("Finding active drafts for user: {} with roles: {}", username, roles);

        // 1. Drafty utworzone przez użytkownika (W toku LUB Oczekujące na QA)
        List<WizardStatus> userDraftStatuses = List.of(WizardStatus.IN_PROGRESS, WizardStatus.AWAITING_QA_APPROVAL);
        List<ValidationDraft> drafts = new ArrayList<>(draftRepository.findByCreatedByAndStatusIn(username, userDraftStatuses));

        // 2. Jeśli QA, dodaj drafty innych osób oczekujące na zatwierdzenie (filtrowane po uprawnieniach do firm)
        if (roles.contains("ROLE_QA") || roles.contains("QA")) {
            log.debug("User is QA, searching for drafts awaiting approval...");
            
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                UserPermissionsCache cache = user.getPermissionsCache();
                
                if (cache != null && cache.getAllowedCompanyIds() != null && !cache.getAllowedCompanyIds().isEmpty()) {
                    List<ValidationDraft> qaDrafts = draftRepository.findByStatusAndCompanyIds(
                        WizardStatus.AWAITING_QA_APPROVAL, 
                        cache.getAllowedCompanyIds()
                    );
                    
                    // Dodaj tylko te których jeszcze nie ma na liście (stworzone przez kogoś innego)
                    for (ValidationDraft qaDraft : qaDrafts) {
                        if (!qaDraft.getCreatedBy().equals(username)) {
                            drafts.add(qaDraft);
                        }
                    }
                    log.info("Added {} drafts awaiting QA approval for user {}", qaDrafts.size(), username);
                } else {
                    // Jeśli brak cache (np. admin bez przypisanej firmy), pokazujemy wszystkie oczekujące? 
                    // Dla bezpieczeństwa lepiej tylko te z IN_PROGRESS, ale Administrator widzi wszystko.
                    log.warn("QA user {} has no company access cache, fetching all awaiting drafts", username);
                    List<ValidationDraft> allAwaiting = draftRepository.findByStatus(WizardStatus.AWAITING_QA_APPROVAL);
                    for (ValidationDraft qaDraft : allAwaiting) {
                        if (!qaDraft.getCreatedBy().equals(username)) {
                            drafts.add(qaDraft);
                        }
                    }
                }
            }
        }

        return drafts;
    }

    @Override
    public List<ValidationDraft> findAllDraftsForUser(String username) {
        return draftRepository.findByCreatedBy(username);
    }

    @Override
    public ValidationDraft saveStep1(Long draftId, ValidationProcedureType procedureType) {
        log.info("Saving step 1 (procedure type: {}) for draft: {}", procedureType, draftId);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        draft.setProcedureType(procedureType);
        draft.setCurrentStep(2);
        draft.setUpdatedAt(LocalDateTime.now());

        ValidationDraft saved = draftRepository.save(draft);
        log.info("Step 1 saved for draft: {}, moving to step 2", draftId);
        return saved;
    }

    @Override
    public ValidationDraft saveStep2(Long draftId, Long coolingDeviceId) {
        log.info("Saving step 2 (device: {}) for draft: {}", coolingDeviceId, draftId);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        CoolingDevice device = coolingDeviceRepository.findById(coolingDeviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device not found: " + coolingDeviceId));

        draft.setCoolingDevice(device);
        draft.setCurrentStep(3);
        draft.setUpdatedAt(LocalDateTime.now());

        ValidationDraft saved = draftRepository.save(draft);
        log.info("Step 2 saved for draft: {} with device: {}, moving to step 3", draftId, coolingDeviceId);
        return saved;
    }

    @Override
    public ValidationDraft saveStep3(Long draftId, List<CustomAcceptanceCriterionDto> criteria) {
        log.info("Saving step 3 (custom criteria count: {}) for draft: {}", criteria.size(), draftId);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        // Delete existing criteria
        criterionRepository.deleteByValidationDraftId(draftId);

        // Create and save new criteria
        List<CustomAcceptanceCriterion> criteriaEntities = new ArrayList<>();
        for (int i = 0; i < criteria.size(); i++) {
            CustomAcceptanceCriterionDto dto = criteria.get(i);
            CustomAcceptanceCriterion entity = CustomAcceptanceCriterion.builder()
                .validationDraft(draft)
                .fieldName(dto.getFieldName())
                .operator(dto.getOperator())
                .limitValue(dto.getLimitValue() != null ? new java.math.BigDecimal(dto.getLimitValue()) : null)
                .unit(dto.getUnit() != null ? dto.getUnit() : dto.getFieldName().getUnit())
                .displayOrder(i)
                .isStandard(false)
                .createdAt(LocalDateTime.now())
                .build();
            criteriaEntities.add(entity);
        }
        criterionRepository.saveAll(criteriaEntities);

        // Lock steps 2-3 after defining criteria
        draft.setStepLockFrom(3);
        draft.setCurrentStep(4);
        draft.setUpdatedAt(LocalDateTime.now());

        ValidationDraft saved = draftRepository.save(draft);
        log.info("Step 3 saved for draft: {}, {} criteria stored, locked steps 2-3, moving to step 4",
            draftId, criteria.size());
        return saved;
    }

    @Override
    public ValidationDraft saveStep4(Long draftId, DeviceLoadState loadState, RecorderPosition recorderPosition) {
        log.info("Saving step 4 (load state: {}, position: {}) for draft: {}",
            loadState, recorderPosition, draftId);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        draft.setDeviceLoadState(loadState);
        draft.setRecorderPosition(recorderPosition);
        draft.setCurrentStep(5);
        draft.setUpdatedAt(LocalDateTime.now());

        ValidationDraft saved = draftRepository.save(draft);
        log.info("Step 4 saved for draft: {}, moving to step 5", draftId);
        return saved;
    }

    @Override
    public ValidationDraft navigateToStep(Long draftId, int targetStep) {
        log.info("Navigating draft: {} to step: {}", draftId, targetStep);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        // Check if navigation is blocked by lock
        if (!canNavigateBack(draft, targetStep)) {
            throw new IllegalStateException(
                "Cannot navigate back to step " + targetStep +
                "; steps 2-3 are locked after defining acceptance criteria (stepLockFrom=" + draft.getStepLockFrom() + ")"
            );
        }

        draft.setCurrentStep(targetStep);
        draft.setUpdatedAt(LocalDateTime.now());

        ValidationDraft saved = draftRepository.save(draft);
        log.info("Navigated draft: {} to step: {}", draftId, targetStep);
        return saved;
    }

    @Override
    public boolean canNavigateBack(ValidationDraft draft, int targetStep) {
        if (draft.getStepLockFrom() == null) {
            return true; // No lock
        }
        // Can navigate to/past lock point, but not back into locked region
        return targetStep >= draft.getStepLockFrom();
    }

    @Override
    public ValidationDraft moveToNextStep(Long draftId) {
        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        int nextStep = Math.min(draft.getCurrentStep() + 1, 9);
        draft.setCurrentStep(nextStep);
        draft.setUpdatedAt(LocalDateTime.now());

        return draftRepository.save(draft);
    }

    @Override
    public ValidationDraft abandonDraft(Long draftId) {
        log.info("Abandoning draft: {}", draftId);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        draft.setStatus(WizardStatus.ABANDONED);
        draft.setUpdatedAt(LocalDateTime.now());

        return draftRepository.save(draft);
    }

    @Override
    public void deleteDraft(Long draftId) {
        log.warn("Deleting draft: {}", draftId);
        draftRepository.deleteById(draftId);
    }

    @Override
    public ValidationDraft saveStep6(Long draftId, List<Long> seriesIds) {
        log.info("Saving step 6 (series count: {}) for draft: {}", seriesIds.size(), draftId);

        ValidationDraft draft = validateAndSaveSeriesSelection(draftId, seriesIds);

        draft.setCurrentStep(7);
        draft.setUpdatedAt(LocalDateTime.now());

        ValidationDraft saved = draftRepository.save(draft);
        log.info("Step 6 saved for draft: {} with {} series, moving to step 7", draftId, seriesIds.size());
        return saved;
    }

    @Override
    public ValidationDraft validateAndSaveSeriesSelection(Long draftId, List<Long> seriesIds) {
        log.info("Validating {} series for draft: {}", seriesIds.size(), draftId);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        if (draft.getCoolingDevice() == null) {
            throw new IllegalStateException("Draft has no cooling device selected");
        }

        // Validate each series exists and belongs to the device
        for (Long seriesId : seriesIds) {
            MeasurementSeries series = measurementSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new IllegalArgumentException("Series not found: " + seriesId));

            // Check series belongs to this device
            if (series.getCoolingDevice() == null || !series.getCoolingDevice().getId().equals(draft.getCoolingDevice().getId())) {
                throw new IllegalArgumentException(
                    "Series " + seriesId + " does not belong to selected device " + draft.getCoolingDevice().getId()
                );
            }

            // Check series is not already used in another validation
            if (series.getUsedInValidation() != null && series.getUsedInValidation()) {
                throw new IllegalArgumentException(
                    "Series " + seriesId + " is already used in another validation"
                );
            }
        }

        draft.setSelectedSeriesIds(new ArrayList<>(seriesIds));
        return draft;
    }
}
