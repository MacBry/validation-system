package com.mac.bry.validationsystem.validation;

import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.RecorderPosition;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Walidacja urządzenia chłodniczego oparta na seriach pomiarowych
 */
@Entity
@Table(name = "validations")
@Audited
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Validation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Urządzenie chłodnicze którego dotyczy walidacja
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cooling_device_id", nullable = false)
    private CoolingDevice coolingDevice;
    
    /**
     * Numer RPW (Roczny Plan Walidacji) w formacie "1/2026"
     */
    @Column(name = "validation_plan_number", length = 20)
    private String validationPlanNumber;
    
    /**
     * Data utworzenia walidacji
     */
    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;
    
    /**
     * Status walidacji
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ValidationStatus status;
    
    /**
     * Średnia temperatura w urządzeniu (wyliczona ze wszystkich serii)
     */
    @Column(name = "average_device_temperature")
    private Double averageDeviceTemperature;

    /**
     * Pozycja czujnika kontrolującego (monitorującego) urządzenie.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "control_sensor_position", length = 30)
    private RecorderPosition controlSensorPosition;

    /**
     * Stan załadowania urządzenia podczas walidacji (pełne/puste/częściowo)
     * Informacja istotna dla interpretacji wyników i zgodności z GMP/GDP
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "device_load_state", length = 20)
    private DeviceLoadState deviceLoadState;

    /**
     * Serie pomiarowe użyte w walidacji.
     * @NotAudited — MeasurementSeries nie jest @Audited, więc pole musi być wykluczone
     */
    @NotAudited
    @ManyToMany
    @JoinTable(
        name = "validation_measurement_series",
        joinColumns = @JoinColumn(name = "validation_id"),
        inverseJoinColumns = @JoinColumn(name = "measurement_series_id")
    )
    private List<MeasurementSeries> measurementSeries = new ArrayList<>();
    
    /**
     * Dodaje serię pomiarową do walidacji
     */
    public void addMeasurementSeries(MeasurementSeries series) {
        this.measurementSeries.add(series);
    }
    
    /**
     * Wylicza średnią temperaturę urządzenia ze wszystkich serii pomiarowych
     */
    public void calculateAverageDeviceTemperature() {
        if (measurementSeries == null || measurementSeries.isEmpty()) {
            this.averageDeviceTemperature = null;
            return;
        }

        // POPRAWKA: Wykluczamy serie referencyjne (bez pozycji w urządzeniu)
        // Serie referencyjne mają recorder_position = null i inne temperatury (np. 20°C)
        double sum = measurementSeries.stream()
            .filter(s -> s.getAvgTemperature() != null)
            .filter(s -> s.getRecorderPosition() != null)  // Wykluczamy serie REF
            .mapToDouble(MeasurementSeries::getAvgTemperature)
            .sum();

        long count = measurementSeries.stream()
            .filter(s -> s.getAvgTemperature() != null)
            .filter(s -> s.getRecorderPosition() != null)  // Wykluczamy serie REF
            .count();

        this.averageDeviceTemperature = count > 0 ? sum / count : null;
    }

    /**
     * Zwraca sformatowaną nazwę stanu załadowania urządzenia
     */
    public String getDeviceLoadStateDisplayName() {
        return deviceLoadState != null ? deviceLoadState.getDisplayName() : "–";
    }

    /**
     * Zwraca ikonę reprezentującą stan załadowania urządzenia
     */
    public String getDeviceLoadStateIcon() {
        return deviceLoadState != null ? deviceLoadState.getStateIcon() : "❓";
    }

    /**
     * Zwraca pełny opis stanu załadowania urządzenia
     */
    public String getDeviceLoadStateDescription() {
        return deviceLoadState != null ? deviceLoadState.getFullDescription() : "Nieokreślono";
    }
}
