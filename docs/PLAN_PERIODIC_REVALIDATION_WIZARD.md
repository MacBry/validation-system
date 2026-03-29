# PERIODIC_REVALIDATION Wizard Flow — Implementation Plan

## Context
GMP Annex 15 §10 requires that periodic revalidation include a documented, QA-approved
validation plan **before** measurements begin. The existing wizard supports 3 procedure types
(OQ, PQ, MAPPING) with a 9-step flow. This plan adds a 4th type — `PERIODIC_REVALIDATION` —
as a **13-step, two-phase wizard** with an enforced QA co-signature barrier between the
planning phase (steps 1–8) and the measurement phase (steps 9–13).

---

## Phase Architecture

```
[Phase 1 — Validation Plan: Steps 1–8]
  → Technik completes plan, signs at step 8
  → Status: AWAITING_QA_APPROVAL (step locked ≥ 9)

[QA Approval: separate action, different user]
  → QA reviews plan PDF, signs
  → Status: IN_PROGRESS, currentStep = 9

[Phase 2 — Measurement Phase: Steps 9–13]
  → Step 9 upload BLOCKED unless status = IN_PROGRESS AND planData.planQaSignedAt ≠ null
  → Step 13: final technik signature → COMPLETED
```

---

## 1. New Database Migrations

### `V2.28.0__add_periodic_revalidation_type.sql`
```sql
-- Extend procedure_type ENUM
ALTER TABLE validation_drafts
  MODIFY COLUMN procedure_type
    ENUM('OQ','PQ','MAPPING','PERIODIC_REVALIDATION') NOT NULL;

-- Extend status ENUM
ALTER TABLE validation_drafts
  MODIFY COLUMN status
    ENUM('IN_PROGRESS','COMPLETED','ABANDONED','AWAITING_QA_APPROVAL') NOT NULL;

-- Relax step_lock_from CHECK — allow locking at step 9 for plan approval
ALTER TABLE validation_drafts DROP CONSTRAINT IF EXISTS chk_step_lock_from;
ALTER TABLE validation_drafts ADD CONSTRAINT chk_step_lock_from
  CHECK (step_lock_from IS NULL OR step_lock_from >= 2);
```

### `V2.28.1__validation_plan_data.sql`
```sql
CREATE TABLE validation_plan_data (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,

  -- ========== STEP 3: REVALIDATION REASON & REFERENCE ==========
  revalidation_reason TEXT,
  previous_validation_date DATE,
  previous_validation_number VARCHAR(100),

  -- ========== STEP 4: MAPPING STATUS (AUTO-FILLED) ==========
  mapping_check_date DATE,
  mapping_status VARCHAR(20),              -- CURRENT | OVERDUE | NEVER
  mapping_overdue_acknowledged BOOLEAN DEFAULT FALSE,  -- Technik acknowledged OVERDUE

  -- ========== STEPS 5–6: LOAD STATE & ACCEPTANCE CRITERIA ==========
  plan_acceptance_temp_min DOUBLE,
  plan_acceptance_temp_max DOUBLE,
  plan_mkt_max_temp DOUBLE,
  plan_uniformity_delta_max DOUBLE,
  plan_drift_max_temp DOUBLE,
  plan_nominal_temp DOUBLE,

  -- ========== STEP 7: DEVIATION PROCEDURES ==========
  plan_deviation_critical_text TEXT,
  plan_deviation_major_text TEXT,
  plan_deviation_minor_text TEXT,

  -- ========== STEP 8: TECHNIK SIGNATURE ==========
  plan_technik_signed_at DATETIME(6),
  plan_technik_username VARCHAR(50),
  plan_technik_full_name VARCHAR(200),

  -- ========== QA APPROVAL SIGNATURE (TWO PATHS) ==========
  -- Path A: Electronic signature in system
  plan_qa_signed_at DATETIME(6),
  plan_qa_username VARCHAR(50),
  plan_qa_full_name VARCHAR(200),

  -- Path B: Scanned signed document (QA outside system)
  plan_qa_approval_method VARCHAR(20),     -- 'ELECTRONIC_SIGNATURE' | 'SCANNED_DOCUMENT'
  plan_qa_scanned_pdf_path VARCHAR(500),   -- Path to scanned PDF (if upload)
  plan_qa_scanned_pdf_hash VARCHAR(256),   -- SHA256 hash for integrity (21 CFR Part 11)
  plan_qa_signer_name VARCHAR(200),        -- Signer name from scanned document
  plan_qa_signer_title VARCHAR(100),       -- Signer title (e.g., "Head of QA")
  plan_qa_approved_by_username VARCHAR(50), -- Internal user who approved upload
  plan_qa_approved_by_at DATETIME(6),      -- When upload was approved internally

  -- ========== AUDIT TRAIL: QA REJECTION ==========
  plan_qa_rejection_reason LONGTEXT,       -- Visible to Technik as "Komentarz QA do poprawy"
  plan_qa_rejected_at DATETIME(6),
  plan_qa_rejected_by_username VARCHAR(50),

  -- ========== GENERATED PLAN DOCUMENT ==========
  plan_pdf_path VARCHAR(500),
  plan_pdf_cache_key VARCHAR(100),         -- Cache key for performance (Redis/file cache)
  plan_pdf_cached_at DATETIME(6),          -- When PDF was last generated/cached
  plan_document_number VARCHAR(100),

  -- ========== AUDIT TRAIL ==========
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX idx_validation_plan_data_technik (plan_technik_username),
  INDEX idx_validation_plan_data_qa (plan_qa_username),
  INDEX idx_validation_plan_data_status_date (mapping_status, mapping_check_date),
  INDEX idx_validation_plan_data_cache_key (plan_pdf_cache_key)
)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Plan danych do rewalidacji okresowej (GMP Annex 15 §10)';

ALTER TABLE validation_drafts
  ADD COLUMN plan_data_id BIGINT UNIQUE,
  ADD CONSTRAINT fk_draft_plan_data
    FOREIGN KEY (plan_data_id) REFERENCES validation_plan_data(id);
```

### `V2.28.2__add_role_qa.sql`
```sql
-- Add ROLE_QA if it doesn't exist
INSERT IGNORE INTO roles (name, description) VALUES
  ('ROLE_QA', 'Quality Assurance - Plan reviewer & approver for periodic revalidation');
```

---

## 1.5 New DTOs

