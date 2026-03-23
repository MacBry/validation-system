package com.mac.bry.validationsystem.deviation;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Analiza odchylenia uzupełniana przez użytkownika.
 *
 * <p>
 * Powiązana 1-do-1 z {@link DeviationEvent}. Zawiera pola wymagane
 * przez GMP/GDP do dokumentacji CAPA (Corrective and Preventive Actions):
 * </p>
 * <ul>
 * <li><b>rootCause</b> — analiza przyczyn źródłowych</li>
 * <li><b>productImpact</b> — ocena wpływu na produkt</li>
 * <li><b>correctiveAction</b> — działania korygujące i zapobiegawcze</li>
 * </ul>
 */
@Entity
@Table(name = "deviation_analyses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviationAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deviation_event_id", nullable = false, unique = true)
    private DeviationEvent deviationEvent;

    /** Analiza przyczyn źródłowych (root cause analysis) */
    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    /** Ocena wpływu na produkt */
    @Column(name = "product_impact", columnDefinition = "TEXT")
    private String productImpact;

    /** Działania korygujące i zapobiegawcze (CAPA) */
    @Column(name = "corrective_action", columnDefinition = "TEXT")
    private String correctiveAction;

    /** Kto wprowadził analizę */
    @Column(name = "analyzed_by", length = 100)
    private String analyzedBy;

    /** Kiedy wprowadzono analizę */
    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;
}
