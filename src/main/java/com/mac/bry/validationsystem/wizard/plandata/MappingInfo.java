package com.mac.bry.validationsystem.wizard.plandata;

import com.mac.bry.validationsystem.measurement.RecorderPosition;
import com.mac.bry.validationsystem.wizard.MappingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Value object for mapping status information (wizard step 4).
 *
 * <p>
 * Auto-filled from the database by checking the last MAPPING-type validation
 * for the selected device. If OVERDUE or NEVER, the technician must explicitly
 * acknowledge before continuing.
 * </p>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MappingInfo {

    /**
     * Date when the mapping history was checked
     */
    @Column(name = "mapping_check_date")
    private LocalDate mappingCheckDate;

    /**
     * Computed mapping status: CURRENT, OVERDUE, or NEVER
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_status", length = 20)
    private MappingStatus mappingStatus;

    /**
     * Whether the technician acknowledged an OVERDUE or NEVER status
     */
    @Column(name = "mapping_overdue_acknowledged")
    private Boolean mappingOverdueAcknowledged;

    // --- Manual entry fields for external mapping (Annex 15 compliance) ---

    @Column(name = "last_mapping_date_manual")
    private LocalDate lastMappingDateManual;

    @Column(name = "mapping_protocol_number_manual", length = 100)
    private String mappingProtocolNumberManual;

    @Column(name = "mapping_valid_until_manual")
    private LocalDate mappingValidUntilManual;

    @Column(name = "sensor_count_manual")
    private Integer sensorCountManual;

    @Enumerated(EnumType.STRING)
    @Column(name = "controller_sensor_location_manual", length = 50)
    private RecorderPosition controllerSensorLocationManual;

    @Enumerated(EnumType.STRING)
    @Column(name = "hot_spot_location_manual", length = 50)
    private RecorderPosition hotSpotLocationManual;

    @Enumerated(EnumType.STRING)
    @Column(name = "cold_spot_location_manual", length = 50)
    private RecorderPosition coldSpotLocationManual;

    /**
     * Checks if this mapping status requires acknowledgement and has not yet been acknowledged
     */
    public boolean needsAcknowledgement() {
        return mappingStatus != null
            && mappingStatus.isBlocking()
            && (mappingOverdueAcknowledged == null || !mappingOverdueAcknowledged);
    }
}