### `wizard/dto/ValidationPlanDataDto.java`
```java
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ValidationPlanDataDto {

    private Long id;

    // ========== STEP 3: PLAN DETAILS ==========
    @NotBlank(message = "Uzasadnienie rewalidacji jest wymagane")
    private String revalidationReason;

    private LocalDate previousValidationDate;

    @Size(max = 100, message = "Numer poprzedniej walidacji nie może przekraczać 100 znaków")
    private String previousValidationNumber;

    // ========== STEP 4: MAPPING STATUS ==========
    private MappingStatus mappingStatus;

    @NotNull(message = "Potwierdzenie mapowania OVERDUE jest wymagane")
    private Boolean mappingOverdueAcknowledged;

    // ========== STEPS 5-6: ACCEPTANCE CRITERIA ==========
    @DecimalMin(value = "-50", message = "Min temperatura nie może być niższa niż -50°C")
    @DecimalMax(value = "50", message = "Min temperatura nie może być wyższa niż 50°C")
    private Double planAcceptanceTempMin;

    @DecimalMin(value = "-50", message = "Max temperatura nie może być niższa niż -50°C")
    @DecimalMax(value = "50", message = "Max temperatura nie może być wyższa niż 50°C")
    private Double planAcceptanceTempMax;

    @DecimalMin(value = "-50")
    @DecimalMax(value = "50")
    private Double planMktMaxTemp;

    @DecimalMin(value = "0", message = "Jednorodność nie może być ujemna")
    @DecimalMax(value = "10", message = "Jednorodność nie może przekraczać 10°C")
    private Double planUniformityDeltaMax;

    @DecimalMin(value = "0", message = "Dryft nie może być ujemny")
    private Double planDriftMaxTemp;

    @NotNull(message = "Temperatura nominalna jest wymagana")
    private Double planNominalTemp;

    // ========== STEP 7: DEVIATIONS ==========
    @NotBlank(message = "Procedura CRITICAL jest wymagana")
    private String planDeviationCriticalText;

    @NotBlank(message = "Procedura MAJOR jest wymagana")
    private String planDeviationMajorText;

    @NotBlank(message = "Procedura MINOR jest wymagana")
    private String planDeviationMinorText;

    // ========== STEP 8 & QA: SIGNATURES ==========
    private LocalDateTime planTechnikSignedAt;
    private String planTechnikUsername;
    private String planTechnikFullName;

    // Path A: Electronic
    private LocalDateTime planQaSignedAt;
    private String planQaUsername;
    private String planQaFullName;

    // Path B: Scanned
    @Enumerated(EnumType.STRING)
    private String planQaApprovalMethod;
    private String planQaScannedPdfHash;
    private String planQaSignerName;
    private String planQaSignerTitle;
    private LocalDateTime planQaApprovedByAt;
    private String planQaApprovedByUsername;

    // ========== REJECTION ==========
    private String planQaRejectionReason;
    private LocalDateTime planQaRejectedAt;
    private String planQaRejectedByUsername;

    // ========== DOCUMENT ==========
    private String planDocumentNumber;
    private String planPdfPath;

    // ========== CONVERSION ==========
    public static ValidationPlanDataDto fromEntity(ValidationPlanData entity) {
        if (entity == null) return null;

        return ValidationPlanDataDto.builder()
            .id(entity.getId())
            .revalidationReason(entity.getRevalidationReason())
            .previousValidationDate(entity.getPreviousValidationDate())
            .previousValidationNumber(entity.getPreviousValidationNumber())
            .mappingStatus(entity.getMappingStatus())
            .mappingOverdueAcknowledged(entity.getMappingOverdueAcknowledged())
            .planAcceptanceTempMin(entity.getPlanAcceptanceTempMin())
            .planAcceptanceTempMax(entity.getPlanAcceptanceTempMax())
            .planMktMaxTemp(entity.getPlanMktMaxTemp())
            .planUniformityDeltaMax(entity.getPlanUniformityDeltaMax())
            .planDriftMaxTemp(entity.getPlanDriftMaxTemp())
            .planNominalTemp(entity.getPlanNominalTemp())
            .planDeviationCriticalText(entity.getPlanDeviationCriticalText())
            .planDeviationMajorText(entity.getPlanDeviationMajorText())
            .planDeviationMinorText(entity.getPlanDeviationMinorText())
            .planTechnikSignedAt(entity.getPlanTechnikSignedAt())
            .planTechnikUsername(entity.getPlanTechnikUsername())
            .planTechnikFullName(entity.getPlanTechnikFullName())
            .planQaSignedAt(entity.getPlanQaSignedAt())
            .planQaUsername(entity.getPlanQaUsername())
            .planQaFullName(entity.getPlanQaFullName())
            .planQaApprovalMethod(entity.getPlanQaApprovalMethod())
            .planQaScannedPdfHash(entity.getPlanQaScannedPdfHash())
            .planQaSignerName(entity.getPlanQaSignerName())
            .planQaSignerTitle(entity.getPlanQaSignerTitle())
            .planQaApprovedByAt(entity.getPlanQaApprovedByAt())
            .planQaApprovedByUsername(entity.getPlanQaApprovedByUsername())
            .planQaRejectionReason(entity.getPlanQaRejectionReason())
            .planQaRejectedAt(entity.getPlanQaRejectedAt())
            .planQaRejectedByUsername(entity.getPlanQaRejectedByUsername())
            .planDocumentNumber(entity.getPlanDocumentNumber())
            .planPdfPath(entity.getPlanPdfPath())
            .build();
    }

    public ValidationPlanData toEntity() {
        // Zwracanym nową encję lub merge z istniejącą
        return ValidationPlanData.builder()
            .id(this.id)
            .revalidationReason(this.revalidationReason)
            .previousValidationDate(this.previousValidationDate)
            .previousValidationNumber(this.previousValidationNumber)
            .mappingStatus(this.mappingStatus)
            .mappingOverdueAcknowledged(this.mappingOverdueAcknowledged)
            .planAcceptanceTempMin(this.planAcceptanceTempMin)
            .planAcceptanceTempMax(this.planAcceptanceTempMax)
            .planMktMaxTemp(this.planMktMaxTemp)
            .planUniformityDeltaMax(this.planUniformityDeltaMax)
            .planDriftMaxTemp(this.planDriftMaxTemp)
            .planNominalTemp(this.planNominalTemp)
            .planDeviationCriticalText(this.planDeviationCriticalText)
            .planDeviationMajorText(this.planDeviationMajorText)
            .planDeviationMinorText(this.planDeviationMinorText)
            .planTechnikSignedAt(this.planTechnikSignedAt)
            .planTechnikUsername(this.planTechnikUsername)
            .planTechnikFullName(this.planTechnikFullName)
            .planQaSignedAt(this.planQaSignedAt)
            .planQaUsername(this.planQaUsername)
            .planQaFullName(this.planQaFullName)
            .planQaApprovalMethod(this.planQaApprovalMethod)
            .planQaScannedPdfHash(this.planQaScannedPdfHash)
            .planQaSignerName(this.planQaSignerName)
            .planQaSignerTitle(this.planQaSignerTitle)
            .planQaApprovedByAt(this.planQaApprovedByAt)
            .planQaApprovedByUsername(this.planQaApprovedByUsername)
            .planQaRejectionReason(this.planQaRejectionReason)
            .planQaRejectedAt(this.planQaRejectedAt)
            .planQaRejectedByUsername(this.planQaRejectedByUsername)
            .planDocumentNumber(this.planDocumentNumber)
            .planPdfPath(this.planPdfPath)
            .build();
    }
}
```

