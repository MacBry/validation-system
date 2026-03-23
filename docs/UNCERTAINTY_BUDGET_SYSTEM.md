# Budżet Niepewności Pomiaru - System GUM-Compliant

## Przegląd

System budżetu niepewności w validation-system implementuje pełne standardy **GUM (Guide to the Expression of Uncertainty in Measurement)** zgodnie z **JCGM 100:2008** oraz wymogami **ISO/IEC 17025:2017** dla laboratoriów badawczych i kalibracyjnych.

## Architektura Systemu

### 1. Podstawowe Komponenty

```java
@Entity
@Table(name = "uncertainty_budgets")
public class UncertaintyBudget {
    // Type A Uncertainty (statistical)
    private Double statisticalUncertainty;

    // Type B Uncertainties (systematic)
    private Double calibrationUncertainty;
    private Double resolutionUncertainty;
    private Double systematicUncertainty;
    private Double stabilityUncertainty;
    private Double spatialUncertainty; // tylko dla validation-level

    // Combined and Expanded
    private Double combinedUncertainty; // uc = √(Σui²)
    private Double expandedUncertainty; // U = k × uc
}
```

### 2. Poziomy Obliczeń

#### Poziom Serii (SERIES)
- **UncertaintyBudgetService** - obliczenia dla pojedynczej serii pomiarowej
- Zawiera komponenty: Type A + Type B (bez spatial)
- Używa rzeczywistych danych kalibracyjnych z certyfikatów

#### Poziom Walidacji (VALIDATION)
- **ValidationUncertaintyBudgetService** - agregacja z wszystkich serii siatki
- Dodatkowo: **niepewność przestrzenna** (spatial uncertainty)
- Aggregated approach: worst-case dla Type B, RSS dla Type A

## Implementacja Type A (Statistical Uncertainty)

**Obliczenie:** `u_A = σ / √n`

```java
public Double calculateStatisticalUncertainty(List<BigDecimal> values) {
    double mean = calculateMean(values);
    double variance = values.stream()
        .mapToDouble(val -> Math.pow(val.doubleValue() - mean, 2))
        .sum() / values.size();
    double standardDeviation = Math.sqrt(variance);
    return standardDeviation / Math.sqrt(values.size()); // Standard error of mean
}
```

**Charakterystyka:**
- Używa odchylenia standardowego średniej (SEM)
- Maleje z √n (więcej pomiarów → mniejsza niepewność)
- Reprezentuje precyzję pomiaru

## Implementacja Type B (Systematic Uncertainties)

### B1. Niepewność Kalibracyjna
**Źródło:** Świadectwa wzorcowania rejestratorów
```java
private Double getCalibrationUncertainty(ThermoRecorder recorder, double temperature) {
    return recorder.getCalibrations().stream()
        .filter(cal -> cal.isValid())
        .flatMap(cal -> cal.getPoints().stream())
        .filter(point -> Math.abs(point.getTemperatureValue().doubleValue() - temperature) < 1.0)
        .mapToDouble(point -> point.getUncertainty().doubleValue())
        .findFirst()
        .orElse(DEFAULT_CALIBRATION_UNCERTAINTY); // penalty za brak kalibracji
}
```

### B2. Niepewność Rozdzielczości
**Obliczenie:** `u_resolution = resolution / (2√3)`
```java
private Double calculateResolutionUncertainty(BigDecimal resolution) {
    if (resolution == null) return DEFAULT_RESOLUTION_UNCERTAINTY;
    // Rectangular probability distribution
    return resolution.doubleValue() / (2.0 * Math.sqrt(3));
}
```

### B3. Niepewność Systematyczna
**Źródło:** Bias correction z kalibratacji
```java
private Double calculateSystematicUncertainty(ThermoRecorder recorder, double temperature) {
    return recorder.getCalibrations().stream()
        .filter(cal -> cal.isValid())
        .flatMap(cal -> cal.getPoints().stream())
        .filter(point -> Math.abs(point.getTemperatureValue().doubleValue() - temperature) < 1.0)
        .mapToDouble(point -> Math.abs(point.getSystematicError().doubleValue()))
        .findFirst()
        .orElse(0.0);
}
```

### B4. Niepewność Stabilności
**Charakterystyka:** Długoterminowy dryftu instrumentu
```java
private Double calculateStabilityUncertainty(MaterialType materialType, int samplingIntervalMinutes) {
    // Estymacja na podstawie typu materiału i czasu próbkowania
    double baseStability = 0.01; // °C per year dla TESTO 175
    double timeFactors = Math.sqrt(samplingIntervalMinutes / 5.0); // reference: 5 min
    return baseStability * timeFactor;
}
```

### B5. Niepewność Przestrzenna (Validation Level Only)
**Obliczenie:** Niejednorodność rozkładu temperatur w komorze
```java
public Double calculateSpatialUncertainty(List<MeasurementSeries> gridSeries) {
    List<Double> averages = gridSeries.stream()
        .filter(s -> !s.getIsReferenceRecorder())
        .map(MeasurementSeries::getAvgTemperature)
        .collect(Collectors.toList());

    double spatialVariance = /* oblicz wariancję średnich temperatur */;
    return Math.sqrt(spatialVariance) / Math.sqrt(averages.size());
}
```

## RSS Combination (Root Sum of Squares)

**Standard GUM Equation:**
```
u_c = √(u_A² + u_B1² + u_B2² + u_B3² + u_B4² + u_B5²)
```

