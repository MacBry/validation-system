package com.mac.bry.validationsystem.device;

import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.laboratory.Laboratory;
import com.mac.bry.validationsystem.materialtype.MaterialType;
import com.mac.bry.validationsystem.validationplan.ValidationPlanNumber;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "cooling_devices")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoolingDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_number", nullable = false, unique = true, length = 50)
    @NotBlank(message = "Numer inwentarzowy jest wymagany")
    private String inventoryNumber;

    @Column(name = "name", nullable = false, length = 200)
    @NotBlank(message = "Nazwa urządzenia jest wymagana")
    private String name;

    /**
     * Dział do którego należy urządzenie (WYMAGANE)
     * NOWE POLE!
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id", nullable = false)
    @NotNull(message = "Dział jest wymagany")
    private Department department;
    
    /**
     * Pracownia do której należy urządzenie (OPCJONALNE)
     * Niektóre działy nie mają pracowni
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "laboratory_id", nullable = true)
    private Laboratory laboratory;

    @Enumerated(EnumType.STRING)
    @Column(name = "chamber_type", nullable = false, length = 30)
    @NotNull(message = "Typ komory jest wymagany")
    private ChamberType chamberType;

    /**
     * @deprecated Użyj materialType zamiast tego pola
     */
    @Deprecated
    @Enumerated(EnumType.STRING)
    @Column(name = "stored_material", length = 20)
    private StoredMaterial storedMaterial;
    
    /**
     * Typ materiału przechowywanego (nowy system z relacją)
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "material_type_id")
    private MaterialType materialType;
    
    @Column(name = "min_operating_temp")
    private Double minOperatingTemp;
    
    @Column(name = "max_operating_temp")
    private Double maxOperatingTemp;

    /**
     * Objętość urządzenia w metrach sześciennych (zgodnie z PDA TR-64)
     */
    @Column(name = "volume_m3")
    private Double volume;

    /**
     * Klasyfikacja kubatury według PDA TR-64 i WHO
     * Determinuje minimalne wymagania dotyczące liczby punktów pomiarowych
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "volume_category", length = 10)
    private VolumeCategory volumeCategory;

    // ValidationPlanNumber nie jest @Audited → wykluczamy to pole z rewizji
    @NotAudited
    @OneToMany(mappedBy = "coolingDevice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ValidationPlanNumber> validationPlanNumbers = new ArrayList<>();

    // Helper methods for bidirectional relationship
    public void addValidationPlanNumber(ValidationPlanNumber validationPlanNumber) {
        validationPlanNumbers.add(validationPlanNumber);
        validationPlanNumber.setCoolingDevice(this);
    }

    public void removeValidationPlanNumber(ValidationPlanNumber validationPlanNumber) {
        validationPlanNumbers.remove(validationPlanNumber);
        validationPlanNumber.setCoolingDevice(null);
    }
    
    /**
     * Zwraca sformatowaną minimalną temperaturę operacyjną
     */
    public String getFormattedMinOperatingTemp() {
        return minOperatingTemp != null ? String.format("%.1f°C", minOperatingTemp) : "–";
    }
    
    /**
     * Zwraca sformatowaną maksymalną temperaturę operacyjną
     */
    public String getFormattedMaxOperatingTemp() {
        return maxOperatingTemp != null ? String.format("%.1f°C", maxOperatingTemp) : "–";
    }
    
    /**
     * Zwraca nazwę materiału (kompatybilność wsteczna)
     * Preferuje materialType nad storedMaterial
     */
    public String getMaterialName() {
        if (materialType != null) {
            return materialType.getName();
        }
        if (storedMaterial != null) {
            return storedMaterial.getDisplayName();
        }
        return "–";
    }
    
    /**
     * Zwraca zakres temperatur przechowywania materiału
     */
    public String getMaterialTemperatureRange() {
        if (materialType != null) {
            return materialType.getTemperatureRange();
        }
        return "–";
    }

    /**
     * Zwraca sformatowaną objętość urządzenia
     */
    public String getFormattedVolume() {
        return volume != null ? String.format("%.2f m³", volume) : "–";
    }

    /**
     * Zwraca nazwę klasy kubatury
     */
    public String getVolumeCategoryDisplayName() {
        return volumeCategory != null ? volumeCategory.getDisplayName() : "–";
    }

    /**
     * Zwraca minimalne wymagane punkty pomiarowe dla danej klasy kubatury
     */
    public Integer getMinMeasurementPoints() {
        return volumeCategory != null ? volumeCategory.getMinMeasurementPoints() : null;
    }

    /**
     * Sprawdza czy dana liczba punktów pomiarowych jest wystarczająca dla tej klasy kubatury
     */
    public boolean isValidMeasurementPoints(int measurementPoints) {
        return volumeCategory == null || volumeCategory.isValidMeasurementPoints(measurementPoints);
    }

    /**
     * Automatycznie ustawia klasę kubatury na podstawie objętości
     */
    public void updateVolumeCategoryFromVolume() {
        if (volume != null) {
            this.volumeCategory = VolumeCategory.fromVolume(volume);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CoolingDevice that = (CoolingDevice) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CoolingDevice{" +
                "id=" + id +
                ", inventoryNumber='" + inventoryNumber + '\'' +
                ", name='" + name + '\'' +
                ", chamberType=" + chamberType +
                ", storedMaterial=" + storedMaterial +
                '}';
    }
}