### `wizard/dto/MappingStatusDto.java` (już istnieje)
```java
public record MappingStatusDto(
    LocalDate lastMappingDate,
    long daysAgo,
    MappingStatus status,  // ← Teraz ENUM zamiast String
    boolean blocking
) {}
```

### `wizard/dto/QaApprovalDto.java` — Request DTO dla zatwierdzenia
```java
@Data @NoArgsConstructor @AllArgsConstructor
public class QaApprovalDto {

    @NotBlank(message = "Hasło jest wymagane")
    private String password;

    @NotBlank(message = "Intent podpisu jest wymagany")
    private String intent;

    // Path B only
    private String qaSignerName;
    private String qaSignerTitle;
}
```

### `wizard/dto/PlanRejectionDto.java` — Request DTO dla odrzucenia
```java
@Data @NoArgsConstructor @AllArgsConstructor
public class PlanRejectionDto {

    @NotBlank(message = "Powód odrzucenia jest wymagany")
    @Size(min = 10, max = 1000, message = "Powód musi mieć 10-1000 znaków")
    private String rejectionReason;
}
```

### `wizard/dto/ValidationPlanResponseDto.java` — Response DTO
```java
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ValidationPlanResponseDto {
    private Long id;
    private String procedureType;
    private String status;
    private int currentStep;
    private ValidationPlanDataDto planData;
    private String coolingDeviceName;
    private String coolingDeviceInventoryNumber;
    private LocalDateTime lastUpdated;
}
```

---

## 2. Enum Changes

### `wizard/ValidationProcedureType.java` — add value:
```java
PERIODIC_REVALIDATION(
  "Rewalidacja Okresowa",
  "Powtórna kwalifikacja urządzenia zgodnie z harmonogramem GMP Annex 15"
)
```

### `wizard/WizardStatus.java` — add value:
```java
AWAITING_QA_APPROVAL(
  "Oczekuje na zatwierdzenie QA",
  "Plan walidacji oczekuje na podpis QA przed rozpoczęciem pomiarów"
)
```

---

## 3. New Entity: `ValidationPlanData`

**File:** `src/main/java/com/mac/bry/validationsystem/wizard/ValidationPlanData.java`

```java
@Entity
@Table(name = "validation_plan_data")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ValidationPlanData {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== STEP 3: REVALIDATION REASON ==========
    private String revalidationReason;
    private LocalDate previousValidationDate;
    private String previousValidationNumber;

    // ========== STEP 4: MAPPING STATUS ==========
    private LocalDate mappingCheckDate;
    private String mappingStatus;                // CURRENT | OVERDUE | NEVER
    private Boolean mappingOverdueAcknowledged;  // Technik acknowledged OVERDUE

    // ========== STEPS 5-6: ACCEPTANCE CRITERIA ==========
    private Double planAcceptanceTempMin;
    private Double planAcceptanceTempMax;
    private Double planMktMaxTemp;
    private Double planUniformityDeltaMax;
    private Double planDriftMaxTemp;
    private Double planNominalTemp;

    // ========== STEP 7: DEVIATION PROCEDURES ==========
    @Column(columnDefinition = "LONGTEXT") private String planDeviationCriticalText;
    @Column(columnDefinition = "LONGTEXT") private String planDeviationMajorText;
    @Column(columnDefinition = "LONGTEXT") private String planDeviationMinorText;

    // ========== STEP 8: TECHNIK SIGNATURE ==========
    private LocalDateTime planTechnikSignedAt;
    private String planTechnikUsername;
    private String planTechnikFullName;

    // ========== QA APPROVAL (TWO PATHS) ==========
    // Path A: Electronic signature
    private LocalDateTime planQaSignedAt;
    private String planQaUsername;
    private String planQaFullName;

    // Path B: Scanned document
    @Column(length = 20)
    private String planQaApprovalMethod;         // ELECTRONIC_SIGNATURE | SCANNED_DOCUMENT
    @Column(length = 500)
    private String planQaScannedPdfPath;
    @Column(length = 256)
    private String planQaScannedPdfHash;         // SHA256 for integrity (21 CFR Part 11)
    @Column(length = 200)
    private String planQaSignerName;
    @Column(length = 100)
    private String planQaSignerTitle;
    private String planQaApprovedByUsername;     // Internal user approving upload
    private LocalDateTime planQaApprovedByAt;

    // ========== AUDIT TRAIL: REJECTION ==========
    @Column(columnDefinition = "LONGTEXT")
    private String planQaRejectionReason;        // "Komentarz QA do poprawy" visible in step 8
    private LocalDateTime planQaRejectedAt;
    private String planQaRejectedByUsername;

    // ========== GENERATED DOCUMENT & CACHE ==========
    @Column(length = 500) private String planPdfPath;
    @Column(length = 100) private String planPdfCacheKey;       // For Redis/file cache
    private LocalDateTime planPdfCachedAt;       // When PDF was last generated
    @Column(length = 100) private String planDocumentNumber;

    // ========== AUDIT TRAIL ==========
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ========== UTILITY METHODS ==========

    public boolean isMappingOverdue() {
        return "OVERDUE".equals(mappingStatus);
    }

    public boolean isMappingOverdueAcknowledged() {
        return Boolean.TRUE.equals(mappingOverdueAcknowledged);
    }

    public boolean isTechnikSigned() {
        return planTechnikSignedAt != null;
    }

    // Path A: Electronic signature
    public boolean isQaApprovedElectronic() {
        return planQaSignedAt != null && "ELECTRONIC_SIGNATURE".equals(planQaApprovalMethod);
    }

    // Path B: Scanned document
    public boolean isQaApprovedScanned() {
        return planQaApprovedByAt != null && "SCANNED_DOCUMENT".equals(planQaApprovalMethod);
    }

    // Either path is acceptable
    public boolean isQaApproved() {
        return isQaApprovedElectronic() || isQaApprovedScanned();
    }

    public boolean isRejected() {
        return planQaRejectedAt != null;
    }

    public boolean isPdfCached() {
        return planPdfCacheKey != null && planPdfCachedAt != null;
    }
}
```

### Modify `ValidationDraft.java` — add field:
```java
@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
@JoinColumn(name = "plan_data_id")
private ValidationPlanData planData;
```

---

## 4. New Service: `ValidationPlanDataService`

**Files:**
- `wizard/service/ValidationPlanDataService.java` (interface)
- `wizard/service/ValidationPlanDataServiceImpl.java`

