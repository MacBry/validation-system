package com.mac.bry.validationsystem.wizard.dto;

import com.mac.bry.validationsystem.wizard.MappingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for mapping status check results in PERIODIC_REVALIDATION wizard step 4.
 *
 * <p>
 * Represents the outcome of checking the device's temperature mapping history.
 * The mapping status is auto-calculated from the database based on the 730-day threshold
 * (GMP standard). If the mapping is OVERDUE or NEVER performed, the technician must
 * explicitly acknowledge before proceeding.
 * </p>
 *
 * Mapped to {@code validation_plan_data} columns:
 * mapping_check_date, mapping_status, mapping_overdue_acknowledged
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MappingStatusDto {

    /**
     * Data sprawdzenia historii mapowan.
     * Auto-populated with current date when the check is performed.
     */
    private LocalDate mappingCheckDate;

    /**
     * Status mapowania: CURRENT (< 730 dni), OVERDUE (>= 730 dni), NEVER.
     * Auto-calculated from the last MAPPING-type validation date for this device.
     */
    private MappingStatus mappingStatus;

    /**
     * Potwierdzenie swiadomej kontynuacji pomimo overdue mapowania.
     * Required when mappingStatus is OVERDUE or NEVER.
     */
    private Boolean mappingOverdueAcknowledged;

    /**
     * Display name of the mapping status for UI rendering.
     */
    private String statusDisplayName;

    /**
     * CSS class for status badge rendering.
     */
    private String statusCssClass;

    /**
     * Human-readable description of the mapping status.
     */
    private String statusDescription;

    /**
     * Number of days since the last mapping validation (null if NEVER).
     */
    private Long daysSinceLastMapping;

    /**
     * Data ostatniego ukonczonego mapowania.
     */
    private LocalDate lastMappingDate;

    /**
     * Data do ktorej mapowanie jest wazne (last + 730 dni).
     */
    private LocalDate mappingValidUntil;

    /**
     * Liczba czujnikow uzytych w ostatnim mapowaniu.
     */
    private Integer sensorCount;

    /**
     * Pozycje i wyniki czujnikow z ostatniego mapowania.
     */
    private java.util.List<SensorPositionDto> sensorPositions;

    // --- Manual entry fields for external mapping ---
    private LocalDate lastMappingDateManual;
    private String mappingProtocolNumberManual;
    private LocalDate mappingValidUntilManual;
    private Integer sensorCountManual;
    private String controllerSensorLocationManual;
    private String hotSpotLocationManual;
    private String coldSpotLocationManual;

    /**
     * Nested DTO for sensor position result.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SensorPositionDto {
        private String position;
        private String recorderSerialNumber;
        private boolean resultPassed;
    }

    /**
     * Creates a DTO from a MappingStatus enum with calculated metadata.
     */
    public static MappingStatusDto fromStatus(MappingStatus status, LocalDate checkDate,
                                              Boolean acknowledged, Long daysSinceLastMapping,
                                              LocalDate lastMappingDate, Integer sensorCount,
                                              java.util.List<SensorPositionDto> sensorPositions) {
        return MappingStatusDto.builder()
            .mappingCheckDate(checkDate)
            .mappingStatus(status)
            .mappingOverdueAcknowledged(acknowledged)
            .statusDisplayName(status.getDisplayName())
            .statusCssClass(status.getCssClass())
            .statusDescription(status.getDescription())
            .daysSinceLastMapping(daysSinceLastMapping)
            .lastMappingDate(lastMappingDate)
            .mappingValidUntil(lastMappingDate != null ? lastMappingDate.plusDays(MappingStatus.MAPPING_VALIDITY_DAYS) : null)
            .sensorCount(sensorCount)
            .sensorPositions(sensorPositions)
            .build();
    }

    /**
     * Checks if the technician is required to acknowledge the mapping status.
     */
    public boolean requiresAcknowledgement() {
        return mappingStatus != null && mappingStatus.isBlocking();
    }

    /**
     * Checks if the step is complete (status checked and acknowledged if required).
     */
    public boolean isComplete() {
        if (mappingStatus == null) {
            return false;
        }
        if (mappingStatus.isBlocking()) {
            return Boolean.TRUE.equals(mappingOverdueAcknowledged);
        }
        return true;
    }
}
