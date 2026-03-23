package com.mac.bry.validationsystem.calibration;

import com.mac.bry.validationsystem.thermorecorder.ThermoRecorder;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorderRepository;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CalibrationServiceImpl implements CalibrationService {

    private final CalibrationRepository calibrationRepository;
    private final ThermoRecorderRepository thermoRecorderRepository;
    private final ThermoRecorderService thermoRecorderService;

    @Value("${calibration.certificates.path:uploads/certificates}")
    private String certificatesPath;

    @Override
    public List<Calibration> findAll() {
        log.debug("Pobieranie wszystkich wzorcowań");
        return calibrationRepository.findAll();
    }

    @Override
    public Optional<Calibration> findById(Long id) {
        log.debug("Pobieranie wzorcowania o id: {}", id);
        return calibrationRepository.findById(id);
    }

    @Override
    public List<Calibration> findByThermoRecorder(ThermoRecorder thermoRecorder) {
        log.debug("Pobieranie wzorcowań dla rejestratora: {}", thermoRecorder.getSerialNumber());
        return calibrationRepository.findByThermoRecorderOrderByCalibrationDateDesc(thermoRecorder);
    }

    @Override
    public Optional<Calibration> findLatestByThermoRecorder(ThermoRecorder thermoRecorder) {
        log.debug("Pobieranie najnowszego wzorcowania dla rejestratora: {}", thermoRecorder.getSerialNumber());
        return calibrationRepository.findFirstByThermoRecorderOrderByCalibrationDateDesc(thermoRecorder);
    }

    @Override
    @Transactional
    public Calibration save(Calibration calibration) {
        log.debug("Zapisywanie wzorcowania: {}", calibration);
        return calibrationRepository.save(calibration);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        log.debug("Usuwanie wzorcowania o id: {}", id);

        // Pobierz wzorcowanie aby znaleźć plik certyfikatu
        Optional<Calibration> calibration = calibrationRepository.findById(id);
        if (calibration.isPresent() && calibration.get().getCertificateFilePath() != null) {
            deleteCertificateFile(calibration.get().getCertificateFilePath());
        }

        calibrationRepository.deleteById(id);
    }

    @Override
    @Transactional
    public Calibration addCalibration(Long thermoRecorderId, LocalDate calibrationDate,
            String certificateNumber, MultipartFile certificateFile,
            List<CalibrationPointDto> points) throws IOException {
        log.debug("Dodawanie wzorcowania dla rejestratora o id: {}", thermoRecorderId);

        ThermoRecorder recorder = thermoRecorderRepository.findById(thermoRecorderId)
                .orElseThrow(
                        () -> new IllegalArgumentException("Nie znaleziono rejestratora o id: " + thermoRecorderId));

        Calibration calibration = Calibration.builder()
                .calibrationDate(calibrationDate)
                .certificateNumber(certificateNumber)
                .validUntil(calibrationDate.plusYears(1))
                .build();

        // Zapisz plik certyfikatu jeśli został przesłany
        if (certificateFile != null && !certificateFile.isEmpty()) {
            String filePath = saveCertificateFile(certificateFile, recorder.getSerialNumber());
            calibration.setCertificateFilePath(filePath);
        }

        recorder.addCalibration(calibration);

        // Dodaj punkty wzorcowania
        if (points != null && !points.isEmpty()) {
            for (CalibrationPointDto pDto : points) {
                CalibrationPoint point = CalibrationPoint.builder()
                        .temperatureValue(pDto.getTemperatureValue())
                        .systematicError(pDto.getSystematicError())
                        .uncertainty(pDto.getUncertainty())
                        .build();
                calibration.addPoint(point);
            }
        }

        Calibration savedCalibration = calibrationRepository.save(calibration);

        // Aktualizuj status rejestratora
        thermoRecorderService.updateRecorderStatus(thermoRecorderId);

        return savedCalibration;
    }

    @Override
    @Transactional
    public void removeCalibration(Long thermoRecorderId, Long calibrationId) {
        log.debug("Usuwanie wzorcowania o id: {} z rejestratora o id: {}", calibrationId, thermoRecorderId);

        ThermoRecorder recorder = thermoRecorderRepository.findById(thermoRecorderId)
                .orElseThrow(
                        () -> new IllegalArgumentException("Nie znaleziono rejestratora o id: " + thermoRecorderId));

        Calibration calibration = calibrationRepository.findById(calibrationId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono wzorcowania o id: " + calibrationId));

        // Usuń plik certyfikatu
        if (calibration.getCertificateFilePath() != null) {
            deleteCertificateFile(calibration.getCertificateFilePath());
        }

        recorder.removeCalibration(calibration);
        calibrationRepository.delete(calibration);

        // Aktualizuj status rejestratora
        thermoRecorderService.updateRecorderStatus(thermoRecorderId);
    }

    @Override
    @Transactional
    public Calibration updateCalibration(Long calibrationId, LocalDate calibrationDate,
            String certificateNumber, MultipartFile certificateFile,
            List<CalibrationPointDto> points) throws IOException {
        log.debug("Aktualizacja wzorcowania o id: {}", calibrationId);

        Calibration calibration = calibrationRepository.findById(calibrationId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono wzorcowania o id: " + calibrationId));

        calibration.setCalibrationDate(calibrationDate);
        calibration.setCertificateNumber(certificateNumber);
        calibration.setValidUntil(calibrationDate.plusYears(1));

        // Jeśli przesłano nowy plik, zamień stary
        if (certificateFile != null && !certificateFile.isEmpty()) {
            // Usuń stary plik
            if (calibration.getCertificateFilePath() != null) {
                deleteCertificateFile(calibration.getCertificateFilePath());
            }
            // Zapisz nowy
            String filePath = saveCertificateFile(certificateFile,
                    calibration.getThermoRecorder().getSerialNumber());
            calibration.setCertificateFilePath(filePath);
        }

        // Aktualizacja punktów wzorcowania
        calibration.getPoints().clear();
        if (points != null && !points.isEmpty()) {
            for (CalibrationPointDto pDto : points) {
                CalibrationPoint point = CalibrationPoint.builder()
                        .temperatureValue(pDto.getTemperatureValue())
                        .systematicError(pDto.getSystematicError())
                        .uncertainty(pDto.getUncertainty())
                        .build();
                calibration.addPoint(point);
            }
        }

        Calibration updated = calibrationRepository.save(calibration);

        // Aktualizuj status rejestratora
        thermoRecorderService.updateRecorderStatus(calibration.getThermoRecorder().getId());

        return updated;
    }

    @Override
    public List<Calibration> findExpiredCalibrations() {
        log.debug("Pobieranie wygasłych wzorcowań");
        return calibrationRepository.findExpiredCalibrations(LocalDate.now());
    }

    private String saveCertificateFile(MultipartFile file, String serialNumber) throws IOException {
        // Utwórz katalog jeśli nie istnieje
        Path uploadPath = Paths.get(certificatesPath);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Wygeneruj unikalną nazwę pliku
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".pdf";
        String filename = serialNumber + "_" + UUID.randomUUID() + extension;

        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        log.info("Zapisano plik certyfikatu: {}", filePath);
        return filePath.toString();
    }

    private void deleteCertificateFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("Usunięto plik certyfikatu: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Błąd podczas usuwania pliku certyfikatu: {}", filePath, e);
        }
    }
}
