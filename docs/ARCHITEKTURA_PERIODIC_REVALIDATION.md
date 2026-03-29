# Architektura PERIODIC_REVALIDATION — Rekomendacje

## 1. Refaktor: Logika z Kontrolerów → Serwisy ✅

### Problem w Obecnym Planie
Kontroler ma `switch` statement dla różnych procedure types (OQ/PQ/MAPPING). Dla PERIODIC_REVALIDATION będzie jeszcze bardziej zaśmiecony.

### Rozwiązanie: Strategy Pattern + Serwis Fasady

**Nowa struktura:**

```
wizard/
├── service/
│   ├── ValidationPlanWizardService.java          ← NEW: Fasada dla PERIODIC_REVALIDATION
│   ├── ValidationWizardStrategy.java             ← NEW: Interface
│   ├── OqPqWizardStrategy.java                   ← Implementacja dla OQ/PQ/MAPPING
│   ├── PeriodicRevalidationWizardStrategy.java   ← Implementacja dla PERIODIC_REVALIDATION
│   └── ValidationWizardStrategyFactory.java      ← Factory dla tworzenia strategii
└── controller/
    └── ValidationWizardController.java           ← SIMPLIFIED: Deleguje do serwisów
```

**Interfejs strategi:**

```java
public interface ValidationWizardStrategy {
    // Preparowanie modelu dla każdego kroku
    void prepareStepModel(ValidationDraft draft, int step, Model model, String username);

    // Walidacja kroku
    void validateStep(ValidationDraft draft, int step) throws IllegalStateException;

    // Zapis kroku
    void saveStep(ValidationDraft draft, int step, Map<String, Object> data, String username);

    // Finalizacja wizarda
    Validation finalize(Long draftId, String username, String password, String intent);
}
```

**Kontroler — uproszczony:**

```java
@GetMapping("/{id}/step/{step}")
public String showStep(@PathVariable Long id, @PathVariable int step,
                       Authentication auth, Model model) {
    String username = auth.getName();
    ValidationDraft draft = draftService.getDraft(id, username)
        .orElseThrow(...);

    // Deleguj logikę kroku do serwisu
    ValidationWizardStrategy strategy = strategyFactory.getStrategy(draft.getProcedureType());
    strategy.prepareStepModel(draft, step, model, username);  // ← Wszystka logika tu

    return "wizard/step" + step;  // Lub dynamicznie z template name
}
```

**Korzyści:**
- ✅ Kontroler nie zna szczegółów procedur (OQ/PQ/PERIODIC_REVALIDATION)
- ✅ Każda procedura ma własną strategię (łatwa do testowania)
- ✅ Łatwe dodanie nowych procedure types
- ✅ Logika biznesowa w serwisach, nie w kontrolerze

---

## 2. Enum dla Statusu Mapowania ✅

### Obecny Problem
```java
mapping_status VARCHAR(20)  // Stringi: "CURRENT", "OVERDUE", "NEVER"
```

### Rozwiązanie: MappingStatus Enum

**Nowy plik:**

```java
// src/main/java/com/mac/bry/validationsystem/wizard/MappingStatus.java

@Getter
public enum MappingStatus {
    CURRENT(
        "Aktualne",
        "Mapowanie wykonane w ciągu ostatnich 730 dni",
        "badge-success",
        false
    ),
    OVERDUE(
        "Przeterminowane",
        "Mapowanie wykonane ponad 730 dni temu - wymagane zatwierdzenie",
        "badge-warning",
        true
    ),
    NEVER(
        "Nigdy nie wykonano",
        "Mapowanie nie zostało nigdy wykonane - wymagane zatwierdzenie",
        "badge-danger",
        true
    );

    private final String displayName;
    private final String description;
    private final String cssClass;      // Bootstrap badge class
    private final boolean requiresAcknowledgement;

    MappingStatus(String displayName, String description, String cssClass, boolean requiresAcknowledgement) {
        this.displayName = displayName;
        this.description = description;
        this.cssClass = cssClass;
        this.requiresAcknowledgement = requiresAcknowledgement;
    }

    public boolean isBlocking() {
        return requiresAcknowledgement;  // Warning, not block
    }
}
```

**Zmiana w encji:**

```java
// Było:
private String mappingStatus;  // VARCHAR(20)

// Teraz:
@Enumerated(EnumType.STRING)
@Column(length = 20)
private MappingStatus mappingStatus;

// Walidacja na level encji:
public boolean needsMappingAcknowledgement() {
    return mappingStatus != null && mappingStatus.isBlocking();
}
```

**W serwisie:**

```java
public void saveMappingStatus(Long draftId) {
    // ... query ostatnia walidacja mapowania

    if (lastMappingDate == null) {
        planData.setMappingStatus(MappingStatus.NEVER);
    } else if (daysSinceLast >= 730) {
        planData.setMappingStatus(MappingStatus.OVERDUE);
    } else {
        planData.setMappingStatus(MappingStatus.CURRENT);
    }
}
```

**W szablonie:**

