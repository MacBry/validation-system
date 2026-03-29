package com.mac.bry.validationsystem.wizard.plandata;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Value object for the technician's signature on the validation plan (wizard step 8).
 *
 * <p>
 * After the technician signs the plan, the wizard status transitions to
 * AWAITING_QA_APPROVAL and the planning phase steps become locked.
 * </p>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TechnikSignature {

    /**
     * Timestamp of the technician's signature
     */
    @Column(name = "plan_technik_signed_at")
    private LocalDateTime signedAt;

    /**
     * Username (login) of the signing technician
     */
    @Column(name = "plan_technik_username", length = 50)
    private String username;

    /**
     * Full name of the signing technician (as displayed on the plan)
     */
    @Column(name = "plan_technik_full_name", length = 200)
    private String fullName;

    /**
     * Checks if the technician has signed the plan
     */
    public boolean isSigned() {
        return signedAt != null && username != null;
    }
}
