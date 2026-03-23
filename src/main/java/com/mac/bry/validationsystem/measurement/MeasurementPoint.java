package com.mac.bry.validationsystem.measurement;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Reprezentuje pojedynczy punkt pomiarowy (timestamp + temperatura)
 */
@Entity
@Table(name = "measurement_points", indexes = {
    @Index(name = "idx_series_time", columnList = "series_id, measurement_time")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementPoint {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Powiązanie z serią pomiarową
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    @JsonIgnore
    private MeasurementSeries series;
    
    /**
     * Data i godzina pomiaru
     */
    @Column(name = "measurement_time", nullable = false)
    private LocalDateTime measurementTime;
    
    /**
     * Wartość temperatury [°C]
     */
    @Column(name = "temperature", nullable = false)
    private Double temperature;
    
    /**
     * Konstruktor pomocniczy
     */
    public MeasurementPoint(LocalDateTime measurementTime, Double temperature) {
        this.measurementTime = measurementTime;
        this.temperature = temperature;
    }
}