```html
<!-- Było -->
<span th:if="${planData.mappingStatus == 'CURRENT'}" class="badge badge-success">...</span>

<!-- Teraz -->
<span th:class="'badge ' + ${planData.mappingStatus.cssClass}">
    [[${planData.mappingStatus.displayName}]]
</span>
```

**Korzyści:**
- ✅ Type-safe (nie "CURRENT" czy "CURREN" — compile error)
- ✅ Logika biznesowa w enum (cssClass, requiresAcknowledgement)
- ✅ Łatwiej w testach (MappingStatus.OVERDUE zamiast "OVERDUE")

---

## 3. Czy Encja Nie Będzie Za Ciężka? ⚠️

### Analiza Obecnego ValidationPlanData

```
21 pól:
- Step 3: 3 pola (reason, date, number)
- Step 4: 3 pola (date, status, acknowledged)
- Steps 5-6: 6 pól (criteria: min, max, mkt, uniformity, drift, nominal)
- Step 7: 3 pola (deviation procedures)
- Step 8: 3 pola (technik signature)
- QA approval: 8 pól (2 ścieżki: elektroniczna + skan)
- Audit rejection: 3 pola (reason, timestamp, user)
- Cache: 3 pola (path, key, timestamp)
+ createdAt, updatedAt
```

### Czy To Za Dużo?

**Nie, ale można lepiej** — Recommendation: **Value Objects + Single Responsibility**

```java
// PRZED (monolityczne):
@Entity
public class ValidationPlanData {
    private Double planAcceptanceTempMin;
    private Double planAcceptanceTempMax;
    private Double planMktMaxTemp;
    private Double planUniformityDeltaMax;
    private Double planDriftMaxTemp;
    private Double planNominalTemp;
    // ... 21 pól ogółem
}

// PO (dekompozycja na Value Objects):
@Entity
public class ValidationPlanData {
    @Id private Long id;

    // Logiczne grupy jako Value Objects (embeddable)
    @Embedded private PlanDetails details;           // Step 3
    @Embedded private MappingInfo mappingInfo;       // Step 4
    @Embedded private AcceptanceCriteria criteria;   // Steps 5-6
    @Embedded private DeviationProcedures deviations; // Step 7
    @Embedded private TechnikSignature technikSig;   // Step 8
    @Embedded private QaApprovalPath qaApproval;     // QA approval
    @Embedded private RejectionAuditTrail rejection; // Rejection
    @Embedded private PdfCache pdfCache;             // Cache

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### Value Objects — Implementacja

**1. PlanDetails (Steps 1-3)**

```java
@Embeddable
@Getter @Setter
public class PlanDetails {
    @Column(columnDefinition = "LONGTEXT")
    private String revalidationReason;

    private LocalDate previousValidationDate;

    @Column(length = 100)
    private String previousValidationNumber;
}
```

**2. MappingInfo (Step 4)**

```java
@Embeddable
@Getter @Setter
public class MappingInfo {
    private LocalDate mappingCheckDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MappingStatus mappingStatus;

    private Boolean mappingOverdueAcknowledged;
}
```

**3. AcceptanceCriteria (Steps 5-6)**

```java
@Embeddable
@Getter @Setter
public class AcceptanceCriteria {
    private Double acceptanceTempMin;
    private Double acceptanceTempMax;
    private Double mktMaxTemp;
    private Double uniformityDeltaMax;
    private Double driftMaxTemp;
    private Double nominalTemp;
}
```

**4. DeviationProcedures (Step 7)**

```java
@Embeddable
@Getter @Setter
public class DeviationProcedures {
    @Column(columnDefinition = "LONGTEXT")
    private String criticalText;

    @Column(columnDefinition = "LONGTEXT")
    private String majorText;

    @Column(columnDefinition = "LONGTEXT")
    private String minorText;
}
```

**5. TechnikSignature (Step 8)**

```java
@Embeddable
@Getter @Setter
public class TechnikSignature {
    private LocalDateTime signedAt;

    @Column(length = 50)
    private String username;

    @Column(length = 200)
    private String fullName;
}
```

**6. QaApprovalPath (QA Approval — TWO PATHS)**

```java
@Embeddable
@Getter @Setter
public class QaApprovalPath {
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private QaApprovalMethod approvalMethod;  // ELECTRONIC_SIGNATURE | SCANNED_DOCUMENT

    // Path A: Electronic signature
    private LocalDateTime electronicSignedAt;
    @Column(length = 50)
    private String electronicUsername;
    @Column(length = 200)
    private String electronicFullName;

    // Path B: Scanned document
    @Column(length = 500)
    private String scannedPdfPath;
    @Column(length = 256)
    private String scannedPdfHash;
    @Column(length = 200)
    private String scannedSignerName;
    @Column(length = 100)
    private String scannedSignerTitle;
    @Column(length = 50)
    private String approvedByUsername;  // Internal user
    private LocalDateTime approvedByAt;