**Methods:**
```java
// Step initialization & data saving
ValidationPlanData initOrGet(Long draftId);
void savePlanDetails(Long draftId, String reason, LocalDate prevDate, String prevNumber);
void saveMappingStatus(Long draftId);
void saveMappingOverdueAcknowledgement(Long draftId, boolean acknowledged);
void saveCriteria(Long draftId, Double tempMin, Double tempMax, Double mktMax,
                  Double uniformity, Double drift, Double nominal);
void saveDeviationProcedures(Long draftId, String critical, String major, String minor);

// Technik signature (Step 8) → generates plan PDF, status = AWAITING_QA_APPROVAL, stepLockFrom = 9
void signPlanAsTechnik(Long draftId, String username, String rawPassword, String intent);

// QA Approval - PATH A: Electronic Signature (internal QA user)
// → status = IN_PROGRESS, currentStep = 9, unlocks measurement phase
void signPlanAsQaElectronic(Long draftId, String qaUsername, String rawPassword, String intent);

// QA Approval - PATH B: Scanned Document (QA outside system)
// → Internal user uploads scanned PDF, approves it
// → status = IN_PROGRESS, currentStep = 9, unlocks measurement phase
void approvePlanWithScannedDocument(Long draftId, String approverUsername,
    MultipartFile scannedPdf, String qaSignerName, String qaSignerTitle);

// QA Rejection → returns to step 8, saves reason visible as "Komentarz QA do poprawy"
// → status = AWAITING_QA_APPROVAL, currentStep = 8, enables re-editing
void rejectPlan(Long draftId, String qaUsername, String rejectionReason);

// PDF caching methods
void cachePlanPdf(Long draftId, String pdfPath);
String getCachedPlanPdfPath(Long draftId);
void invalidatePdfCache(Long draftId);
```

**Key constraints:**
- In `signPlanAsQaElectronic()`: Throw `IllegalStateException` if `qaUsername.equals(planTechnikUsername)` — 2-person rule
- In `approvePlanWithScannedDocument()`: Throw `IllegalStateException` if `approverUsername.equals(planTechnikUsername)` — 2-person rule
- Both paths require: `planData.isTechnikSigned() == true`
- Both paths set: `planQaApprovalMethod` to either `ELECTRONIC_SIGNATURE` or `SCANNED_DOCUMENT`
- Scanned path: Calculate `planQaScannedPdfHash` (SHA256) for 21 CFR Part 11 compliance
- Rejection: Clear both approval paths, reset status to `AWAITING_QA_APPROVAL`, allow re-editing steps 1-8

---

## 5. New Service: `MappingStatusService`

**File:** `wizard/MappingStatusService.java`

```java
public MappingStatusDto getMappingStatus(Long deviceId) {
    // Query: SELECT max(v.created_at) FROM validations v
    //        JOIN validation_measurement_series vms ON ...
    //        WHERE v.procedure_type = 'MAPPING' AND device = deviceId
    // Returns: lastMappingDate, daysAgo, status (CURRENT if < 730 days, OVERDUE if >= 730, NEVER if null)
}
```

**DTO:** `wizard/dto/MappingStatusDto.java`
```java
record MappingStatusDto(LocalDate lastMappingDate, long daysAgo, String status, boolean blocking) {}
```

Note: Overdue mapping is a **warning**, not a hard block (can still proceed with acknowledgment).

---

## 6. New Service: `ValidationPlanPdfService`

**File:** `wizard/pdf/ValidationPlanPdfService.java`

Uses iText 7 (same as `ValidationProtocolPdfService`). Generates a signed PDF with:
- **Header**: Device name, inventory number, plan document number, date
- **Section A**: Cel i zakres rewalidacji (reason + previous validation reference)
- **Section B**: Status mapowania (table with last mapping date, days elapsed, status badge, acknowledgement checkbox if OVERDUE)
- **Section C**: Kryteria akceptacji (table: parameter, limit, unit)
  - Temperatura: min/max range
  - MKT max: threshold
  - Jednorodność ΔT max: threshold
  - Dryft max: threshold per recorder
- **Section D**: Plan rozmieszczenia rejestratorów (grid 3×3×3 based on RecorderPosition)
- **Section E**: Procedury odchyleń (3 tables: CRITICAL/MAJOR/MINOR + CAPA text)
- **Section F**: Blok podpisów (Technik + QA, with timestamp, intent, full name)
- **Section G**: QA Rejection Note (if rejected) — "Komentarz QA do poprawy" visible to Technik

**Performance Optimization (PDF Caching):**

```java
public String generateOrRetrievePlanPdf(Long draftId) {
    // 1. Check if PDF cached in DB (plan_pdf_cache_key exists & <= 1 hour old)
    String cacheKey = planData.getPlanPdfCacheKey();
    if (cacheKey != null && planData.getPlanPdfCachedAt().isAfter(now.minusHours(1))) {
        return fileCache.get(cacheKey);  // Return from file cache
    }

    // 2. If not cached: Generate fresh PDF
    byte[] pdfBytes = generatePlanPdf(draftId);

    // 3. Store in file cache (Redis or file-based)
    String newCacheKey = "plan_pdf_" + draftId + "_" + System.currentTimeMillis();
    fileCache.put(newCacheKey, pdfBytes);  // e.g., Redis with 24h TTL

    // 4. Update DB with cache metadata
    planData.setPlanPdfCacheKey(newCacheKey);
    planData.setPlanPdfCachedAt(LocalDateTime.now());

    return pdfBytes;
}

public void invalidateCache(Long draftId) {
    // Called when plan data changes (steps 3-7), forces regeneration on next view
    planData.setPlanPdfCacheKey(null);
    planData.setPlanPdfCachedAt(null);
}
```

**Cache Invalidation Triggers:**
- When saving step 3 (reason) → invalidate cache
- When saving step 4 (mapping) → invalidate cache
- When saving step 5-6 (criteria) → invalidate cache
- When saving step 7 (deviations) → invalidate cache
- When rejection happens → invalidate cache
- When re-editing after rejection → invalidate cache

After technik signs → `PdfSigningService.sign()` with TSA → file saved to `uploads/signed/plan_{draftId}_signed.pdf`.

---

## 7. Modify `ValidationWizardController`

**File:** `wizard/ValidationWizardController.java`

