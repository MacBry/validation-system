package com.mac.bry.validationsystem.department;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.laboratory.Laboratory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.util.ArrayList;
import java.util.List;

/**
 * Encja reprezentująca dział w firmie
 * Przykład: Dział Laboratoryjny, Dział Preparatyki
 */
@Entity
@Table(name = "departments")
@Audited
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Department {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Firma do której należy dział
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    
    /**
     * Nazwa działu
     * Przykład: "Dział Laboratoryjny"
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    /**
     * Skrót działu (używany w numerach RPW)
     * Przykład: "LAB", "PREP", "IMM"
     */
    @Column(name = "abbreviation", nullable = false, unique = true, length = 20)
    private String abbreviation;
    
    /**
     * Opis działu (opcjonalny)
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    /**
     * Czy dział ma pracownie (podpracownie)?
     * true = dział ma pracownie (np. Dział Laboratoryjny ma ORS, PALZPZ)
     * false = dział nie ma pracowni (np. Dział Preparatyki)
     */
    @Column(name = "has_laboratories", nullable = false)
    private Boolean hasLaboratories = false;
    
    /**
     * Pracownie należące do tego działu (opcjonalne)
     */
    @NotAudited
    @OneToMany(mappedBy = "department", fetch = FetchType.LAZY)
    private List<Laboratory> laboratories = new ArrayList<>();
}
