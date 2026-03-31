package com.mac.bry.validationsystem.wizard.service;

import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import com.mac.bry.validationsystem.security.service.AuditService;
import com.mac.bry.validationsystem.wizard.ValidationDraft;
import com.mac.bry.validationsystem.wizard.ValidationDraftRepository;
import com.mac.bry.validationsystem.wizard.ValidationPlanData;
import com.mac.bry.validationsystem.wizard.ValidationPlanDataRepository;
import com.mac.bry.validationsystem.wizard.WizardStatus;
import com.mac.bry.validationsystem.wizard.dto.DeviationProceduresDto;
import com.mac.bry.validationsystem.wizard.dto.MappingStatusDto;
import com.mac.bry.validationsystem.wizard.dto.PlanCriteriaDto;
import com.mac.bry.validationsystem.wizard.dto.ValidationPlanDataDto;
import com.mac.bry.validationsystem.wizard.plandata.QaApprovalMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implementation of ValidationPlanDataService.
 *
 * Manages validation plan data for PERIODIC_REVALIDATION procedure type.
 * Handles plan creation, modification, technik signing, and QA approval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ValidationPlanDataServiceImpl implements ValidationPlanDataService {

    private final ValidationPlanDataRepository planDataRepository;
    private final ValidationDraftRepository draftRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Override
    @Transactional
    public ValidationPlanData initializePlanData(Long draftId) {
        log.info("Initializing plan data for draft ID: {}", draftId);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        if (draft.getPlanData() != null) {
            log.debug("Plan data already exists for draft {}, returning existing", draftId);
            return draft.getPlanData();
        }

        ValidationPlanData newPlan = ValidationPlanData.builder()
            .build();

        ValidationPlanData saved = planDataRepository.save(newPlan);
        draft.setPlanData(saved);
        draftRepository.save(draft);

        log.info("Created new plan data with ID: {}", saved.getId());
        return saved;
    }

    @Override
    public Optional<ValidationPlanData> findById(Long planDataId) {
        return planDataRepository.findById(planDataId);
    }

    @Override
    public Optional<ValidationPlanData> findByDraftId(Long draftId) {
        return planDataRepository.findByValidationDraftId(draftId);
    }

    @Override
    @Transactional
    public ValidationDraft savePlanDetails(Long draftId, ValidationPlanDataDto dto) {
        log.info("Saving plan details for draft ID: {}", draftId);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        ValidationPlanData plan = initializePlanData(draftId);

        // Hibernate 6 returns null for @Embedded when all columns are null,
        // so we need to lazily init the embedded PlanDetails VO
        if (plan.getDetails() == null) {
            plan.setDetails(new com.mac.bry.validationsystem.wizard.plandata.PlanDetails());
        }

        // Map DTO fields to PlanDetails Value Object
        plan.getDetails().setRevalidationReason(dto.getRevalidationReason());
        plan.getDetails().setPreviousValidationDate(dto.getPreviousValidationDate());
        plan.getDetails().setPreviousValidationNumber(dto.getPreviousValidationNumber());

        planDataRepository.save(plan);
        log.debug("Plan details saved for draft {}", draftId);

        return draft;
    }

    @Override
    @Transactional
    public ValidationDraft saveMappingStatus(Long draftId, MappingStatusDto mappingStatus) {
        log.info("Saving mapping status for draft ID: {}", draftId);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        ValidationPlanData plan = initializePlanData(draftId);

        if (plan.getMappingInfo() == null) {
            plan.setMappingInfo(new com.mac.bry.validationsystem.wizard.plandata.MappingInfo());
        }

        plan.getMappingInfo().setMappingCheckDate(mappingStatus.getMappingCheckDate());
        plan.getMappingInfo().setMappingStatus(mappingStatus.getMappingStatus());
        plan.getMappingInfo().setMappingOverdueAcknowledged(
            Boolean.TRUE.equals(mappingStatus.getMappingOverdueAcknowledged()));

        // Manual entry fields
        plan.getMappingInfo().setLastMappingDateManual(mappingStatus.getLastMappingDateManual());
        plan.getMappingInfo().setMappingProtocolNumberManual(mappingStatus.getMappingProtocolNumberManual());
        plan.getMappingInfo().setMappingValidUntilManual(mappingStatus.getMappingValidUntilManual());
        plan.getMappingInfo().setSensorCountManual(mappingStatus.getSensorCountManual());
        plan.getMappingInfo().setControllerSensorLocationManual(mappingStatus.getControllerSensorLocationManual());
        plan.getMappingInfo().setHotSpotLocationManual(mappingStatus.getHotSpotLocationManual());
        plan.getMappingInfo().setColdSpotLocationManual(mappingStatus.getColdSpotLocationManual());

        planDataRepository.save(plan);
        log.debug("Mapping status saved for draft {}: {}", draftId, mappingStatus.getMappingStatus());

        return draft;
    }

    @Override
    @Transactional
    public ValidationDraft savePlanCriteria(Long draftId, PlanCriteriaDto criteria) {
        log.info("Saving plan criteria for draft ID: {}", draftId);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        ValidationPlanData plan = initializePlanData(draftId);

        if (plan.getAcceptanceCriteria() == null) {
            plan.setAcceptanceCriteria(new com.mac.bry.validationsystem.wizard.plandata.PlanAcceptanceCriteria());
        }

        plan.getAcceptanceCriteria().setPlanDeviceLoadState(criteria.getPlanDeviceLoadState());
        plan.getAcceptanceCriteria().setPlanNominalTemp(criteria.getPlanNominalTemp());
        plan.getAcceptanceCriteria().setPlanAcceptanceTempMin(criteria.getPlanAcceptanceTempMin());
        plan.getAcceptanceCriteria().setPlanAcceptanceTempMax(criteria.getPlanAcceptanceTempMax());
        plan.getAcceptanceCriteria().setPlanMktMaxTemp(criteria.getPlanMktMaxTemp());
        plan.getAcceptanceCriteria().setPlanUniformityDeltaMax(criteria.getPlanUniformityDeltaMax());
        plan.getAcceptanceCriteria().setPlanDriftMaxTemp(criteria.getPlanDriftMaxTemp());

        planDataRepository.save(plan);
        log.debug("Plan criteria saved for draft {}", draftId);

        return draft;
    }

    @Override
    @Transactional
    public ValidationDraft saveDeviationProcedures(Long draftId, DeviationProceduresDto procedures) {
        log.info("Saving deviation procedures for draft ID: {}", draftId);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        ValidationPlanData plan = initializePlanData(draftId);

        if (plan.getDeviationProcedures() == null) {
            plan.setDeviationProcedures(new com.mac.bry.validationsystem.wizard.plandata.DeviationProcedures());
        }

        plan.getDeviationProcedures().setCriticalText(procedures.getPlanDeviationCriticalText());
        plan.getDeviationProcedures().setMajorText(procedures.getPlanDeviationMajorText());
        plan.getDeviationProcedures().setMinorText(procedures.getPlanDeviationMinorText());

        planDataRepository.save(plan);
        log.debug("Deviation procedures saved for draft {}", draftId);

        return draft;
    }

    @Override
    @Transactional
    public ValidationDraft signPlanAsTechnician(Long draftId, String username, String rawPassword) {
        log.info("Signing plan as technician - draft ID: {}, username: {}", draftId, username);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        // Verify user exists and password matches (FDA 21 CFR Part 11 electronic signature)
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            log.warn("Password mismatch for user {} during plan signature", username);
            throw new IllegalArgumentException("Invalid password");
        }

        ValidationPlanData plan = initializePlanData(draftId);

        if (plan.getTechnikSignature() == null) {
            plan.setTechnikSignature(new com.mac.bry.validationsystem.wizard.plandata.TechnikSignature());
        }

        plan.getTechnikSignature().setSignedAt(LocalDateTime.now());
        plan.getTechnikSignature().setUsername(username);
        plan.getTechnikSignature().setFullName(user.getFullName());

        planDataRepository.save(plan);

        // Transition draft to AWAITING_QA_APPROVAL
        draft.setStatus(WizardStatus.AWAITING_QA_APPROVAL);
        draft.setStepLockFrom(9); // Lock measurement phase until QA approves
        draftRepository.save(draft);

        log.info("Plan signed by technician {} and draft transitioned to AWAITING_QA_APPROVAL", username);

        auditService.logOperation("ValidationPlanData", plan.getId(), "PLAN_SIGN_TECHNICIAN", null,
            java.util.Map.of("draftId", draftId, "username", username));

        return draft;
    }

    @org.springframework.beans.factory.annotation.Value("${app.qa.scans-path:./uploads/qa_scans}")
    private String scansPath;

    @Override
    @Transactional
    public ValidationDraft approvePlanAsQa(Long draftId, String qaUsername, String rawPassword) {
        log.info("Approving plan as QA - draft ID: {}, QA username: {}", draftId, qaUsername);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        ValidationPlanData plan = draft.getPlanData();
        if (plan == null) {
            throw new IllegalArgumentException("Plan data not found for draft: " + draftId);
        }

        // Verify QA user exists and password matches (FDA 21 CFR Part 11 co-signature)
        User qaUser = userRepository.findByUsername(qaUsername)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + qaUsername));

        if (!passwordEncoder.matches(rawPassword, qaUser.getPassword())) {
            log.warn("Password mismatch for QA user {} during plan approval", qaUsername);
            throw new IllegalArgumentException("Invalid password");
        }

        // Verify 2-person rule: QA cannot be same as technik
        String technikUsername = plan.getTechnikSignature().getUsername();
        if (qaUsername.equals(technikUsername)) {
            log.warn("Attempt to self-approve plan by same user {}", qaUsername);
            throw new IllegalArgumentException("QA approver cannot be the same person as technician (2-person rule)");
        }

        if (plan.getQaApproval() == null) {
            plan.setQaApproval(new com.mac.bry.validationsystem.wizard.plandata.QaApprovalPath());
        }

        plan.getQaApproval().setApprovalMethod(QaApprovalMethod.ELECTRONIC_SIGNATURE);
        plan.getQaApproval().setElectronicSignedAt(LocalDateTime.now());
        plan.getQaApproval().setElectronicUsername(qaUsername);
        plan.getQaApproval().setElectronicFullName(qaUser.getFullName());

        planDataRepository.save(plan);

        // Transition draft back to IN_PROGRESS, unblock step 9
        draft.setStatus(WizardStatus.IN_PROGRESS);
        draft.setCurrentStep(9);
        draft.setStepLockFrom(null);
        draftRepository.save(draft);

        log.info("Plan approved by QA {} and draft transitioned to IN_PROGRESS at step 9", qaUsername);

        auditService.logOperation("ValidationPlanData", plan.getId(), "PLAN_APPROVE_QA", null,
            java.util.Map.of("draftId", draftId, "qaUsername", qaUsername, "method", "ELECTRONIC"));

        return draft;
    }

    @Override
    @Transactional
    public ValidationDraft approvePlanExternal(Long draftId, String technicianUsername, org.springframework.web.multipart.MultipartFile scan) {
        log.info("Approving plan via external scan - draft ID: {}, tech: {}", draftId, technicianUsername);

        ValidationDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        ValidationPlanData plan = draft.getPlanData();
        if (plan == null) {
            throw new IllegalArgumentException("Plan data not found for draft: " + draftId);
        }

        if (scan == null || scan.isEmpty()) {
            throw new IllegalArgumentException("Scan file is required for external approval");
        }

        try {
            // Create directory if not exists
            java.nio.file.Path uploadDir = java.nio.file.Paths.get(scansPath);
            if (!java.nio.file.Files.exists(uploadDir)) {
                java.nio.file.Files.createDirectories(uploadDir);
            }

            // Generate filename: QA_SCAN_{draftId}_{timestamp}.pdf
            String originalFilename = scan.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".pdf";
            String filename = "QA_SCAN_" + draftId + "_" + System.currentTimeMillis() + extension;
            java.nio.file.Path targetPath = uploadDir.resolve(filename);

            // Copy file
            java.nio.file.Files.copy(scan.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            if (plan.getQaApproval() == null) {
                plan.setQaApproval(new com.mac.bry.validationsystem.wizard.plandata.QaApprovalPath());
            }

            // Update plan data
            plan.getQaApproval().setApprovalMethod(QaApprovalMethod.SCANNED_DOCUMENT);
            plan.getQaApproval().setScannedDocumentPath(targetPath.toString());
            plan.getQaApproval().setScannedUploadedAt(LocalDateTime.now());
            plan.getQaApproval().setScannedUploadedBy(technicianUsername);

            planDataRepository.save(plan);

            // Transition draft back to IN_PROGRESS, unblock step 9
            draft.setStatus(WizardStatus.IN_PROGRESS);
            draft.setCurrentStep(9);
            draft.setStepLockFrom(null);
            draftRepository.save(draft);

            log.info("Plan approved via external scan {} by {} and draft transitioned to IN_PROGRESS at step 9",
                    filename, technicianUsername);

            auditService.logOperation("ValidationPlanData", plan.getId(), "PLAN_APPROVE_QA", null,
                java.util.Map.of("draftId", draftId, "uploadedBy", technicianUsername, "method", "EXTERNAL_SCAN", "fileName", filename));

            return draft;

        } catch (java.io.IOException e) {
            log.error("Failed to save QA scan for draft {}: {}", draftId, e.getMessage());
            throw new RuntimeException("Nie udało się zapisać skanu podpisu: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ValidationDraft rejectPlan(Long draftId, String qaUsername, String rejectionReason) {
        log.info("Rejecting plan - draft ID: {}, QA username: {}", draftId, qaUsername);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        ValidationPlanData plan = draft.getPlanData();
        if (plan == null) {
            throw new IllegalArgumentException("Plan data not found for draft: " + draftId);
        }

        // Verify QA user exists — rejection does not require password (audit trail only)
        userRepository.findByUsername(qaUsername)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + qaUsername));

        if (plan.getRejectionTrail() == null) {
            plan.setRejectionTrail(new com.mac.bry.validationsystem.wizard.plandata.RejectionAuditTrail());
        }

        int previousCount = plan.getRejectionTrail().getRejectionAttemptCount() != null
            ? plan.getRejectionTrail().getRejectionAttemptCount() : 0;

        plan.getRejectionTrail().setRejectionReason(rejectionReason);
        plan.getRejectionTrail().setRejectedAt(LocalDateTime.now());
        plan.getRejectionTrail().setRejectedByUsername(qaUsername);
        plan.getRejectionTrail().setRejectionAttemptCount(previousCount + 1);

        planDataRepository.save(plan);

        // Return draft to step 8 for revision
        draft.setCurrentStep(8);
        draft.setStatus(WizardStatus.IN_PROGRESS);
        draftRepository.save(draft);

        log.info("Plan rejected by QA {}. Reason: {}. Draft returned to step 8", qaUsername, rejectionReason);

        auditService.logOperation("ValidationPlanData", plan.getId(), "PLAN_REJECT_QA", null,
            java.util.Map.of("draftId", draftId, "qaUsername", qaUsername, "reason", rejectionReason));

        return draft;
    }

    @Override
    @Transactional
    public ValidationPlanData save(ValidationPlanData planData) {
        return planDataRepository.save(planData);
    }
}
