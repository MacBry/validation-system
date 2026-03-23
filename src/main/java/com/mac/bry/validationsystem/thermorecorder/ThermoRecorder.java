package com.mac.bry.validationsystem.thermorecorder;

import com.mac.bry.validationsystem.calibration.Calibration;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.laboratory.Laboratory;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "thermo_recorders")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThermoRecorder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_number", nullable = false, unique = true, length = 50)
    @NotBlank(message = "Numer seryjny jest wymagany")
    private String serialNumber;

    @Column(name = "model", nullable = false, length = 100)
    @NotBlank(message = "Model jest wymagany")
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @NotNull(message = "Status jest wymagany")
    private RecorderStatus status;

    /**
     * Rozdzielczość cyfrowa rejestratora w stopniach Celsjusza
     * Używana do obliczenia komponenty niepewności rozdzielczości w budżecie GUM
     */
    @Column(name = "resolution", precision = 4, scale = 3)
    private BigDecimal resolution;

    /**
     * Dział do którego przypisany jest rejestrator (WYMAGANE)
     * NOWE POLE!
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id", nullable = false)
    @NotNull(message = "Dział jest wymagany")
    private Department department;
    
    /**
     * Pracownia do której przypisany jest rejestrator (OPCJONALNE)
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "laboratory_id", nullable = true)
    private Laboratory laboratory;

    // Calibration jest @Audited — relacja odwrotna (mappedBy), Envers śledzi przez FK w calibrations_AUD
    @NotAudited
    @OneToMany(mappedBy = "thermoRecorder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("calibrationDate DESC")
    @Builder.Default
    private List<Calibration> calibrations = new ArrayList<>();

    // Helper methods for bidirectional relationship
    public void addCalibration(Calibration calibration) {
        calibrations.add(calibration);
        calibration.setThermoRecorder(this);
    }

    public void removeCalibration(Calibration calibration) {
        calibrations.remove(calibration);
        calibration.setThermoRecorder(null);
    }
    
    /**
     * Zwraca najnowszą kalibrację (lista jest już posortowana DESC)
     */
    public Calibration getLatestCalibration() {
        return calibrations.isEmpty() ? null : calibrations.get(0);
    }

    /**
     * Zwraca rozdzielczość rejestratora z domyślną wartością jeśli nie ustawiona
     */
    public BigDecimal getResolution() {
        if (resolution != null) {
            return resolution;
        }

        // Domyślne wartości na podstawie modelu
        String modelLower = model != null ? model.toLowerCase() : "";
        if (modelLower.contains("testo")) {
            return new BigDecimal("0.100"); // TESTO 175: 0.1°C
        } else if (modelLower.contains("precision") || modelLower.contains("pt100")) {
            return new BigDecimal("0.010"); // Precyzyjne: 0.01°C
        } else {
            return new BigDecimal("0.100"); // domyślna: 0.1°C
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThermoRecorder that = (ThermoRecorder) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ThermoRecorder{" +
                "id=" + id +
                ", serialNumber='" + serialNumber + '\'' +
                ", model='" + model + '\'' +
                ", status=" + status +
                '}';
    }
}
