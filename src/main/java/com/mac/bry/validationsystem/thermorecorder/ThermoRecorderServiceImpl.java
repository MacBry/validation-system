package com.mac.bry.validationsystem.thermorecorder;

import com.mac.bry.validationsystem.calibration.Calibration;
import com.mac.bry.validationsystem.calibration.CalibrationRepository;
import com.mac.bry.validationsystem.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ThermoRecorderServiceImpl implements ThermoRecorderService {

    private final ThermoRecorderRepository thermoRecorderRepository;
    private final CalibrationRepository calibrationRepository;
    private final SecurityService securityService;

    @Override
    public List<ThermoRecorder> findAll() {
        log.debug("Pobieranie wszystkich rejestratorów TESTO");
        return thermoRecorderRepository.findAll();
    }

    @Override
    public Page<ThermoRecorder> getAllAccessibleRecorders(Pageable pageable) {
        log.debug("Pobieranie dostępnych rejestratorów TESTO (paginacja): page={}, size={}",
                pageable.getPageNumber(), pageable.getPageSize());

        // Domyślne sortowanie po numerze seryjnym
        if (pageable.getSort().isUnsorted()) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by("serialNumber").ascending());
        }

        if (securityService.isSuperAdmin()) {
            return thermoRecorderRepository.findAll(pageable);
        }

        return thermoRecorderRepository.findAllAccessible(
                securityService.getAllowedCompanyIds(),
                securityService.getDepartmentIdsWithImplicitAccess(),
                pageable);
    }

    @Override
    public List<ThermoRecorder> getAllAccessibleRecorders() {
        log.debug("Pobieranie wszystkich dostępnych rejestratorów TESTO (bez paginacji)");
        if (securityService.isSuperAdmin()) {
            return thermoRecorderRepository.findAll(Sort.by("serialNumber").ascending());
        }

        return thermoRecorderRepository.findAllAccessible(
                securityService.getAllowedCompanyIds(),
                securityService.getDepartmentIdsWithImplicitAccess(),
                PageRequest.of(0, Integer.MAX_VALUE, Sort.by("serialNumber").ascending())).getContent();
    }

    @Override
    public Optional<ThermoRecorder> findById(Long id) {
        log.debug("Pobieranie rejestratora TESTO o id: {}", id);
        return thermoRecorderRepository.findById(id);
    }

    @Override
    public Optional<ThermoRecorder> findByIdWithCalibrations(Long id) {
        log.debug("Pobieranie rejestratora TESTO o id: {} wraz z wzorcowaniami", id);
        return thermoRecorderRepository.findByIdWithCalibrations(id);
    }

    @Override
    public Optional<ThermoRecorder> findBySerialNumber(String serialNumber) {
        log.debug("Pobieranie rejestratora TESTO o numerze seryjnym: {}", serialNumber);
        return thermoRecorderRepository.findBySerialNumber(serialNumber);
    }

    @Override
    public List<ThermoRecorder> findByStatus(RecorderStatus status) {
        log.debug("Pobieranie rejestratorów TESTO o statusie: {}", status);
        return thermoRecorderRepository.findByStatus(status);
    }

    @Override
    @Transactional
    public ThermoRecorder save(ThermoRecorder thermoRecorder) {
        log.debug("Zapisywanie rejestratora TESTO: {}", thermoRecorder);
        return thermoRecorderRepository.save(thermoRecorder);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        log.debug("Usuwanie rejestratora TESTO o id: {}", id);
        thermoRecorderRepository.deleteById(id);
    }

    @Override
    public boolean existsBySerialNumber(String serialNumber) {
        return thermoRecorderRepository.existsBySerialNumber(serialNumber);
    }

    @Override
    @Transactional
    public void updateRecorderStatus(Long recorderId) {
        log.debug("Aktualizacja statusu rejestratora o id: {}", recorderId);

        ThermoRecorder recorder = thermoRecorderRepository.findById(recorderId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono rejestratora o id: " + recorderId));

        // Jeśli rejestrator jest w trakcie wzorcowania, nie zmieniaj statusu automatycznie
        if (recorder.getStatus() == RecorderStatus.UNDER_CALIBRATION) {
            log.debug("Pomijanie automatycznej aktualizacji statusu dla rejestratora {} - status to Wysłano do wzorcowania",
                    recorder.getSerialNumber());
            return;
        }

        Optional<Calibration> latestCalibration = calibrationRepository
                .findFirstByThermoRecorderOrderByCalibrationDateDesc(recorder);

        RecorderStatus newStatus;
        if (latestCalibration.isPresent() && latestCalibration.get().isValid()) {
            newStatus = RecorderStatus.ACTIVE;
        } else {
            newStatus = RecorderStatus.INACTIVE;
        }

        if (recorder.getStatus() != newStatus) {
            recorder.setStatus(newStatus);
            thermoRecorderRepository.save(recorder);
            log.info("Zaktualizowano status rejestratora {} z {} na {}",
                    recorder.getSerialNumber(), recorder.getStatus(), newStatus);
        }
    }

    @Override
    @Transactional
    public void updateAllRecorderStatuses() {
        log.info("Aktualizacja statusów wszystkich rejestratorów");
        List<ThermoRecorder> recorders = thermoRecorderRepository.findAll();

        for (ThermoRecorder recorder : recorders) {
            updateRecorderStatus(recorder.getId());
        }

        log.info("Zaktualizowano statusy {} rejestratorów", recorders.size());
    }

    @Override
    public List<ThermoRecorder> searchAccessibleRecorders(String query) {
        boolean isSuperAdmin = securityService.isSuperAdmin();
        var companyIds = securityService.getAllowedCompanyIds();
        var deptIds = securityService.getDepartmentIdsWithImplicitAccess();

        return thermoRecorderRepository.searchAccessible(query, isSuperAdmin, companyIds, deptIds);
    }
}