### Extend `showStep()` switch for PERIODIC_REVALIDATION:
```java
case 3 -> {
    if (isPeriodicRevalidation(draft)) {
        model.addAttribute("planData", planDataService.initOrGet(id));
        return "wizard/step3-pr";
    }
    return "wizard/step3"; // existing: custom acceptance criteria
}
case 4 -> {
    if (isPeriodicRevalidation(draft)) {
        model.addAttribute("mappingStatus", mappingStatusService.getMappingStatus(
            draft.getCoolingDevice().getId()));
        model.addAttribute("planData", planDataService.initOrGet(id));
        return "wizard/step4-pr";
    }
    // existing: load state + recorder position
}
case 5 -> {
    if (isPeriodicRevalidation(draft)) {
        model.addAttribute("planData", planDataService.initOrGet(id));
        model.addAttribute("loadStates", DeviceLoadState.values());
        model.addAttribute("positions", RecorderPosition.values());
        return "wizard/step5-pr";  // load state + acceptance criteria combined
    }
    // existing: OQ/PQ/MAPPING branch
}
case 6, 7 -> { if (isPeriodicRevalidation(draft)) { ... return "wizard/step6-pr" / "step7-pr"; } ... }
case 8 -> { if (isPeriodicRevalidation(draft)) { return "wizard/step8-pr"; } ... }
case 9 -> {
    if (isPeriodicRevalidation(draft)) {
        // BARRIER CHECK
        if (draft.getStatus() != WizardStatus.IN_PROGRESS || draft.getPlanData().getPlanQaSignedAt() == null)
            return "redirect:/wizard/" + id + "/awaiting-approval";
        return "wizard/step9-pr"; // vi2 upload
    }
    return "wizard/step9"; // existing finalization
}
case 10, 11, 12, 13 -> { /* PERIODIC_REVALIDATION steps 10-13 */ }
```

### Add new endpoints:
```
GET  /wizard/{id}/awaiting-approval              → shows read-only plan status + rejection reason (if any)
GET  /wizard/{id}/plan-review                    → QA review page (requires ROLE_QA or ROLE_SUPER_ADMIN)
GET  /wizard/{id}/preview-plan-pdf               → stream cached PDF (iFrame support)

POST /wizard/{id}/api/step3-pr                   → saves plan details
POST /wizard/{id}/api/step4-pr                   → saves mapping acknowledgement
POST /wizard/{id}/api/step5-pr                   → saves load state + nominal temp
POST /wizard/{id}/api/step6-pr                   → saves acceptance criteria
POST /wizard/{id}/api/step7-pr                   → saves deviation procedures

POST /wizard/{id}/finalize-plan                  → technik signs plan (step 8) → PDF generated & cached
POST /wizard/{id}/plan-approve-electronic        → QA signs electronically (PATH A)
POST /wizard/{id}/plan-approve-scanned           → Upload scanned PDF (PATH B)
POST /wizard/{id}/plan-reject                    → QA rejects → back to step 8 with reason

POST /wizard/{id}/finalize-revalidation          → final signing (step 13)
```

---

## 8. Modify `WizardFinalizationService`

**New method: `finalizePlanPhase()`**
```java
@Transactional
public void finalizePlanPhase(Long draftId, String username, String rawPassword, String intent) {
    // 1. Load draft, verify ownership, verify IN_PROGRESS
    // 2. BCrypt verify password
    // 3. Generate plan PDF: validationPlanPdfService.generate(draft)
    // 4. Sign PDF with PdfSigningService (same keystore as validation PDFs)
    // 5. Set planData.planTechnikSigned* fields
    // 6. Set planData.planPdfPath
    // 7. Allocate document number via DocumentNumberingService
    // 8. Set draft.status = AWAITING_QA_APPROVAL
    // 9. Set draft.stepLockFrom = 9  ← prevents editing steps 1-8
    // 10. Save
}
```

**New method: `finalizeRevalidation()`**
```java
@Transactional
public Validation finalizeRevalidation(Long draftId, String username, String rawPassword, String intent) {
    // Same pipeline as finalizeWizard() with additions:
    // - Guard: verify planData.planQaSignedAt != null
    // - After creating Validation entity: attach planData reference (new field on Validation or keep in draft)
    // - ValidationPackageService will auto-detect PERIODIC_REVALIDATION and prepend plan PDF
}
```

---

## 8.5 Modify `SecurityConfig.java`

**File:** `security/config/SecurityConfig.java`

Add role-based access control for QA endpoints:
```java
// In SecurityConfig.configureHttpSecurity():
http
  // ... existing security rules ...
  .authorizeHttpRequests(auth -> auth
    // QA Plan Review & Approval (ROLE_QA only)
    .requestMatchers("/wizard/*/plan-review").hasAnyRole("QA", "SUPER_ADMIN")
    .requestMatchers("/wizard/*/plan-approve-*").hasAnyRole("QA", "SUPER_ADMIN")
    .requestMatchers("/wizard/*/plan-reject").hasAnyRole("QA", "SUPER_ADMIN")

    // Technik can view awaiting-approval status
    .requestMatchers("/wizard/*/awaiting-approval").authenticated()
    .requestMatchers("/wizard/*/preview-plan-pdf").authenticated()

    // ... rest of rules ...
  );
```

**Note:** `ROLE_QA` must exist in DB before running migrations. Verify in `V2.28.2__add_role_qa.sql`.

---

## 9. Modify `ValidationPackageService`

**File:** `validation/ValidationPackageService.java`

In `generateValidationPackage()`, add at the beginning:
```java
// Check if this validation came from a PERIODIC_REVALIDATION draft
Optional<ValidationDraft> draft = draftRepository.findByCompletedValidation(validation);
if (draft.isPresent() && draft.get().getProcedureType() == PERIODIC_REVALIDATION) {
    String planPath = draft.get().getPlanData().getPlanPdfPath();
    if (planPath != null) {
        zipOut.putNextEntry(new ZipEntry("00_Plan_Walidacji_" + id + "_" + date + ".pdf"));
        Files.copy(Path.of(planPath), zipOut);
    }
}
```

Also add `findByCompletedValidation(Validation v)` to `ValidationDraftRepository`.

---

## 10. New HTML Templates (style: sidebar layout, `.vcc-*` classes)

| Template | Content |
|----------|---------|
| `wizard/step3-pr.html` | Reason (textarea), previous validation date (datepicker), previous doc number |
| `wizard/step4-pr.html` | Read-only mapping status card (last date, days elapsed, status badge CURRENT/OVERDUE/NEVER), **acknowledgment checkbox if OVERDUE** — must be checked to continue |
| `wizard/step5-pr.html` | DeviceLoadState select, RecorderPosition info (read-only from material type), nominal temp |
| `wizard/step6-pr.html` | Acceptance criteria form: temp min/max, MKT max, uniformity ΔT, drift max — all pre-filled from device's materialType |
| `wizard/step7-pr.html` | Three expandable textareas for CRITICAL/MAJOR/MINOR CAPA text, pre-filled with GMP-standard defaults |
| `wizard/step8-pr.html` | Plan review (iFrame PDF preview via `/wizard/{id}/preview-plan-pdf` with cache), **"Odśwież podgląd" button** (invalidates cache), signing form (password, intent dropdown, "Podpisz plan" button) |
| `wizard/awaiting-approval.html` | Read-only status: plan PDF download, QA username, date submitted, status badge, **rejection reason if exists** (as "Komentarz QA do poprawy" card) |
| `wizard/plan-review.html` | QA view: plan PDF embed (cached), **two approval sections** — (A) electronic signature form OR (B) scanned document upload + signer metadata, reject button (textarea), 2-person rule notice |
| `wizard/step9-pr.html` | .vi2 file upload (same as existing upload template but with plan criteria summary panel) |
| `wizard/step10-pr.html` | Series selection (same pattern as existing step6.html) |
| `wizard/step11-pr.html` | Statistics review (same as existing step7.html for PERIODIC_REVALIDATION) |
| `wizard/step12-pr.html` | Deviations list (read-only, same as step8.html pattern) |
| `wizard/step13-pr.html` | Final signing form (same as step9.html, calls `/wizard/{id}/finalize-revalidation`) |