    public boolean isApproved() {
        return (electronicSignedAt != null && "ELECTRONIC_SIGNATURE".equals(approvalMethod.toString()))
            || (approvedByAt != null && "SCANNED_DOCUMENT".equals(approvalMethod.toString()));
    }
}

public enum QaApprovalMethod {
    ELECTRONIC_SIGNATURE,
    SCANNED_DOCUMENT
}
```

**7. RejectionAuditTrail**

```java
@Embeddable
@Getter @Setter
public class RejectionAuditTrail {
    @Column(columnDefinition = "LONGTEXT")
    private String rejectionReason;

    private LocalDateTime rejectedAt;

    @Column(length = 50)
    private String rejectedByUsername;

    public boolean isRejected() {
        return rejectedAt != null;
    }
}
```

**8. PdfCache**

```java
@Embeddable
@Getter @Setter
public class PdfCache {
    @Column(length = 500)
    private String pdfPath;

    @Column(length = 100)
    private String cacheKey;

    private LocalDateTime cachedAt;

    public boolean isCached() {
        return cacheKey != null && cachedAt != null &&
               cachedAt.isAfter(LocalDateTime.now().minusHours(1));
    }
}
```

### ValidationPlanData — Uproszczona

```java
@Entity
@Table(name = "validation_plan_data")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ValidationPlanData {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private PlanDetails details;

    @Embedded
    private MappingInfo mappingInfo;

    @Embedded
    private AcceptanceCriteria criteria;

    @Embedded
    private DeviationProcedures deviations;

    @Embedded
    private TechnikSignature technikSignature;

    @Embedded
    private QaApprovalPath qaApproval;

    @Embedded
    private RejectionAuditTrail rejectionTrail;

    @Embedded
    private PdfCache pdfCache;

    @Column(length = 100)
    private String planDocumentNumber;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ========== DOMAIN LOGIC ==========

    public boolean isTechnikSigned() {
        return technikSignature != null && technikSignature.getSignedAt() != null;
    }

    public boolean isQaApproved() {
        return qaApproval != null && qaApproval.isApproved();
    }

    public boolean isRejected() {
        return rejectionTrail != null && rejectionTrail.isRejected();
    }

    public boolean isMappingOverdueAcknowledged() {
        return mappingInfo != null && mappingInfo.getMappingOverdueAcknowledged() != null
            && mappingInfo.getMappingOverdueAcknowledged();
    }

    public boolean isPdfCached() {
        return pdfCache != null && pdfCache.isCached();
    }
}
```

### Korzyści Tego Podejścia

| Aspect | Bez Value Objects | Z Value Objects |
|--------|-------------------|-----------------|
| **Pola w encji** | 21 | 9 (+ embedded) |
| **Czytelność** | Chaotyczne, brak logicznych grup | Jasne grupy logiczne (Steps) |
| **Testowanie** | Trudne setup (21 pól) | Łatwe — testuj Value Object osobno |
| **Reusability** | Brak — każde pole to string | AcceptanceCriteria można reuse w innym miejscu |
| **Single Responsibility** | Encja robi "wszystko" | Każdy VO odpowiada za jeden koncept |
| **Validacja** | GlobalExceptionHandler | Validacja w samych Value Objects |
| **Migracje** | Dodaj nowe kolumny | Czasem wystarczy zmiana w VO |

### Migration Strategy

**Opcja A: Bezpośrednia migracja (jeśli dopiero zaczynamy)**
```sql
-- Tabela pozostaje taka sama (kolumny nie zmieniają się)
-- Tylko logika Java zmienia się na Value Objects
-- @Embeddable nie tworzy nowych tabel, tylko grupuje kolumny
```

**Opcja B: Jeśli już mamy dane (refactor w przyszłości)**
```sql
-- Obecna V2.28.1 pozostaje bez zmian
-- Dodamy V2.28.3__refactor_plan_data_value_objects.sql (pusta — struktura się nie zmienia)
-- Tylko Java code zmienia się
```

---

## 4. Podsumowanie Architektoniczne

| Decyzja | Rekomendacja | Powód |
|---------|--------------|-------|
| **Logika w Serwisach** | ✅ Strategy Pattern | Kontroler zupełnie czysty, testowanie serwisów niezależne |
| **Enums dla statusów** | ✅ MappingStatus enum | Type-safety, domain logic w enum |
| **Ciężkość encji** | ✅ Value Objects (@Embeddable) | Rozbija dużą encję na mniejsze, czytelne obiekty |
| **Liczba pól** | ✅ 9 logical groups + 21 kolumn BD | Złoty środek — BD bez zmian, Java logiczna |

---

## 5. Implementacja Kolejność

```
1. Utwórz MappingStatus enum
2. Utwórz Value Objects (8 embeddable klas)
3. Utwórz ValidationWizardStrategy + Factory
4. Utwórz PeriodicRevalidationWizardStrategy
5. Upraszcz ValidationWizardController (deleguj do strategii)
6. Zmień ValidationPlanData na Value Objects
7. Aktualizuj serwisy (ValidationPlanDataService, etc.)
```

Chcesz bym zaktualizował plan z tym podejściem?
