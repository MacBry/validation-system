package com.mac.bry.validationsystem.wizard;

import com.mac.bry.validationsystem.wizard.dto.MappingStatusDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Service for computing mapping status (GMP Annex 15 §10).
 *
 * Determines if a device's mapping is CURRENT (< 730 days), OVERDUE (>= 730 days),
 * or NEVER performed based on validation history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MappingStatusService {

    private final ValidationDraftRepository draftRepository;

    private static final int MAPPING_THRESHOLD_DAYS = 730; // ~2 years

    /**
     * Compute mapping status for a device.
     *
     * Queries the last COMPLETED MAPPING validation for the device
     * and calculates days elapsed since that validation.
     *
     * @param coolingDeviceId ID of the cooling device
     * @return MappingStatusDto with status (CURRENT/OVERDUE/NEVER) and days elapsed
     */
    public MappingStatusDto computeMappingStatus(Long coolingDeviceId) {
        log.debug("Computing mapping status for device ID: {}", coolingDeviceId);

        // Find last completed MAPPING validation for this device
        Optional<ValidationDraft> lastMapping = draftRepository
            .findTopByCoolingDeviceIdAndProcedureTypeAndStatusOrderByUpdatedAtDesc(
                coolingDeviceId,
                ValidationProcedureType.MAPPING,
                WizardStatus.COMPLETED
            );

        if (lastMapping.isEmpty()) {
            log.debug("No mapping validation found for device {}", coolingDeviceId);
            return MappingStatusDto.fromStatus(MappingStatus.NEVER, LocalDate.now(), false, null, null, 0, java.util.Collections.emptyList());
        }

        ValidationDraft draft = lastMapping.get();
        LocalDate lastMappingDate = draft.getUpdatedAt().toLocalDate();
        LocalDate today = LocalDate.now();
        long daysAgo = ChronoUnit.DAYS.between(lastMappingDate, today);

        MappingStatus status = (daysAgo < MAPPING_THRESHOLD_DAYS) ? MappingStatus.CURRENT : MappingStatus.OVERDUE;

        // Fetch sensor positions and results if associated validation exists
        java.util.List<MappingStatusDto.SensorPositionDto> sensorPositions = new java.util.ArrayList<>();
        Integer sensorCount = 0;

        if (draft.getCompletedValidation() != null) {
            com.mac.bry.validationsystem.validation.Validation v = draft.getCompletedValidation();
            if (v.getMeasurementSeries() != null) {
                sensorCount = v.getMeasurementSeries().size();
                for (com.mac.bry.validationsystem.measurement.MeasurementSeries s : v.getMeasurementSeries()) {
                    sensorPositions.add(MappingStatusDto.SensorPositionDto.builder()
                        .position(s.getRecorderPosition() != null ? s.getRecorderPosition().name() : "REF")
                        .recorderSerialNumber(s.getRecorderSerialNumber())
                        .resultPassed(s.getViolationCount() != null && s.getViolationCount() == 0)
                        .build());
                }
            }
        }

        log.debug("Device {} mapping status: {} ({} days ago), sensors: {}", coolingDeviceId, status, daysAgo, sensorCount);

        return MappingStatusDto.fromStatus(status, today, false, daysAgo, lastMappingDate, sensorCount, sensorPositions);
    }
}