---

## 11. Files to Create

```
src/main/java/com/mac/bry/validationsystem/wizard/

  ===== ENTITIES & VALUE OBJECTS =====
  ValidationPlanData.java
  MappingStatus.java                       ← NEW ENUM
  QaApprovalMethod.java                    ← NEW ENUM (ELECTRONIC_SIGNATURE | SCANNED_DOCUMENT)

  ===== VALUE OBJECTS (@Embeddable) - FROM ARCHITEKTURA PLAN =====
  vo/PlanDetails.java
  vo/MappingInfo.java
  vo/AcceptanceCriteria.java
  vo/DeviationProcedures.java
  vo/TechnikSignature.java
  vo/QaApprovalPath.java
  vo/RejectionAuditTrail.java
  vo/PdfCache.java

  ===== DTOs =====
  dto/ValidationPlanDataDto.java           ← Main DTO with validation
  dto/MappingStatusDto.java                ← Status query result (record)
  dto/QaApprovalDto.java                   ← Request body for QA approval
  dto/PlanRejectionDto.java                ← Request body for rejection
  dto/ValidationPlanResponseDto.java       ← Response DTO for API/Controller

  ===== SERVICES =====
  service/ValidationPlanDataService.java   ← Interface
  service/ValidationPlanDataServiceImpl.java
  service/MappingStatusService.java
  service/PlanPdfCacheService.java         ← PDF caching (Redis or file-based)

  ===== PDF GENERATION =====
  pdf/ValidationPlanPdfService.java

  ===== STRATEGY PATTERN (FROM ARCHITEKTURA PLAN) =====
  strategy/ValidationWizardStrategy.java   ← Interface
  strategy/OqPqWizardStrategy.java         ← For OQ/PQ/MAPPING
  strategy/PeriodicRevalidationWizardStrategy.java
  strategy/ValidationWizardStrategyFactory.java

src/main/resources/db/migration/
  V2.28.0__add_periodic_revalidation_type.sql
  V2.28.1__validation_plan_data.sql
  V2.28.2__add_role_qa.sql

src/main/resources/templates/wizard/
  step3-pr.html  step4-pr.html  step5-pr.html  step6-pr.html  step7-pr.html
  step8-pr.html  awaiting-approval.html  plan-review.html
  step9-pr.html  step10-pr.html  step11-pr.html  step12-pr.html  step13-pr.html
```

## 12. Files to Modify

```
wizard/ValidationProcedureType.java           — add PERIODIC_REVALIDATION
wizard/WizardStatus.java                      — add AWAITING_QA_APPROVAL
wizard/ValidationDraft.java                   — add planData OneToOne field
wizard/ValidationDraftRepository.java         — add findByCompletedValidation()

wizard/ValidationWizardController.java        — delegate to Strategy instead of switch
  (remove big switch statement, inject ValidationWizardStrategy)

wizard/service/ValidationDraftService.java    — new method signatures
wizard/service/ValidationDraftServiceImpl.java — implementations

wizard/service/WizardFinalizationService.java — finalizePlanPhase(), finalizeRevalidation()
  (now uses DTOs: ValidationPlanDataDto, QaApprovalDto, PlanRejectionDto)

wizard/pdf/ProcedureStrategyFactory.java      — add PERIODIC_REVALIDATION strategy (if exists)

security/config/SecurityConfig.java           — add @PreAuthorize("hasAnyRole('QA', 'SUPER_ADMIN')")
                                               for QA endpoints

validation/ValidationPackageService.java      — prepend plan PDF to ZIP
```

---

## 12.3 DTO Usage Pattern

### Controller → Service Flow

```java
// ValidationWizardController.java

@PostMapping("/{id}/api/step3-pr")
public ResponseEntity<?> saveStep3(
    @PathVariable Long id,
    @Valid @RequestBody ValidationPlanDataDto dto,  // ← DTO input
    Authentication auth) {

    String username = auth.getName();

    // Service zwraca DTO
    ValidationPlanDataDto saved = planDataService.savePlanDetails(id, dto, username);

    return ResponseEntity.ok(ValidationPlanResponseDto.builder()
        .id(id)
        .planData(saved)
        .status("Step 3 saved")
        .build());
}

@PostMapping("/{id}/plan-approve-electronic")
public ResponseEntity<?> approveElectronic(
    @PathVariable Long id,
    @Valid @RequestBody QaApprovalDto approvalDto,  // ← Specific DTO
    Authentication auth) {

    String qaUsername = auth.getName();

    // Service przyjmuje DTO
    ValidationPlanDataDto approved = planDataService.signPlanAsQaElectronic(
        id, qaUsername, approvalDto.getPassword(), approvalDto.getIntent());

    return ResponseEntity.ok(approved);
}

@PostMapping("/{id}/plan-reject")
public ResponseEntity<?> rejectPlan(
    @PathVariable Long id,
    @Valid @RequestBody PlanRejectionDto rejectionDto,  // ← Specific DTO
    Authentication auth) {

    String qaUsername = auth.getName();

    planDataService.rejectPlan(id, qaUsername, rejectionDto.getRejectionReason());

    return ResponseEntity.ok(new StatusDto("Plan rejected, back to step 8"));
}
```

### Service Implementation Pattern

```java
// ValidationPlanDataService.java (Interface)

public interface ValidationPlanDataService {

    // Input: DTO with validation, Output: DTO
    ValidationPlanDataDto savePlanDetails(
        Long draftId,
        ValidationPlanDataDto dto,
        String username);

    ValidationPlanDataDto saveCriteria(
        Long draftId,
        ValidationPlanDataDto dto,
        String username);

    ValidationPlanDataDto signPlanAsQaElectronic(
        Long draftId,
        String qaUsername,
        String password,
        String intent);

    void rejectPlan(
        Long draftId,
        String qaUsername,
        String rejectionReason);
}
```

