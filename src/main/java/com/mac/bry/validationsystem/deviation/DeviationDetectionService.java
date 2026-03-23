package com.mac.bry.validationsystem.deviation;

import com.mac.bry.validationsystem.measurement.MeasurementPoint;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Serwis do automatycznego wykrywania naruszeń temperaturowych
 * z danych pomiarowych (MeasurementPoint).
 *
 * <p>
 * Iteruje po punktach pomiarowych każdej serii w kolejności czasu,
 * wykrywa ciągłe odcinki poza granicami (lowerLimit / upperLimit),
 * tworzy {@link DeviationEvent} z klasyfikacją severity wg GMP/GDP.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviationDetectionService {

    private final DeviationEventRepository deviationEventRepository;
    private final ValidationRepository validationRepository;

    /**
     * Wykrywa naruszenia temperaturowe dla danej walidacji i zapisuje je do bazy.
     * Przed zapisem usuwa wcześniejsze wyniki (idempotent).
     *
     * @param validationId ID walidacji
     * @return lista wykrytych naruszeń
     */
    @Transactional
    public List<DeviationEvent> detectAndSave(Long validationId) {
        Validation validation = validationRepository.findById(validationId)
                .orElseThrow(() -> new IllegalArgumentException("Walidacja nie znaleziona: " + validationId));

        // Usuń poprzednie wyniki (re-kalkulacja)
        deviationEventRepository.deleteByValidationId(validationId);

        List<DeviationEvent> allEvents = new ArrayList<>();

        // Filtruj tylko serie siatki (urządzenie) - nie analizuj rejestratorów referencyjnych (otoczenie)
        List<MeasurementSeries> gridSeries = validation.getMeasurementSeries().stream()
                .filter(s -> Boolean.FALSE.equals(s.getIsReferenceRecorder()))
                .toList();

        long referenceCount = validation.getMeasurementSeries().size() - gridSeries.size();
        log.debug("Analiza odchyleń: {} serii siatki, {} rejestratorów referencyjnych (pominięte)",
                  gridSeries.size(), referenceCount);

        for (MeasurementSeries series : gridSeries) {
            Double lower = series.getLowerLimit();
            Double upper = series.getUpperLimit();

            // Fallback do limitów urządzenia (to z nich zazwyczaj korzysta aplikacja)
            if (series.getCoolingDevice() != null) {
                if (lower == null) {
                    lower = series.getCoolingDevice().getMinOperatingTemp();
                }
                if (upper == null) {
                    upper = series.getCoolingDevice().getMaxOperatingTemp();
                }
            }

            // Pomijamy serie bez granic
            if (lower == null && upper == null) {
                log.debug("Seria {} nie ma ustawionych granic (nawet na urządzeniu) — pomijam detekcję",
                        series.getId());
                continue;
            }

            List<MeasurementPoint> points = series.getMeasurementPoints();
            if (points == null || points.size() < 2) {
                continue;
            }

            List<DeviationEvent> seriesEvents = detectViolationsInSeries(validation, series, points, lower, upper);
            allEvents.addAll(seriesEvents);
        }

        if (!allEvents.isEmpty()) {
            deviationEventRepository.saveAll(allEvents);
            log.info("Wykryto {} naruszeń dla walidacji {}", allEvents.size(), validationId);
        } else {
            log.info("Brak naruszeń temperaturowych dla walidacji {}", validationId);
        }

        return allEvents;
    }

    /**
     * Zwraca listę naruszeń dla danej walidacji (posortowane: CRITICAL → MAJOR →
     * MINOR, potem czas).
     */
    public List<DeviationEvent> findByValidationId(Long validationId) {
        return deviationEventRepository.findByValidationIdOrderBySeverityAscStartTimeAsc(validationId);
    }

    // =========================================================================
    // PRYWATNE METODY DETEKCJI
    // =========================================================================

    private List<DeviationEvent> detectViolationsInSeries(
            Validation validation,
            MeasurementSeries series,
            List<MeasurementPoint> points,
            Double lower,
            Double upper) {

        List<DeviationEvent> events = new ArrayList<>();

        boolean inViolation = false;
        LocalDateTime violationStart = null;
        double peakTemp = 0;
        double violatedLimit = 0;
        ViolationType currentType = null;

        for (int i = 0; i < points.size(); i++) {
            MeasurementPoint point = points.get(i);
            double temp = point.getTemperature();
            LocalDateTime time = point.getMeasurementTime();

            boolean isAboveUpper = (upper != null && temp > upper);
            boolean isBelowLower = (lower != null && temp < lower);
            boolean isOutOfRange = isAboveUpper || isBelowLower;

            if (isOutOfRange) {
                ViolationType type = isAboveUpper ? ViolationType.ABOVE_UPPER : ViolationType.BELOW_LOWER;
                double limit = isAboveUpper ? upper : lower;

                if (!inViolation) {
                    // Początek nowego naruszenia
                    inViolation = true;
                    violationStart = time;
                    peakTemp = temp;
                    violatedLimit = limit;
                    currentType = type;
                } else {
                    // Kontynuacja naruszenia — aktualizuj peak
                    if (currentType == ViolationType.ABOVE_UPPER && temp > peakTemp) {
                        peakTemp = temp;
                    } else if (currentType == ViolationType.BELOW_LOWER && temp < peakTemp) {
                        peakTemp = temp;
                    }
                }
            } else {
                if (inViolation) {
                    // Koniec naruszenia — zapisz event
                    DeviationEvent event = buildEvent(validation, series, violationStart, time,
                            peakTemp, violatedLimit, currentType);
                    events.add(event);

                    inViolation = false;
                    violationStart = null;
                }
            }
        }

        // Zamknij otwarte naruszenie na końcu serii
        if (inViolation && violationStart != null) {
            LocalDateTime lastTime = points.get(points.size() - 1).getMeasurementTime();
            DeviationEvent event = buildEvent(validation, series, violationStart, lastTime,
                    peakTemp, violatedLimit, currentType);
            events.add(event);
        }

        return events;
    }

    private DeviationEvent buildEvent(
            Validation validation,
            MeasurementSeries series,
            LocalDateTime start,
            LocalDateTime end,
            double peakTemp,
            double violatedLimit,
            ViolationType type) {

        long durationMinutes = Math.max(1, Duration.between(start, end).toMinutes());
        double deltaTemp = Math.abs(peakTemp - violatedLimit);
        DeviationSeverity severity = DeviationSeverity.classify(durationMinutes, deltaTemp);

        return DeviationEvent.builder()
                .validation(validation)
                .measurementSeries(series)
                .startTime(start)
                .endTime(end)
                .durationMinutes(durationMinutes)
                .peakTemperature(peakTemp)
                .violatedLimit(violatedLimit)
                .violationType(type)
                .severity(severity)
                .build();
    }
}
