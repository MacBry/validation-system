package com.mac.bry.validationsystem.wizard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for QA electronic approval of the validation plan.
 *
 * <p>
 * Submitted by a QA user on the plan-review page to electronically co-sign
 * the validation plan (FDA 21 CFR Part 11 Path A). The password is verified
 * server-side against the QA user's stored credentials before the approval
 * timestamp is written to {@link com.mac.bry.validationsystem.wizard.plandata.QaApprovalPath}.
 * </p>
 *
 * <p>
 * Per GMP Annex 11 §12 and FDA 21 CFR Part 11 §11.200(a)(1), an electronic
 * signature requires both a username and password. The {@code intent} field
 * captures the meaning of the signature (displayed on the plan PDF).
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QaApprovalDto {

    /**
     * QA user's password for electronic signature verification.
     * Required — used to authenticate the signing action.
     */
    @NotBlank(message = "Haslo jest wymagane do podpisania elektronicznego")
    private String password;

    /**
     * Statement of intent / reason for the electronic signature.
     * Required — captured in the audit trail and on the plan PDF.
     * Example: "Zatwierdzenie planu walidacji przez QA"
     */
    @NotBlank(message = "Oswiadczenie intencji podpisu jest wymagane")
    @Size(max = 500, message = "Oswiadczenie intencji moze miec maksymalnie 500 znakow")
    private String intent;

    /**
     * Full name of the QA signer as it should appear on the plan PDF.
     * Optional — if blank, the display name from the user account is used.
     */
    @Size(max = 200, message = "Imie i nazwisko moze miec maksymalnie 200 znakow")
    private String qaSignerName;

    /**
     * Title / position of the QA signer (e.g., "Kierownik Działu QA").
     * Optional — displayed on the plan PDF next to the QA signature.
     */
    @Size(max = 200, message = "Stanowisko moze miec maksymalnie 200 znakow")
    private String qaSignerTitle;
}