```java
// ValidationPlanDataServiceImpl.java

@Service @Transactional
public class ValidationPlanDataServiceImpl implements ValidationPlanDataService {

    @Override
    public ValidationPlanDataDto savePlanDetails(
        Long draftId,
        ValidationPlanDataDto dto,
        String username) {

        // 1. Load draft
        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found"));

        // 2. Get or create plan data
        ValidationPlanData planData = draft.getPlanData();
        if (planData == null) {
            planData = new ValidationPlanData();
            draft.setPlanData(planData);
        }

        // 3. Map DTO → Entity (custom mapper or manual)
        planData.setRevalidationReason(dto.getRevalidationReason());
        planData.setPreviousValidationDate(dto.getPreviousValidationDate());
        planData.setPreviousValidationNumber(dto.getPreviousValidationNumber());

        // 4. Save
        planData = planDataRepository.save(planData);
        draft = draftRepository.save(draft);

        // 5. Return DTO
        return ValidationPlanDataDto.fromEntity(planData);
    }
}
```

### Best Practices

1. **Always use @Valid** for request DTOs
```java
@PostMapping("/save")
public ResponseEntity<?> save(@Valid @RequestBody ValidationPlanDataDto dto) { }
```

2. **DTOs for API boundaries only**
```
Controller (REST) ↔ [DTO] ↔ Service ↔ [Entity] ↔ Repository
```

3. **Use specific DTOs for specific endpoints**
```
- Step 3 payload: ValidationPlanDataDto (just reason + dates)
- QA approval: QaApprovalDto (password + intent)
- Rejection: PlanRejectionDto (reason only)
```

4. **Conversion methods in DTO**
```java
ValidationPlanDataDto dto = ValidationPlanDataDto.fromEntity(entity);
ValidationPlanData entity = dto.toEntity();
```

5. **Never expose sensitive data in DTO**
```java
// OK:
private String planQaUsername;

// NOT OK:
private String planQaPassword;  // ← Usunąć!
private byte[] planQaPfxFile;   // ← Usunąć!
```

---

## 12.5 Two QA Approval Paths — Complete Specification

### Path A: Electronic Signature (Internal QA User)

**Flow:**
1. Technik completes steps 1-8, signs at step 8
2. Status: `AWAITING_QA_APPROVAL`, `stepLockFrom = 9`
3. QA user (with ROLE_QA) views `/wizard/{id}/plan-review`
4. QA reviews PDF (iFrame), clicks "Zatwierdź plan"
5. Form: password + intent dropdown
6. Backend: `signPlanAsQaElectronic(draftId, qaUsername, password, intent)`
   - Verify: `qaUsername != planTechnikUsername` (2-person rule)
   - Generate PDF signature with `PdfSigningService` + TSA
   - Set: `planQaSignedAt = now()`, `planQaUsername`, `planQaFullName`
   - Set: `planQaApprovalMethod = 'ELECTRONIC_SIGNATURE'`
   - Update: `draft.status = IN_PROGRESS, currentStep = 9`
   - Clear: `stepLockFrom` → steps 1-8 now locked (read-only)
7. Audit: Envers captures signature, intent, timestamp
8. Technik can now access step 9+ → measurement phase unlocked

**Data Model:**
```
plan_qa_signed_at = [FILLED]
plan_qa_username = [FILLED]
plan_qa_full_name = [FILLED]
plan_qa_approval_method = 'ELECTRONIC_SIGNATURE'
plan_qa_approved_by_username = NULL  (not used in Path A)
plan_qa_scanned_pdf_path = NULL
```

### Path B: Scanned Document (QA Outside System)

**Flow:**
1. Technik completes steps 1-8, signs at step 8
2. Status: `AWAITING_QA_APPROVAL`, `stepLockFrom = 9`
3. Technik/Admin sends PDF to external QA (email, printed, etc.)
4. External QA signs physically or electronically (outside system)
5. Returns signed document (scanned PDF)
6. Technik/Admin/Manager (with internal account) visits `/wizard/{id}/plan-review`
7. Sees "Upload podpisanego dokumentu" section
8. Uploads scanned PDF + fills: "Imię/Nazwisko QA" + "Stanowisko QA"
9. Backend: `approvePlanWithScannedDocument(draftId, approverUsername, scannedPdf, qaSignerName, qaSignerTitle)`
   - Verify: `approverUsername != planTechnikUsername` (2-person rule)
   - Calculate: `planQaScannedPdfHash = SHA256(scannedPdf)` (21 CFR Part 11)
   - Save: scanned PDF to `uploads/qa-approvals/plan_{draftId}_{timestamp}.pdf`
   - Set: `planQaScannedPdfPath`, `planQaScannedPdfHash`
   - Set: `planQaSignerName`, `planQaSignerTitle`
   - Set: `planQaApprovedByUsername = approverUsername`
   - Set: `planQaApprovedByAt = now()`
   - Set: `planQaApprovalMethod = 'SCANNED_DOCUMENT'`
   - Update: `draft.status = IN_PROGRESS, currentStep = 9`
   - Clear: `stepLockFrom` → steps 1-8 now locked (read-only)
10. Audit: Envers captures uploader, timestamp, hash, signer metadata
11. Technik can now access step 9+ → measurement phase unlocked

**Data Model:**
```
plan_qa_signed_at = NULL  (not used in Path B)
plan_qa_approval_method = 'SCANNED_DOCUMENT'
plan_qa_scanned_pdf_path = [FILLED]
plan_qa_scanned_pdf_hash = [FILLED]  (SHA256)
plan_qa_signer_name = [FILLED]
plan_qa_signer_title = [FILLED]
plan_qa_approved_by_username = [FILLED]  (internal user)
plan_qa_approved_by_at = [FILLED]
```

### Rejection (Both Paths)

**Flow:**
1. QA reviews plan, identifies issues
2. Clicks "Odrzuć plan" (available on both paths)
3. Form: textarea with rejection reason (required)
4. Backend: `rejectPlan(draftId, qaUsername, rejectionReason)`
   - Verify: QA has ROLE_QA or ROLE_SUPER_ADMIN
   - Save: `planQaRejectionReason = rejectionReason`
   - Set: `planQaRejectedAt = now()`, `planQaRejectedByUsername = qaUsername`
   - Clear: `planQaSignedAt`, `planQaApprovedByAt`, `planQaApprovalMethod` (both paths reset)
   - Update: `draft.status = AWAITING_QA_APPROVAL, currentStep = 8`
   - Clear: `stepLockFrom = NULL` → allow re-editing steps 1-8
   - Invalidate: PDF cache
5. Audit: Envers captures rejection reason, rejector, timestamp
6. Technik sees: `awaiting-approval` page with rejection reason as "Komentarz QA do poprawy"
7. Technik can edit steps 1-8 again, re-sign at step 8, restart review

