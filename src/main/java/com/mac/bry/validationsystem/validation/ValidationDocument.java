package com.mac.bry.validationsystem.validation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

/**
 * Rejestr dokumentów generowanych dla danej walidacji.
 * Przechowuje numer dokumentu, licznik generacji, autorów i hash SHA-256 (tylko przy pierwszej generacji).
 */
@Entity
@Table(name = "validation_documents")
@Audited
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_id", nullable = false)
    private Validation validation;

    /**
     * Numer dokumentu, np. SW/LZTHLA/2026/001
     */
    @Column(name = "document_number", nullable = false, unique = true, length = 60)
    private String documentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    /**
     * Ile razy wygenerowano ten dokument (każde pobranie inkrementuje licznik).
     */
    @Column(name = "generation_count", nullable = false)
    @Builder.Default
    private Integer generationCount = 0;

    @Column(name = "first_generated_at")
    private LocalDateTime firstGeneratedAt;

    @Column(name = "first_generated_by", length = 100)
    private String firstGeneratedBy;

    @Column(name = "last_generated_at")
    private LocalDateTime lastGeneratedAt;

    @Column(name = "last_generated_by", length = 100)
    private String lastGeneratedBy;

    /**
     * Hash SHA-256 zawartości przy PIERWSZEJ generacji – pozwala wykryć, czy późniejsza
     * regeneracja dała identyczny wynik (brak zmian danych) lub czy dane się zmieniły.
     * Null jeśli dokument nie zawiera binarnej treści (np. Word nie jest hashowany).
     */
    @Column(name = "pdf_hash_sha256", length = 64)
    private String pdfHashSha256;

    /**
     * True jeśli przy ostatniej regeneracji hash dokumentu różnił się od pierwszej generacji,
     * co oznacza że dane wejściowe (walidacja/serie) uległy zmianie między generacjami.
     */
    @Column(name = "data_changed", nullable = false)
    @Builder.Default
    private Boolean dataChanged = false;
}
