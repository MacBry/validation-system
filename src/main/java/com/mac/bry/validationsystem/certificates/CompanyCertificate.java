package com.mac.bry.validationsystem.certificates;

import com.mac.bry.validationsystem.company.Company;
import jakarta.persistence.*;
import lombok.*;
import com.mac.bry.validationsystem.security.converter.AttributeEncryptionConverter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.time.LocalDateTime;

@Entity
@Table(name = "company_certificates")
@Audited
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    private String alias;

    private String subject;

    private String issuer;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "sha256_fingerprint")
    private String sha256Fingerprint;

    @NotAudited
    @Lob
    @Column(name = "keystore_data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] keystoreData;

    @NotAudited
    @Convert(converter = AttributeEncryptionConverter.class)
    @Column(name = "keystore_password", nullable = false)
    private String keystorePassword;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Transient
    public boolean isExpired() {
        return validTo != null && validTo.isBefore(LocalDateTime.now());
    }

    @Transient
    public boolean isExpiringSoon() {
        return !isExpired() && validTo != null
                && validTo.isBefore(LocalDateTime.now().plusDays(30));
    }
}