**Implementacja:**
```java
public UncertaintyBudget calculateCombinedUncertainty(UncertaintyBudget budget) {
    double combined = Math.sqrt(
        Math.pow(budget.getStatisticalUncertainty(), 2) +
        Math.pow(budget.getCalibrationUncertainty(), 2) +
        Math.pow(budget.getResolutionUncertainty(), 2) +
        Math.pow(budget.getSystematicUncertainty(), 2) +
        Math.pow(budget.getStabilityUncertainty(), 2) +
        Math.pow(budget.getSpatialUncertainty() != null ? budget.getSpatialUncertainty() : 0.0, 2)
    );

    budget.setCombinedUncertainty(combined);
    return budget;
}
```

## Expanded Uncertainty i Coverage Factor

**Obliczenie:** `U = k × u_c`

**Coverage Factor k:**
- k = 2.0 dla dużych próbek (n ≥ 30) → poziom ufności ~95.45%
- k > 2.0 dla małych próbek (t-Student distribution)

```java
private double calculateCoverageFactor(int sampleSize) {
    if (sampleSize >= 30) {
        return 2.0; // Normal distribution approximation
    }
    // t-Student distribution dla małych próbek
    return TDistribution.getCriticalValue(sampleSize - 1, 0.05);
}
```

## Integracja z UI (Thymeleaf Templates)

### Validation Details (Level Validation)
```html
<!-- Sekcja H: Budżet Niepewności (GUM-Compliant) -->
<th:block th:insert="~{validation/fragments/uncertainty-budget-section :: section(${summaryStats}, ${validation})}"></th:block>
```

### Measurement Series Details
```html
<!-- Budżet Niepewności (GUM-Compliant) -->
<div th:if="${seriesEntity != null and seriesEntity.uncertaintyBudget != null}" class="limits-card">
    <th:block th:with="budget=${seriesEntity.uncertaintyBudget}">
        <!-- Wyświetlanie komponentów Type A i Type B -->
    </th:block>
</div>
```

## Konfiguracja i Deployment

### Database Migration
```sql
-- V2.23.0__uncertainty_budget_schema.sql
CREATE TABLE uncertainty_budgets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    statistical_uncertainty DOUBLE,
    calibration_uncertainty DOUBLE,
    resolution_uncertainty DOUBLE,
    systematic_uncertainty DOUBLE,
    stability_uncertainty DOUBLE,
    spatial_uncertainty DOUBLE,
    combined_uncertainty DOUBLE NOT NULL,
    expanded_uncertainty DOUBLE NOT NULL,
    coverage_factor DOUBLE NOT NULL DEFAULT 2.0,
    confidence_level DOUBLE NOT NULL DEFAULT 95.45,
    degrees_of_freedom INTEGER,
    budget_type ENUM('SERIES', 'VALIDATION') NOT NULL,
    dominant_uncertainty_source VARCHAR(50),
    calculation_notes TEXT,
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Spring Configuration
```java
@Service
@RequiredArgsConstructor
public class UncertaintyBudgetService {
    // Automatyczna iniekcja przez @RequiredArgsConstructor
}
```

## Testing Strategy

### Unit Tests
```java
@ExtendWith(MockitoExtension.class)
class UncertaintyBudgetServiceTest {
    @Test
    @DisplayName("Should calculate complete uncertainty budget with all components")
    void shouldCalculateCompleteUncertaintyBudget() {
        // Test wszystkich komponentów Type A i Type B
    }
}
```

### Integration Tests
```java
@SpringBootTest
@Transactional
class UncertaintyBudgetIntegrationTest {
    @Test
    @DisplayName("Should calculate and persist complete uncertainty budget")
    void shouldCalculateAndPersistUncertaintyBudget() {
        // Test pełnego przepływu z bazą danych
    }
}
```

## Compliance i Standardy

### GUM (JCGM 100:2008)
- ✅ Type A uncertainty (statistical analysis)
- ✅ Type B uncertainty (other methods)
- ✅ RSS combination dla independent sources
- ✅ Coverage factor calculation
- ✅ Degrees of freedom estimation

### ISO/IEC 17025:2017
- ✅ Calibration uncertainty from certificates
- ✅ Traceability to national standards
- ✅ Measurement uncertainty budget documentation
- ✅ Validation of measurement methods

### FDA 21 CFR Part 11
- ✅ Electronic signatures support
- ✅ Audit trail for uncertainty calculations
- ✅ Data integrity and security

## Troubleshooting

### Brak Kalibracji Rejestratora
```java
// System automatycznie zastosuje penalty
private static final double DEFAULT_CALIBRATION_UNCERTAINTY = 1.0; // °C
```

### Pusty Budżet Niepewności
```java
// Sprawdź w logach
log.warn("Nie udało się obliczyć budżetu niepewności dla serii {}: {}", seriesId, ex.getMessage());
```

### Problemy z Obliczeniami Spatial
```java
// Wymaga minimum 2 serii siatki
if (gridSeries.size() < 2) {
    log.warn("Za mało serii siatki dla obliczenia spatial uncertainty");
    return 0.0;
}
```

## Zaawansowane Funkcje

### Analiza Dominujących Źródeł
- Automatyczna identyfikacja największego komponentu niepewności
- Rekomendacje poprawy dokładności systemu pomiarowego

### Quality Metrics
- Uncertainty Quality Index (UQI)
- Porównanie z wymaganiami GDP/GMP (±0.5°C, ±1.0°C)

### Export i Raportowanie
- PDF reports z pełnym budżetem niepewności
- Zgodność z wymaganiami inspekcyjnymi (GIF, WIF, FDA, EMA)
