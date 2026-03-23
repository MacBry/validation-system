package com.mac.bry.validationsystem.validation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

/**
 * Rekord elektronicznego podpisu walidacji.
 * Tworzony jednorazowo przy zatwierdzeniu walidacji (zmiana statusu na COMPLETED).
 * Przechowuje dane podpisującego, treść oświadczenia, informacje o certyfikacie
 * oraz SHA-256 podpisanego dokumentu PDF.
 */
@Entity
@Table(name = "validation_signatures")
@Audited
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_id", nullable = false, unique = true)
    private Validation validation;

    /** Nazwa użytkownika który podpisał */
    @Column(name = "signed_by", nullable = false, length = 100)
    private String signedBy;

    /** Dokładna data i czas podpisu */
    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    /** Treść oświadczenia (signing intent) wg Annex 11 §12 */
    @Column(name = "signing_intent", nullable = false, columnDefinition = "TEXT")
    private String signingIntent;

    /** Podmiot certyfikatu organizacji (X.500 DN) */
    @Column(name = "cert_subject", length = 255)
    private String certSubject;

    /** Numer seryjny certyfikatu (hex) */
    @Column(name = "cert_serial", length = 100)
    private String certSerial;

    /** SHA-256 podpisanego PDF */
    @Column(name = "document_hash", length = 64)
    private String documentHash;

    /** Ścieżka do pliku podpisanego PDF na dysku */
    @Column(name = "signed_pdf_path", length = 500)
    private String signedPdfPath;
}
