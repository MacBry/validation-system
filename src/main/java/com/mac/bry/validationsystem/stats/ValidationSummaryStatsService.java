package com.mac.bry.validationsystem.stats;

import java.util.Optional;

/**
 * Serwis obliczania i zarządzania zbiorczymi statystykami walidacji.
 *
 * <p>
 * Odpowiada za agregację statystyk ze wszystkich {@code MeasurementSeries}
 * przypisanych do walidacji w jedną spójną reprezentację
 * {@link ValidationSummaryStats}.
 * </p>
 *
 * <p>
 * Metodologia obliczania każdej statystyki opisana jest w:<br>
 * {@code docs/VALIDATION_SUMMARY_STATS_METODOLOGIA.md}
 * </p>
 */
public interface ValidationSummaryStatsService {

    /**
     * Oblicza i zapisuje statystyki zbiorcze dla walidacji.
     *
     * <p>
     * Jeśli rekord statystyk dla danej walidacji już istnieje — zostaje
     * zaktualizowany.
     * Jeśli nie istnieje — zostaje utworzony.
     * </p>
     *
     * <p>
     * <strong>Wydajność:</strong> Wszystkie obliczenia bazują na polach
     * zagregowanych
     * {@code MeasurementSeries} — nie są ładowane surowe {@code MeasurementPoint}.
     * </p>
     *
     * @param validationId ID walidacji
     * @return DTO z obliczonymi statystykami zbiorczymi
     * @throws IllegalArgumentException jeśli walidacja o podanym ID nie istnieje
     */
    ValidationSummaryStatsDto calculateAndSave(Long validationId);

    /**
     * Pobiera statystyki zbiorcze dla walidacji.
     *
     * @param validationId ID walidacji
     * @return Optional z DTO statystyk lub empty jeśli jeszcze nie obliczono
     */
    Optional<ValidationSummaryStatsDto> findByValidationId(Long validationId);

    /**
     * Usuwa statystyki zbiorcze powiązane z walidacją.
     * Wywoływane przy usuwaniu walidacji.
     *
     * @param validationId ID walidacji
     */
    void deleteByValidationId(Long validationId);
}
