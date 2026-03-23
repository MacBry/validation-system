package com.mac.bry.validationsystem.calibration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalibrationPointRepository extends JpaRepository<CalibrationPoint, Long> {

    /**
     * Znajduje wszystkie punkty wzorcowania dla konkretnego świadectwa.
     * Sortuje po zadanej temperaturze rosnąco.
     */
    List<CalibrationPoint> findByCalibrationIdOrderByTemperatureValueAsc(Long calibrationId);
}
