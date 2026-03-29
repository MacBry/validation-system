package com.mac.bry.validationsystem.wizard.plandata;

/**
 * Method used for QA approval of the validation plan.
 *
 * <p>
 * Two paths are supported per FDA 21 CFR Part 11:
 * - Electronic signature within the system
 * - Scanned signed document uploaded to the system
 * </p>
 */
public enum QaApprovalMethod {

    /**
     * QA approves electronically within the system (2-person co-signature)
     */
    ELECTRONIC_SIGNATURE,

    /**
     * QA signs a printed plan, which is then scanned and uploaded
     */
    SCANNED_DOCUMENT
}
