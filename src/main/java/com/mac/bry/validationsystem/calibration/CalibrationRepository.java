package com.mac.bry.validationsystem.calibration;

import com.mac.bry.validationsystem.thermorecorder.ThermoRecorder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CalibrationRepository extends JpaRepository<Calibration, Long> {
    
    List<Calibration> findByThermoRecorderOrderByCalibrationDateDesc(ThermoRecorder thermoRecorder);
    
    @Query("SELECT c FROM Calibration c WHERE c.thermoRecorder = :thermoRecorder " +
           "ORDER BY c.calibrationDate DESC")
    List<Calibration> findLatestByThermoRecorder(ThermoRecorder thermoRecorder);
    
    Optional<Calibration> findFirstByThermoRecorderOrderByCalibrationDateDesc(ThermoRecorder thermoRecorder);
    
    List<Calibration> findByValidUntilBefore(LocalDate date);
    
    @Query("SELECT c FROM Calibration c WHERE c.validUntil < :date")
    List<Calibration> findExpiredCalibrations(LocalDate date);

    /**
     * Pobiera najnowsze wzorcowania rejestratorów, które wygasają w podanym oknie czasowym.
     */
    @Query("SELECT c FROM Calibration c " +
           "WHERE c.thermoRecorder.status <> com.mac.bry.validationsystem.thermorecorder.RecorderStatus.UNDER_CALIBRATION " +
           "AND c.validUntil BETWEEN :from AND :to " +
           "AND c.calibrationDate = (" +
           "  SELECT MAX(c2.calibrationDate) FROM Calibration c2 WHERE c2.thermoRecorder = c.thermoRecorder" +
           ")")
    List<Calibration> findLatestExpiringCalibrations(
            @org.springframework.data.repository.query.Param("from") LocalDate from,
            @org.springframework.data.repository.query.Param("to") LocalDate to);
}
