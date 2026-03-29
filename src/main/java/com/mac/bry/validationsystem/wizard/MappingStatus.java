package com.mac.bry.validationsystem.wizard;

import lombok.Getter;

/**
 * Status of temperature mapping for a cooling device in periodic revalidation.
 *
 * <p>
 * Determined automatically by comparing the date of the last MAPPING-type validation
 * against the 730-day (2-year) threshold required by GMP standards.
 * </p>
 *
 * <ul>
 *   <li>CURRENT: Last mapping within 730 days - no action required</li>
 *   <li>OVERDUE: Last mapping older than 730 days - requires technician acknowledgement</li>
 *   <li>NEVER: No mapping validation found for this device - requires technician acknowledgement</li>
 * </ul>
 */
@Getter
public enum MappingStatus {

    /**
     * Mapping is current (last mapping within 730 days)
     */
    CURRENT(
        "Aktualne",
        "Mapowanie wykonane w ciagu ostatnich 730 dni",
        "vcc-status--valid",
        false
    ),

    /**
     * Mapping is overdue (last mapping older than 730 days)
     */
    OVERDUE(
        "Przeterminowane",
        "Mapowanie wykonane ponad 730 dni temu - wymagane zatwierdzenie",
        "vcc-status--warning",
        true
    ),

    /**
     * No mapping has ever been performed for this device
     */
    NEVER(
        "Nigdy nie wykonano",
        "Mapowanie nie zostalo nigdy wykonane - wymagane zatwierdzenie",
        "vcc-status--invalid",
        true
    );

    /** Maximum number of days before mapping is considered overdue */
    public static final int MAPPING_VALIDITY_DAYS = 730;

    private final String displayName;
    private final String description;
    private final String cssClass;
    private final boolean requiresAcknowledgement;

    MappingStatus(String displayName, String description, String cssClass, boolean requiresAcknowledgement) {
        this.displayName = displayName;
        this.description = description;
        this.cssClass = cssClass;
        this.requiresAcknowledgement = requiresAcknowledgement;
    }

    /**
     * Checks if this status requires the technician to explicitly acknowledge
     * before proceeding with the revalidation wizard.
     */
    public boolean isBlocking() {
        return requiresAcknowledgement;
    }
}
