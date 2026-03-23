package com.mac.bry.validationsystem.materialtype;

import com.mac.bry.validationsystem.company.Company;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;

/**
 * Typ materiału przechowywanego w urządzeniu chłodniczym
 */
@Entity
@Table(name = "material_types")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "name", nullable = false, length = 100)
    @NotBlank(message = "Nazwa materiału jest wymagana")
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "min_storage_temp")
    private Double minStorageTemp;

    @Column(name = "max_storage_temp")
    private Double maxStorageTemp;

    @Column(name = "activation_energy", precision = 10, scale = 4)
    private BigDecimal activationEnergy;

    @Column(name = "standard_source", length = 255)
    private String standardSource;

    @Column(name = "application", length = 255)
    private String application;

    /**
     * Czy materiał jest aktywny (można go wybierać dla nowych urządzeń)
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Zwraca sformatowaną minimalną temperaturę przechowywania
     */
    public String getFormattedMinStorageTemp() {
        return minStorageTemp != null ? String.format("%.1f°C", minStorageTemp) : "–";
    }

    /**
     * Zwraca sformatowaną maksymalną temperaturę przechowywania
     */
    public String getFormattedMaxStorageTemp() {
        return maxStorageTemp != null ? String.format("%.1f°C", maxStorageTemp) : "–";
    }

    /**
     * Zwraca zakres temperatur jako string
     */
    public String getTemperatureRange() {
        if (minStorageTemp == null && maxStorageTemp == null) {
            return "–";
        }
        if (minStorageTemp == null) {
            return "do " + getFormattedMaxStorageTemp();
        }
        if (maxStorageTemp == null) {
            return "od " + getFormattedMinStorageTemp();
        }
        return getFormattedMinStorageTemp() + " do " + getFormattedMaxStorageTemp();
    }

    @Override
    public String toString() {
        return "MaterialType{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", tempRange=" + getTemperatureRange() +
                '}';
    }
}
