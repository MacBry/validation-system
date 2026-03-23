package com.mac.bry.validationsystem.measurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository dla punktów pomiarowych
 */
@Repository
public interface MeasurementPointRepository extends JpaRepository<MeasurementPoint, Long> {
    
    /**
     * Znajduje wszystkie punkty dla danej serii
     */
    List<MeasurementPoint> findBySeriesIdOrderByMeasurementTimeAsc(Long seriesId);
    
    /**
     * Znajduje wszystkie punkty dla danej serii (przez obiekt)
     */
    List<MeasurementPoint> findBySeriesOrderByMeasurementTimeAsc(MeasurementSeries series);
    
    /**
     * Usuwa wszystkie punkty dla danej serii
     */
    void deleteBySeriesId(Long seriesId);
}