**Data Model:**
```
plan_qa_rejection_reason = [FILLED]
plan_qa_rejected_at = [FILLED]
plan_qa_rejected_by_username = [FILLED]
plan_qa_signed_at = NULL
plan_qa_approved_by_at = NULL
plan_qa_approval_method = NULL
stepLockFrom = NULL  (allow re-editing)
currentStep = 8
status = AWAITING_QA_APPROVAL
```

### Validation: Step 9 Unlocking

```java
// In ValidationWizardController.showStep(9)
if (isPeriodicRevalidation(draft)) {
    boolean pathA = draft.getPlanData().planQaSignedAt != null
        && "ELECTRONIC_SIGNATURE".equals(draft.getPlanData().planQaApprovalMethod);

    boolean pathB = draft.getPlanData().planQaApprovedByAt != null
        && "SCANNED_DOCUMENT".equals(draft.getPlanData().planQaApprovalMethod);

    if (draft.getStatus() != IN_PROGRESS || (!pathA && !pathB)) {
        return "redirect:/wizard/" + id + "/awaiting-approval";  // Not approved yet
    }
}
// Both paths unlock step 9
```

---

---

## 13. Verification & Testing

### 13.1 Database Migrations
1. **Run migrations**: `mvn flyway:migrate`
   - V2.28.0 (enum extension) must execute cleanly
   - V2.28.1 (validation_plan_data table) must execute cleanly
   - V2.28.2 (ROLE_QA) must execute cleanly
2. Verify: `SELECT * FROM roles WHERE name = 'ROLE_QA'` returns 1 row

### 13.2 Compilation
`mvn clean compile` — zero errors (new entities, services, controller)

### 13.3 Unit Tests
```java
// ValidationPlanDataServiceTest
- testInitOrGet() — creates plan if absent
- testSaveMappingOverdueAcknowledgement() — persists acknowledgement flag
- testSignPlanAsTechnik() — generates PDF, caches it, sets timestamp
- testSignPlanAsQaElectronic_Success() — 2-person rule passes, sets approvalMethod
- testSignPlanAsQaElectronic_SamePerson() — throws IllegalStateException
- testApprovePlanWithScannedDocument() — calculates SHA256, saves path
- testRejectPlan() — clears approvals, resets step to 8, saves reason

// MappingStatusServiceTest
- testMappingStatusCurrent() — < 730 days → CURRENT
- testMappingStatusOverdue() — >= 730 days → OVERDUE
- testMappingStatusNever() — no previous mapping → NEVER

// ValidationPlanPdfServiceTest
- testGenerateOrRetrievePlanPdf_FirstCall() — generates fresh PDF
- testGenerateOrRetrievePlanPdf_CachedCall() — returns from cache
- testInvalidateCache() — clears cache key
- testPdfContainsRejectionReason() — if rejected, reason appears in PDF
```

### 13.4 Integration Tests
```java
// Full workflow: Path A (Electronic Signature)
- Create PERIODIC_REVALIDATION wizard
- Complete steps 1-8
- Technik signs → PDF cached, status = AWAITING_QA_APPROVAL
- Verify step 9 blocked (redirects to awaiting-approval)
- QA user signs electronically
- Verify status = IN_PROGRESS, currentStep = 9
- Verify planQaApprovalMethod = ELECTRONIC_SIGNATURE
- Technik can now access step 9+

// Full workflow: Path B (Scanned Document)
- Create PERIODIC_REVALIDATION wizard
- Complete steps 1-8
- Technik signs → PDF cached, status = AWAITING_QA_APPROVAL
- Admin uploads scanned PDF (PATH B)
- Verify planQaApprovalMethod = SCANNED_DOCUMENT
- Verify planQaScannedPdfHash is saved (SHA256)
- Verify status = IN_PROGRESS, currentStep = 9

// Full workflow: Rejection
- Technik completes steps 1-8, signs
- QA rejects with reason "Przejrzyj kryteria akceptacji"
- Verify status = AWAITING_QA_APPROVAL, currentStep = 8
- Verify planQaRejectionReason is saved
- Technik views awaiting-approval page → sees rejection reason
- Technik edits steps 1-8 again, re-signs
- QA approves (either path)

// Full workflow: Mapping OVERDUE acknowledgement
- Step 4: mapping_status = OVERDUE
- Checkbox "Potwierdzam kontynuację mimo upływu 2 lat" must be checked
- Verify mapping_overdue_acknowledged = true in DB
- In final validation PDF, note that Technik acknowledged OVERDUE
```

### 13.5 Manual Flow (HTTPS localhost:8443)
1. **Setup**: Create users with roles:
   - `technik_user` — ROLE_USER
   - `qa_user` — ROLE_QA
   - `admin_user` — ROLE_SUPER_ADMIN

2. **Path A Test (Electronic Signature)**:
   - Login as `technik_user`, create PERIODIC_REVALIDATION wizard
   - Complete steps 1-8, sign plan (step 8)
   - Logout, login as `qa_user`
   - Visit `/wizard/{id}/plan-review`
   - Review PDF (should be cached, load fast)
   - Click "Zatwierdź plan elektronicznie"
   - Enter password, select intent, confirm
   - Verify: status changes to IN_PROGRESS, currentStep = 9
   - Login as `technik_user`, verify step 9 is accessible

3. **Path B Test (Scanned Document)**:
   - Logout, login as `technik_user`, create new PERIODIC_REVALIDATION wizard
   - Complete steps 1-8, sign plan (step 8)
   - Logout, login as `admin_user`
   - Visit `/wizard/{id}/plan-review`
   - Upload scanned PDF (test file: `test-qa-signature.pdf`)
   - Enter: "Jan Kowalski", "Head of Quality"
   - Verify: planQaApprovalMethod = SCANNED_DOCUMENT
   - Verify: planQaScannedPdfHash is not null (SHA256)
   - Status = IN_PROGRESS, currentStep = 9

4. **Rejection Test**:
   - Logout, login as `qa_user`
   - Visit `/wizard/{id}/plan-review`
   - Click "Odrzuć plan"
   - Enter rejection reason: "Przejrzyj kryteria temperature"
   - Verify: status = AWAITING_QA_APPROVAL, currentStep = 8
   - Login as `technik_user`, view `/wizard/{id}/awaiting-approval`
   - Verify: rejection reason displays as "Komentarz QA do poprawy"
   - Re-edit steps 1-8, re-sign plan
   - QA approves (any path)

5. **PDF Cache Test**:
   - At step 8, view PDF (should load in iFrame)
   - Modify step 7 (deviation text)
   - Go back to step 8, verify PDF updated
   - Click "Odśwież podgląd" button
   - Verify: cache invalidated, fresh PDF generated

6. **Measurement Phase & Download**:
   - Complete steps 9-13 (upload .vi2, select series, review stats, deviations, sign)
   - Download validation ZIP
   - Verify: `00_Plan_Walidacji_*.pdf` is first entry
   - Verify: plan PDF contains all sections including mapping acknowledgement (if OVERDUE)
