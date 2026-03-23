package com.mac.bry.validationsystem.calibration;

import com.mac.bry.validationsystem.thermorecorder.ThermoRecorder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CalibrationService {

    List<Calibration> findAll();

    Optional<Calibration> findById(Long id);

    List<Calibration> findByThermoRecorder(ThermoRecorder thermoRecorder);

    Optional<Calibration> findLatestByThermoRecorder(ThermoRecorder thermoRecorder);

    Calibration save(Calibration calibration);

    void deleteById(Long id);

    /**
     * Dodaje nowe wzorcowanie do rejestratora wraz z punktami wzorcowania
     */
    Calibration addCalibration(Long thermoRecorderId, LocalDate calibrationDate,
            String certificateNumber, MultipartFile certificateFile,
            List<CalibrationPointDto> points) throws IOException;

    /**
     * Usuwa wzorcowanie z rejestratora
     */
    void removeCalibration(Long thermoRecorderId, Long calibrationId);

    /**
     * Aktualizuje wzorcowanie i jego punkty
     */
    Calibration updateCalibration(Long calibrationId, LocalDate calibrationDate,
            String certificateNumber, MultipartFile certificateFile,
            List<CalibrationPointDto> points) throws IOException;

    /**
     * Znajduje wygasłe wzorcowania
     */
    List<Calibration> findExpiredCalibrations();
}
