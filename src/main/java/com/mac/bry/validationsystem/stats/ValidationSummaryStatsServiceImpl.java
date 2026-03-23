package com.mac.bry.validationsystem.stats;

import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.UncertaintyBudget;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementacja serwisu obliczającego i zapisującego zbiorcze statystyki
 * walidacji.
 *
 * <h3>Architektura wydajnościowa</h3>
 * <p>
 * WSZYSTKIE obliczenia bazują wyłącznie na zagregowanych polach encji
 * {@link MeasurementSeries} (min, max, avg, variance, measurementCount itd.).
 * Surowe punkty pomiarowe {@code MeasurementPoint} NIE są ładowane —
 * zapewnia to O(k) złożoność zamiast O(N), gdzie k = liczba serii, N = liczba
 * punktów.
 * </p>
 *
 * <h3>Metodologia</h3>
 * <p>
 * Szczegółowy opis każdej statystyki globalnej:
 * {@code docs/VALIDATION_SUMMARY_STATS_METODOLOGIA.md}
 * </p>
 *
 * @see ValidationSummaryStatsService
 * @see MeasurementSeries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationSummaryStatsServiceImpl implements ValidationSummaryStatsService {

        private final ValidationSummaryStatsRepository statsRepository;
        private final ValidationRepository validationRepository;
        private final ValidationUncertaintyBudgetService validationUncertaintyBudgetService;

        // =========================================================================
        // PUBLICZNE METODY SERWISU
        // =========================================================================

        @Override
        @Transactional
        public ValidationSummaryStatsDto calculateAndSave(Long validationId) {
                log.info("Obliczanie statystyk zbiorczych dla walidacji ID: {}", validationId);

                Validation validation = validationRepository.findById(validationId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Nie znaleziono walidacji o ID: " + validationId));

                List<MeasurementSeries> allSeries = validation.getMeasurementSeries();

                if (allSeries == null || allSeries.isEmpty()) {
                        log.warn("Walidacja ID: {} nie ma przypisanych serii pomiarowych. " +
                                        "Statystyki zbiorcze nie zostaną obliczone.", validationId);
                        throw new IllegalStateException(
                                        "Walidacja ID " + validationId + " nie posiada serii pomiarowych.");
                }

                // Pobierz istniejący rekord lub stwórz nowy
                ValidationSummaryStats stats = statsRepository.findByValidationId(validationId)
                                .orElseGet(() -> {
                                        ValidationSummaryStats newStats = new ValidationSummaryStats();
                                        newStats.setValidation(validation);
                                        return newStats;
                                });

                // --- Oblicz wszystkie statystyki (zabezpieczone try-catch dla odporności na
                // błędy) ---
                try {
                        calculateTableATemperatureStats(stats, allSeries);
                } catch (Exception e) {
                        log.error("Błąd podczas obliczania Tabeli A (temperatury) dla walidacji {}: {}", validationId,
                                        e.getMessage());
                }

                try {
                        calculateTableBMktStats(stats, allSeries);
                } catch (Exception e) {
                        log.error("Błąd podczas obliczania Tabeli B (MKT) dla walidacji {}: {}", validationId,
                                        e.getMessage());
                }

                try {
                        calculateTableCComplianceStats(stats, allSeries);
                } catch (Exception e) {
                        log.error("Błąd podczas obliczania Tabeli C (zgodność) dla walidacji {}: {}", validationId,
                                        e.getMessage());
                }

                try {
                        calculateTableDDriftStats(stats, allSeries);
                } catch (Exception e) {
                        log.error("Błąd podczas obliczania Tabeli D (dryft) dla walidacji {}: {}", validationId,
                                        e.getMessage());
                }

                try {
                        calculateMetadata(stats, allSeries);
                } catch (Exception e) {
                        log.error("Błąd podczas obliczania metadanych dla walidacji {}: {}", validationId,
                                        e.getMessage());
                }

                // GUM COMPLIANCE: Oblicz zagregowany budżet niepewności dla walidacji
                try {
                        calculateValidationUncertaintyBudget(stats, allSeries);
                } catch (Exception e) {
                        log.error("Błąd podczas obliczania budżetu niepewności dla walidacji {}: {}", validationId,
                                        e.getMessage());
                }

                stats.setCalculatedAt(LocalDateTime.now());

                ValidationSummaryStats saved = statsRepository.save(stats);
                log.info("Zapisano statystyki zbiorcze ID: {} dla walidacji ID: {}",
                                saved.getId(), validationId);

                return ValidationSummaryStatsDto.fromEntity(saved);
        }

        @Override
        @Transactional(readOnly = true)
        public Optional<ValidationSummaryStatsDto> findByValidationId(Long validationId) {
                return statsRepository.findByValidationId(validationId)
                                .map(ValidationSummaryStatsDto::fromEntity);
        }

        @Override
        @Transactional
        public void deleteByValidationId(Long validationId) {
                statsRepository.findByValidationId(validationId)
                                .ifPresent(stats -> {
                                        statsRepository.delete(stats);
                                        log.info("Usunięto statystyki zbiorcze dla walidacji ID: {}", validationId);
                                });
        }

        // =========================================================================
        // TABELA A — Statystyki temperatury (prywatne metody obliczeniowe)
        // =========================================================================

        /**
         * Główna metoda agregacji statystyk temperatury (Tabela A).
         *
         * <p>
         * Deleguje do metod prywatnych dla przejrzystości kodu i możliwości testowania
         * każdej statystyki osobno.
         * </p>
         *
         * @param stats     encja do wypełnienia
         * @param allSeries wszystkie serie przypisane do walidacji
         */
        private void calculateTableATemperatureStats(ValidationSummaryStats stats,
                        List<MeasurementSeries> allSeries) {
                // Podział na serie siatki i referencyjne
                List<MeasurementSeries> gridSeries = allSeries.stream()
                                .filter(s -> Boolean.FALSE.equals(s.getIsReferenceRecorder()))
                                .collect(Collectors.toList());

                List<MeasurementSeries> validGridSeries = gridSeries.stream()
                                .filter(s -> s.getMinTemperature() != null && s.getMaxTemperature() != null
                                                && s.getAvgTemperature() != null && s.getMeasurementCount() != null
                                                && s.getMeasurementCount() > 0)
                                .collect(Collectors.toList());

                // A.1 — Minimalna temperatura globalna (coldspot)
                stats.setGlobalMinTemp(calculateGlobalMinTemp(validGridSeries));

                // A.2 — Maksymalna temperatura globalna (hotspot temperatura)
                stats.setGlobalMaxTemp(calculateGlobalMaxTemp(validGridSeries));

                // A.3 — Średnia ważona (TYLKO serie siatki - nie referencyjne)
                double overallAvg = calculateOverallWeightedAvg(validGridSeries);
                stats.setOverallAvgTemp(validGridSeries.isEmpty() ? null : overallAvg);

                // A.4 — Globalne odchylenie standardowe (TYLKO serie siatki - nie referencyjne)
                Double pooledStdDev = calculatePooledStdDev(validGridSeries, overallAvg);
                stats.setGlobalStdDev(pooledStdDev);

                // A.5 — Globalny współczynnik zmienności CV%
                stats.setGlobalCvPercentage(calculateCvPercentage(pooledStdDev, overallAvg));

                // A.6 — Hotspot (seria z najwyższą maxTemp w siatce)
                setHotspot(stats, validGridSeries);

                // A.7 — Coldspot (seria z najniższą minTemp w siatce)
                setColdspot(stats, validGridSeries);

                // A.8 — Niepewność rozszerzona globalna (max z serii siatki - nie
                // referencyjnych)
                stats.setGlobalExpandedUncertainty(calculateMaxExpandedUncertainty(validGridSeries));

                // A.9 — Percentyle P5 i P95 (konserwatywne przybliżenie)
                stats.setGlobalPercentile5(calculateGlobalPercentile5(validGridSeries));
                stats.setGlobalPercentile95(calculateGlobalPercentile95(validGridSeries));

                log.debug("Tabela A obliczona: min={}, max={}, avg={}, stdDev={}, cv={}%, hotspot={}, coldspot={}",
                                stats.getGlobalMinTemp(), stats.getGlobalMaxTemp(), stats.getOverallAvgTemp(),
                                stats.getGlobalStdDev(), stats.getGlobalCvPercentage(),
                                stats.getHotspotTemp(), stats.getColdspotTemp());
        }

        // =========================================================================
        // A.1 — Minimalna temperatura globalna
        // =========================================================================

        /**
         * A.1 Minimalna temperatura globalna — coldspot.
         *
         * <p>
         * <b>Wzór:</b> {@code min { s.minTemperature | s ∈ gridSeries }}
         * </p>
         *
         * <p>
         * Tylko serie siatki — rejestratory referencyjne mierzą warunki otoczenia.
         * </p>
         */
        private Double calculateGlobalMinTemp(List<MeasurementSeries> gridSeries) {
                return gridSeries.stream()
                                .map(MeasurementSeries::getMinTemperature)
                                .filter(t -> t != null)
                                .min(Double::compareTo)
                                .orElse(null);
        }

        // =========================================================================
        // A.2 — Maksymalna temperatura globalna
        // =========================================================================

        /**
         * A.2 Maksymalna temperatura globalna — hotspot temperatura.
         *
         * <p>
         * <b>Wzór:</b> {@code max { s.maxTemperature | s ∈ gridSeries }}
         * </p>
         */
        private Double calculateGlobalMaxTemp(List<MeasurementSeries> gridSeries) {
                return gridSeries.stream()
                                .map(MeasurementSeries::getMaxTemperature)
                                .filter(t -> t != null)
                                .max(Double::compareTo)
                                .orElse(null);
        }

        // =========================================================================
        // A.3 — Średnia ważona
        // =========================================================================

        /**
         * A.3 Średnia temperatura ważona liczbą pomiarów.
         *
         * <p>
         * <b>Wzór:</b><br>
         * {@code overallAvg = Σ(s.avgTemperature × s.measurementCount) / Σ(s.measurementCount)}
         * </p>
         *
         * <p>
         * <b>Dlaczego ważona?</b> Prosta średnia z avgTemperature poszczególnych serii
         * byłaby
         * błędna gdy serie mają różną długość. Seria z 1000 pomiarami musi ważyć 10×
         * więcej
         * niż seria ze 100 pomiarami.
         * </p>
         *
         * @return ważona średnia lub 0.0 jeśli brak serii
         */
        private double calculateOverallWeightedAvg(List<MeasurementSeries> validSeries) {
                if (validSeries.isEmpty())
                        return 0.0;

                double weightedSum = validSeries.stream()
                                .mapToDouble(s -> s.getAvgTemperature() * s.getMeasurementCount())
                                .sum();

                long totalCount = validSeries.stream()
                                .mapToLong(MeasurementSeries::getMeasurementCount)
                                .sum();

                return totalCount > 0 ? weightedSum / totalCount : 0.0;
        }

        // =========================================================================
        // A.4 — Globalne odchylenie standardowe (pooled variance)
        // =========================================================================

        /**
         * A.4 Globalne odchylenie standardowe — "pooled variance" z tożsamością
         * Steinera.
         *
         * <p>
         * <b>Problem:</b> Nie możemy zsumować odchyleń standardowych — nie są
         * addytywne.<br>
         * Błędne byłoby: {@code mean(s.stdDeviation)} — ignoruje wariancję
         * między-serii.
         * </p>
         *
         * <p>
         * <b>Poprawny wzór (tożsamość Steinera dla połączonych grup):</b><br>
         * {@code σ²_global = [Σ(n_s × σ²_s) + Σ(n_s × (μ_s − μ_global)²)] / N_total}
         * </p>
         *
         * <p>
         * Gdzie:<br>
         * {@code n_s} = liczba pomiarów w serii s<br>
         * {@code σ²_s} = wariancja serii s ({@code s.variance})<br>
         * {@code μ_s} = średnia serii s ({@code s.avgTemperature})<br>
         * {@code μ_global} = średnia globalna (z A.3)<br>
         * {@code N_total} = łączna liczba pomiarów
         * </p>
         *
         * <p>
         * <b>Semantyka:</b> Pierwszy składnik sumy ({@code n_s × σ²_s}) to wariancja
         * wewnątrz-serii. Drugi składnik ({@code n_s × (μ_s − μ_global)²}) to wariancja
         * między-serii (efekt niejednorodności przestrzennej komory).
         * </p>
         *
         * @param validSeries serie z niepustymi danymi
         * @param globalMean  średnia globalna (z {@link #calculateOverallWeightedAvg})
         * @return globalne odchylenie standardowe lub null jeśli brak danych
         */
        private Double calculatePooledStdDev(List<MeasurementSeries> validSeries, double globalMean) {
                if (validSeries.isEmpty())
                        return null;

                // Potrzebujemy variance per seria — pomijamy serie bez variance
                List<MeasurementSeries> seriesWithVariance = validSeries.stream()
                                .filter(s -> s.getVariance() != null)
                                .collect(Collectors.toList());

                if (seriesWithVariance.isEmpty())
                        return null;

                long totalCount = seriesWithVariance.stream()
                                .mapToLong(MeasurementSeries::getMeasurementCount)
                                .sum();

                if (totalCount == 0)
                        return null;

                // Σ n_s × σ²_s (wariancja wewnątrz-serii, ważona)
                double withinGroupVarianceSum = seriesWithVariance.stream()
                                .mapToDouble(s -> s.getMeasurementCount() * s.getVariance())
                                .sum();

                // Σ n_s × (μ_s − μ_global)² (wariancja między-serii)
                double betweenGroupVarianceSum = seriesWithVariance.stream()
                                .mapToDouble(s -> {
                                        double diff = s.getAvgTemperature() - globalMean;
                                        return s.getMeasurementCount() * diff * diff;
                                })
                                .sum();

                double pooledVariance = (withinGroupVarianceSum + betweenGroupVarianceSum) / totalCount;

                return Math.sqrt(pooledVariance);
        }

        // =========================================================================
        // A.5 — Globalny współczynnik zmienności
        // =========================================================================

        /**
         * A.5 Globalny współczynnik zmienności CV%.
         *
         * <p>
         * <b>Wzór:</b> {@code CV = (σ_global / |μ_global|) × 100}
         * </p>
         *
         * <p>
         * <b>Interpretacja:</b><br>
         * CV &lt; 2% → doskonała jednorodność<br>
         * CV 2–5% → dobra jednorodność<br>
         * CV 5–15% → akceptowalna<br>
         * CV &gt; 15% → wymaga uzasadnienia w protokole
         * </p>
         */
        private Double calculateCvPercentage(Double stdDev, double globalMean) {
                if (stdDev == null || Math.abs(globalMean) < 1e-9)
                        return null;
                return (stdDev / Math.abs(globalMean)) * 100.0;
        }

        // =========================================================================
        // A.6 — Hotspot
        // =========================================================================

        /**
         * A.6 Ustawia temperaturę i ID serii hotspot.
         *
         * <p>
         * <b>Hotspot:</b> Seria siatki z najwyższą wartością {@code avgTemperature}.
         * Wskazuje miejsce w komorze o najwyższej średniej temperaturze.
         * </p>
         *
         * <p>
         * <b>Wymaganie GDP:</b> Lokalizacja hotspot musi być udokumentowana w
         * protokole.
         * ID serii pozwala zidentyfikować pozycję rejestratora (np. FRONT_TOP_LEFT).
         * </p>
         */
        private void setHotspot(ValidationSummaryStats stats,
                        List<MeasurementSeries> gridSeries) {
                gridSeries.stream()
                                .filter(s -> s.getMaxTemperature() != null)
                                .max(Comparator.comparingDouble(MeasurementSeries::getMaxTemperature))
                                .ifPresent(hotspotSeries -> {
                                        stats.setHotspotTemp(hotspotSeries.getMaxTemperature());
                                        stats.setHotspotSeriesId(hotspotSeries.getId());
                                });
        }

        // =========================================================================
        // A.7 — Coldspot
        // =========================================================================

        /**
         * A.7 Ustawia temperaturę i ID serii coldspot.
         *
         * <p>
         * <b>Coldspot:</b> Seria siatki z najniższą wartością {@code avgTemperature}.
         * Wskazuje miejsce w komorze o najniższej średniej temperaturze.
         * </p>
         *
         * <p>
         * <b>Wymaganie GDP:</b> Dla produktów wrażliwych na zamrożenie —
         * coldspot jest kluczowym parametrem akceptacji walidacji.
         * </p>
         */
        private void setColdspot(ValidationSummaryStats stats,
                        List<MeasurementSeries> gridSeries) {
                gridSeries.stream()
                                .filter(s -> s.getMinTemperature() != null)
                                .min(Comparator.comparingDouble(MeasurementSeries::getMinTemperature))
                                .ifPresent(coldspotSeries -> {
                                        stats.setColdspotTemp(coldspotSeries.getMinTemperature());
                                        stats.setColdspotSeriesId(coldspotSeries.getId());
                                });
        }

        // =========================================================================
        // A.8 — Niepewność rozszerzona globalna
        // =========================================================================

        /**
         * A.8 Globalna niepewność rozszerzona — wartość maksymalna (podejście
         * konserwatywne).
         *
         * <p>
         * <b>Wzór:</b>
         * {@code U_global = max { s.expandedUncertainty | s ∈ gridSeries }}
         * </p>
         *
         * <p>
         * <b>Uzasadnienie:</b> Zgodnie z zasadą ostrożności (prudent approach z GUM —
         * Guide
         * to the Expression of Uncertainty in Measurement), raportujemy maksymalną
         * niepewność
         * spośród serii siatki (urządzenia). Rejestratory referencyjne (otoczenie)
         * są wyłączone, ponieważ walidacja dotyczy urządzenia, nie warunków
         * zewnętrznych.<br>
         * Obowiązuje: {@code U = k × σ}, gdzie k=2 (95% przedział ufności dla rozkładu
         * normalnego przy n→∞).
         * </p>
         */
        private Double calculateMaxExpandedUncertainty(List<MeasurementSeries> gridSeries) {
                return gridSeries.stream()
                                .map(MeasurementSeries::getExpandedUncertainty)
                                .filter(u -> u != null)
                                .max(Double::compareTo)
                                .orElse(null);
        }

        // =========================================================================
        // A.9 — Percentyle P5 i P95
        // =========================================================================

        /**
         * A.9 Globalny percentyl P5 — przybliżenie dolne (TYLKO serie siatki).
         *
         * <p>
         * <b>Uwaga metodyczna:</b> Dokładny globalny P5 wymagałby scalenia wszystkich
         * surowych punktów pomiarowych i posortowania — złożoność O(N log N) przy N =
         * 100 000+.
         *
         * <p>
         * Stosujemy <b>konserverwatywne przybliżenie</b>:<br>
         * {@code globalP5 = min { s.percentile5 | s ∈ gridSeries }}
         * </p>
         *
         * <p>
         * Przybliżenie jest "bezpieczne w dół" — podaje wartość nieco niższą lub równą
         * dokładnemu globalnemu P5, co jest akceptowane w protokołach GMP jako
         * podejście konserwatywne. WYKLUCZAMY rejestratory referencyjne (otoczenie).
         * </p>
         */
        private Double calculateGlobalPercentile5(List<MeasurementSeries> gridSeries) {
                return gridSeries.stream()
                                .map(MeasurementSeries::getPercentile5)
                                .filter(p -> p != null)
                                .min(Double::compareTo)
                                .orElse(null);
        }

        /**
         * A.9 Globalny percentyl P95 — przybliżenie górne (TYLKO serie siatki).
         *
         * <p>
         * <b>Wzór:</b> {@code globalP95 = max { s.percentile95 | s ∈ gridSeries }}
         * </p>
         *
         * <p>
         * Analogicznie do P5 — przybliżenie "bezpieczne w górę".
         * WYKLUCZAMY rejestratory referencyjne (otoczenie).
         * </p>
         */
        private Double calculateGlobalPercentile95(List<MeasurementSeries> gridSeries) {
                return gridSeries.stream()
                                .map(MeasurementSeries::getPercentile95)
                                .filter(p -> p != null)
                                .max(Double::compareTo)
                                .orElse(null);
        }

        // =========================================================================
        // TABELA B — MKT (Mean Kinetic Temperature)
        // =========================================================================

        /**
         * Główna metoda agregacji statystyk MKT (Tabela B).
         *
         * <p>
         * <b>Kluczowa własność:</b> MKT <em>nie jest addytywny</em> — nie można
         * obliczać
         * {@code mean(s.mktTemperature)}. Zamiast tego można odtworzyć globalną sumę
         * Arrheniusa z per-series MKT korzystając z tożsamości:
         *
         * <pre>
         * MKT_k = ΔH/R / (-ln(Σ e^-(ΔH/R/Ti) / n_k))
         * ⇒  Σ e^(-ΔH/R/Ti) = n_k × e^(-ΔH/R / MKT_k_K)
         * ⇒  globalMKT = ΔH/R / (-ln(Σ_k(n_k × e^(-ΔH/R/MKT_k_K)) / N)) - 273.15
         * </pre>
         *
         * <p>
         * Dzięki tej tożsamości mamy dokładny (a nie przybliżony) globalny MKT
         * bez dostępu do surowych punktów pomiarowych.
         *
         * @param stats     encja do wypełnienia
         * @param allSeries wszystkie serie przypisane do walidacji
         */
        private void calculateTableBMktStats(ValidationSummaryStats stats,
                        List<MeasurementSeries> allSeries) {

                // Stałe fizykochemiczne (zgodnie z WHO TRS 953)
                final double R = 8.314472; // J/(mol·K) — stała gazowa
                final double deltaH_J = 83_144.72; // J/mol — domyślna energia aktywacji
                // = 83.14 kJ/mol (farmaceutyczna wartość referencyjna)
                final double deltaH_R = deltaH_J / R; // K — iloraz używany we wzorach
                stats.setMktDeltaHR(deltaH_R);

                // Podział na siatkę i referencyjne
                List<MeasurementSeries> gridSeries = allSeries.stream()
                                .filter(s -> Boolean.FALSE.equals(s.getIsReferenceRecorder())
                                                && s.getMktTemperature() != null
                                                && s.getMeasurementCount() != null
                                                && s.getMeasurementCount() > 0)
                                .collect(Collectors.toList());

                List<MeasurementSeries> refSeries = allSeries.stream()
                                .filter(s -> Boolean.TRUE.equals(s.getIsReferenceRecorder())
                                                && s.getMktTemperature() != null)
                                .collect(Collectors.toList());

                // B.1 Globalny MKT z tożsamości Arrheniusa
                if (!gridSeries.isEmpty()) {
                        long N = gridSeries.stream().mapToLong(MeasurementSeries::getMeasurementCount).sum();
                        if (N > 0) {
                                // Σ_k (n_k × e^(-ΔH/R / MKT_k_Kelvin))
                                double arrheniusSum = gridSeries.stream()
                                                .mapToDouble(s -> {
                                                        double mktKelvin = s.getMktTemperature() + 273.15;
                                                        return s.getMeasurementCount()
                                                                        * Math.exp(-deltaH_R / mktKelvin);
                                                })
                                                .sum();

                                if (arrheniusSum > 0) {
                                        double globalMktKelvin = deltaH_R / (-Math.log(arrheniusSum / N));
                                        stats.setGlobalMkt(globalMktKelvin - 273.15);
                                        log.debug("Tabela B: globalMkt={}°C (arrheniusSum={}, N={})",
                                                        String.format("%.3f", globalMktKelvin - 273.15), arrheniusSum,
                                                        N);
                                }
                        }
                }

                // B.3 Najgorszy MKT (max) — worst case (TYLKO serie siatki - nie referencyjne)
                gridSeries.stream()
                                .max(Comparator.comparingDouble(MeasurementSeries::getMktTemperature))
                                .ifPresent(worst -> {
                                        stats.setMktWorstValue(worst.getMktTemperature());
                                        stats.setMktWorstSeriesId(worst.getId());
                                });

                // B.4 Najlepszy MKT siatki (min) — best case
                gridSeries.stream()
                                .min(Comparator.comparingDouble(MeasurementSeries::getMktTemperature))
                                .ifPresent(best -> {
                                        stats.setMktBestValue(best.getMktTemperature());
                                        stats.setMktBestSeriesId(best.getId());
                                });

                // B.5 MKT rejestratora referencyjnego
                refSeries.stream()
                                .findFirst()
                                .ifPresent(ref -> {
                                        stats.setMktReferenceValue(ref.getMktTemperature());
                                        stats.setMktReferenceSeriesId(ref.getId());
                                });

                // B.6 Różnica globalMkt − mktReference
                if (stats.getGlobalMkt() != null && stats.getMktReferenceValue() != null) {
                        stats.setMktDeltaInternalVsReference(
                                        stats.getGlobalMkt() - stats.getMktReferenceValue());
                }

                log.debug("Tabela B: worst={}\u00b0C (#{}) best={}\u00b0C (#{}) ref={}\u00b0C delta={}",
                                stats.getMktWorstValue(), stats.getMktWorstSeriesId(),
                                stats.getMktBestValue(), stats.getMktBestSeriesId(),
                                stats.getMktReferenceValue(), stats.getMktDeltaInternalVsReference());
        }

        // =========================================================================
        // TABELA D — Stabilność / Drift / Spike (Analiza trendu)
        // =========================================================================

        /**
         * D — Agregacja statystyk trendu ze wszystkich serii pomiarowych.
         *
         * <p>
         * Korzysta z pól obliczonych przez {@code calculateStatistics()} per seria:
         * <ul>
         * <li>{@code trendCoefficient} — slope regresji liniowej [°C/h]</li>
         * <li>{@code driftClassification} — STABLE | DRIFT | SPIKE | MIXED</li>
         * <li>{@code spikeCount} — liczba anomalii impulsowych</li>
         * </ul>
         */
        private void calculateTableDDriftStats(ValidationSummaryStats stats,
                        List<MeasurementSeries> allSeries) {

                // Podział na serie siatki (urządzenie) vs referencyjne (otoczenie)
                List<MeasurementSeries> gridSeries = allSeries.stream()
                                .filter(s -> Boolean.FALSE.equals(s.getIsReferenceRecorder()))
                                .collect(Collectors.toList());

                List<MeasurementSeries> seriesWithTrend = gridSeries.stream()
                                .filter(s -> s.getTrendCoefficient() != null && s.getMeasurementCount() != null)
                                .collect(Collectors.toList());

                // D.1 — Najsilniejszy dryftu (max |trendCoefficient|) w całej walidacji
                seriesWithTrend.stream()
                                .max(Comparator.comparingDouble(s -> Math.abs(s.getTrendCoefficient())))
                                .ifPresent(worst -> {
                                        stats.setMaxAbsTrendCoefficient(Math.abs(worst.getTrendCoefficient()));
                                        stats.setMaxTrendSeriesId(worst.getId());
                                });

                // D.2 — Średnioważony współczynnik trendu (ważony liczbą pomiarów)
                if (!seriesWithTrend.isEmpty()) {
                        long totalN = seriesWithTrend.stream().mapToLong(MeasurementSeries::getMeasurementCount).sum();
                        if (totalN > 0) {
                                double weightedTrendSum = seriesWithTrend.stream()
                                                .mapToDouble(s -> s.getTrendCoefficient() * s.getMeasurementCount())
                                                .sum();
                                stats.setAvgTrendCoefficient(weightedTrendSum / totalN);
                        }
                }

                // D.3 — Łączna liczba spike'ów (TYLKO serie siatki - nie referencyjne)
                int totalSpikes = gridSeries.stream()
                                .filter(s -> s.getSpikeCount() != null)
                                .mapToInt(MeasurementSeries::getSpikeCount)
                                .sum();
                stats.setTotalSpikeCount(totalSpikes);

                // D.4 — Serie z driftem (DRIFT lub MIXED) (TYLKO serie siatki - nie
                // referencyjne)
                long withDrift = gridSeries.stream()
                                .filter(s -> "DRIFT".equals(s.getDriftClassification())
                                                || "MIXED".equals(s.getDriftClassification()))
                                .count();
                stats.setSeriesWithDriftCount((int) withDrift);

                // D.5 — Serie stabilne (STABLE) (TYLKO serie siatki - nie referencyjne)
                long stableCount = gridSeries.stream()
                                .filter(s -> "STABLE".equals(s.getDriftClassification()))
                                .count();
                stats.setSeriesStableCount((int) stableCount);

                // D.6 — Dominująca klasyfikacja (moda) (TYLKO serie siatki - nie referencyjne)
                gridSeries.stream()
                                .filter(s -> s.getDriftClassification() != null)
                                .collect(Collectors.groupingBy(MeasurementSeries::getDriftClassification,
                                                Collectors.counting()))
                                .entrySet().stream()
                                .max(Map.Entry.comparingByValue())
                                .map(Map.Entry::getKey)
                                .ifPresent(stats::setDominantDriftClassification);

                log.debug("Tabela D: maxTrend={}°C/h (#{}) avgTrend={}°C/h spikes={} withDrift={} stable={} dominant={}",
                                stats.getMaxAbsTrendCoefficient(), stats.getMaxTrendSeriesId(),
                                stats.getAvgTrendCoefficient(), totalSpikes,
                                withDrift, stableCount, stats.getDominantDriftClassification());
        }

        // =========================================================================
        // TABELA C — Czas w zakresie / Zgodność temperaturowa (Compliance)
        // =========================================================================

        /**
         * C — Agregacja statystyk czasu w zakresie i przekroczeń ze wszystkich serii.
         *
         * <p>
         * <b>Obliczenia są sumami — liniowe i dokładne:</b>
         * <ul>
         * <li>C.1: totalTimeInRange = Σ s.totalTimeInRangeMinutes</li>
         * <li>C.2: totalTimeOutOfRange = Σ s.totalTimeOutOfRangeMinutes</li>
         * <li>C.3: compliance% = timeIn/(timeIn+timeOut)×100</li>
         * <li>C.4: totalViolations = Σ s.violationCount</li>
         * <li>C.5: maxViolation = max{ s.maxViolationDurationMinutes }</li>
         * <li>C.6: seriesWithViolations = count{ s | s.violationCount > 0 }</li>
         * <li>C.7: seriesFullyCompliant = count{ s | s.violationCount == 0 }</li>
         * </ul>
         */
        private void calculateTableCComplianceStats(ValidationSummaryStats stats,
                        List<MeasurementSeries> allSeries) {

                // Podział na serie siatki (urządzenie) vs referencyjne (otoczenie)
                List<MeasurementSeries> gridSeries = allSeries.stream()
                                .filter(s -> Boolean.FALSE.equals(s.getIsReferenceRecorder()))
                                .collect(Collectors.toList());

                // C.1 — Łączny czas w zakresie (TYLKO serie siatki - nie referencyjne)
                long timeIn = gridSeries.stream()
                                .filter(s -> s.getTotalTimeInRangeMinutes() != null)
                                .mapToLong(MeasurementSeries::getTotalTimeInRangeMinutes)
                                .sum();
                stats.setTotalTimeInRangeMinutes(timeIn);

                // C.2 — Łączny czas poza zakresem (TYLKO serie siatki - nie referencyjne)
                long timeOut = gridSeries.stream()
                                .filter(s -> s.getTotalTimeOutOfRangeMinutes() != null)
                                .mapToLong(MeasurementSeries::getTotalTimeOutOfRangeMinutes)
                                .sum();
                stats.setTotalTimeOutOfRangeMinutes(timeOut);

                // C.3 — Globalny wskaźnik zgodności [%]
                long totalTime = timeIn + timeOut;
                if (totalTime > 0) {
                        stats.setGlobalCompliancePercentage((double) timeIn / totalTime * 100.0);
                }

                // C.4 — Łączna liczba przekroczeń (TYLKO serie siatki - nie referencyjne)
                int totalViolations = gridSeries.stream()
                                .filter(s -> s.getViolationCount() != null)
                                .mapToInt(MeasurementSeries::getViolationCount)
                                .sum();
                stats.setTotalViolations(totalViolations);

                // C.5 — Najdłuższe przekroczenie (TYLKO serie siatki - nie referencyjne)
                gridSeries.stream()
                                .filter(s -> s.getMaxViolationDurationMinutes() != null)
                                .max(Comparator.comparingLong(MeasurementSeries::getMaxViolationDurationMinutes))
                                .ifPresent(worst -> {
                                        stats.setMaxViolationDurationMinutes(worst.getMaxViolationDurationMinutes());
                                        stats.setMaxViolationSeriesId(worst.getId());
                                });

                // C.6 — Liczba serii z przekroczeniami (TYLKO serie siatki - nie referencyjne)
                long withViolations = gridSeries.stream()
                                .filter(s -> s.getViolationCount() != null && s.getViolationCount() > 0)
                                .count();
                stats.setSeriesWithViolationsCount((int) withViolations);

                // C.7 — Liczba serii zgodnych (TYLKO serie siatki - nie referencyjne)
                long fullyCompliant = gridSeries.stream()
                                .filter(s -> s.getViolationCount() != null && s.getViolationCount() == 0)
                                .count();
                stats.setSeriesFullyCompliantCount((int) fullyCompliant);

                log.debug("Tabela C: timeIn={}min timeOut={}min compliance={}% violations={} maxViol={}min (#{}) withViol={} fullOK={}",
                                timeIn, timeOut,
                                stats.getGlobalCompliancePercentage() != null
                                                ? String.format("%.1f", stats.getGlobalCompliancePercentage())
                                                : "–",
                                totalViolations, stats.getMaxViolationDurationMinutes(),
                                stats.getMaxViolationSeriesId(), withViolations, fullyCompliant);
        }

        // =========================================================================
        // METADANE WALIDACJI
        // =========================================================================

        /**
         * Oblicza metadane walidacji (liczba serii, czasy, interwał).
         */
        private void calculateMetadata(ValidationSummaryStats stats,
                        List<MeasurementSeries> allSeries) {
                int total = allSeries.size();
                long grid = allSeries.stream()
                                .filter(s -> Boolean.FALSE.equals(s.getIsReferenceRecorder()))
                                .count();
                long ref = allSeries.stream()
                                .filter(s -> Boolean.TRUE.equals(s.getIsReferenceRecorder()))
                                .count();

                stats.setTotalSeriesCount(total);
                stats.setGridSeriesCount((int) grid);
                stats.setReferenceSeriesCount((int) ref);

                // Łączna liczba pomiarów
                long totalMeasurements = allSeries.stream()
                                .filter(s -> s.getMeasurementCount() != null)
                                .mapToLong(MeasurementSeries::getMeasurementCount)
                                .sum();
                stats.setTotalMeasurementCount(totalMeasurements);

                // Czas trwania sesji mapowania
                allSeries.stream()
                                .map(MeasurementSeries::getFirstMeasurementTime)
                                .filter(t -> t != null)
                                .min(LocalDateTime::compareTo)
                                .ifPresent(stats::setValidationStartTime);

                allSeries.stream()
                                .map(MeasurementSeries::getLastMeasurementTime)
                                .filter(t -> t != null)
                                .max(LocalDateTime::compareTo)
                                .ifPresent(stats::setValidationEndTime);

                if (stats.getValidationStartTime() != null && stats.getValidationEndTime() != null) {
                        long durationMin = java.time.Duration.between(
                                        stats.getValidationStartTime(),
                                        stats.getValidationEndTime()).toMinutes();
                        stats.setTotalDurationMinutes(durationMin);
                }

                // Dominujący interwał pomiarowy (moda)
                allSeries.stream()
                                .map(MeasurementSeries::getMeasurementIntervalMinutes)
                                .filter(i -> i != null && i > 0)
                                .collect(Collectors.groupingBy(i -> i, Collectors.counting()))
                                .entrySet().stream()
                                .max(Map.Entry.comparingByValue())
                                .map(Map.Entry::getKey)
                                .ifPresent(stats::setDominantIntervalMinutes);

                log.debug("Metadane walidacji: serie={}, grid={}, ref={}, pomiary={}, czas={}min",
                                total, grid, ref, totalMeasurements, stats.getTotalDurationMinutes());
        }

        // =========================================================================
        // GUM UNCERTAINTY BUDGET - BUDŻET NIEPEWNOŚCI WALIDACJI
        // =========================================================================

        /**
         * Oblicza zagregowany budżet niepewności dla całej walidacji
         * zgodnie z GUM (Guide to the Expression of Uncertainty in Measurement).
         *
         * Łączy niepewności ze wszystkich serii siatki i dodaje komponent przestrzenny.
         */
        private void calculateValidationUncertaintyBudget(ValidationSummaryStats stats,
                        List<MeasurementSeries> allSeries) {
                try {
                        // Filtruj serie siatki (nie referencyjne) - zgodnie z praktyką w innych
                        // metodach
                        List<MeasurementSeries> gridSeries = allSeries.stream()
                                        .filter(s -> Boolean.FALSE.equals(s.getIsReferenceRecorder()))
                                        .collect(Collectors.toList());

                        if (gridSeries.isEmpty()) {
                                log.warn("⚠️ BRAK SERII SIATKI dla walidacji {} - pomijam budżet niepewności",
                                                stats.getValidation().getId());
                                return;
                        }

                        // Oblicz zagregowany budżet niepewności
                        UncertaintyBudget validationBudget = validationUncertaintyBudgetService
                                        .calculateValidationUncertaintyBudget(gridSeries);

                        // Zapisz budżet w statystykach
                        stats.setValidationUncertaintyBudget(validationBudget);

                        // Zaktualizuj maksymalną niepewność rozszerzoną w tabeli A
                        // (może być używana w istniejących polach)
                        if (stats.getGlobalExpandedUncertainty() == null ||
                                        validationBudget.getExpandedUncertainty() > stats
                                                        .getGlobalExpandedUncertainty()) {
                                stats.setGlobalExpandedUncertainty(validationBudget.getExpandedUncertainty());
                        }

                        // Generuj rekomendacje poprawy jakości
                        String recommendations = validationUncertaintyBudgetService
                                        .generateImprovementRecommendations(validationBudget);

                        log.info("✅ GMP VALIDATION UNCERTAINTY: Walidacja {} | U_expanded={:.4f}°C | " +
                                        "Dominant: {} | Quality: {:.0f}% | Grid series: {}",
                                        stats.getValidation().getId(),
                                        validationBudget.getExpandedUncertainty(),
                                        validationBudget.getDominantUncertaintySource(),
                                        calculateQualityIndex(stats, validationBudget),
                                        gridSeries.size());

                        log.debug("🔬 UNCERTAINTY RECOMMENDATIONS: {}", recommendations);

                } catch (Exception e) {
                        log.error("❌ BŁĄD obliczania budżetu niepewności walidacji {}: {}",
                                        stats.getValidation().getId(), e.getMessage(), e);

                        // W przypadku błędu - nie blokuj zapisywania statystyk, ale zapisz w logach
                        log.warn("⚠️ Statystyki walidacji zostaną zapisane bez budżetu niepewności");
                }
        }

        /**
         * Oblicza wskaźnik jakości niepewności (0-100%) na podstawie zakresu docelowego
         */
        private double calculateQualityIndex(ValidationSummaryStats stats, UncertaintyBudget budget) {
                // Oszacuj zakres temperatury na podstawie globalnych statystyk
                Double globalRange = null;
                if (stats.getGlobalMaxTemp() != null && stats.getGlobalMinTemp() != null) {
                        globalRange = stats.getGlobalMaxTemp() - stats.getGlobalMinTemp();
                }

                // Jeśli brak zakresu, użyj typowego zakresu dla chłodzenia farmaceutycznego
                // (6°C dla 2-8°C)
                if (globalRange == null || globalRange < 1.0) {
                        globalRange = 6.0; // typowy zakres 2-8°C
                }

                return validationUncertaintyBudgetService.calculateUncertaintyQualityIndex(budget, globalRange);
        }
}
