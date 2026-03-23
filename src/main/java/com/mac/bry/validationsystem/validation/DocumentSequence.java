package com.mac.bry.validationsystem.validation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sekwencer numerów dokumentów – jeden rekord per (typ, pracownia, rok).
 * Blokada pesymistyczna chroni przed race-condition przy równoległych generacjach.
 */
@Entity
@Table(name = "document_sequence",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_doc_seq",
                columnNames = {"type_prefix", "lab_abbrev", "year"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type_prefix", nullable = false, length = 10)
    private String typePrefix;

    @Column(name = "lab_abbrev", nullable = false, length = 20)
    private String labAbbrev;

    @Column(name = "`year`", nullable = false)
    private Integer year;

    @Column(name = "last_number", nullable = false)
    @Builder.Default
    private Integer lastNumber = 0;
}
