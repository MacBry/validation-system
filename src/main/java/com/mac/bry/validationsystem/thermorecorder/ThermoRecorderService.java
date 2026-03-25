package com.mac.bry.validationsystem.thermorecorder;

import java.util.List;
import java.util.Optional;

public interface ThermoRecorderService {

    List<ThermoRecorder> findAll();

    org.springframework.data.domain.Page<ThermoRecorder> getAllAccessibleRecorders(
            org.springframework.data.domain.Pageable pageable);

    /**
     * Zwraca rejestratory dostępne dla użytkownika z opcjonalnym filtrowaniem.
     */
    org.springframework.data.domain.Page<ThermoRecorder> getAllAccessibleRecorders(
            org.springframework.data.domain.Pageable pageable, Long companyId, Long departmentId, Long laboratoryId);

    List<ThermoRecorder> getAllAccessibleRecorders();

    Optional<ThermoRecorder> findById(Long id);

    Optional<ThermoRecorder> findByIdWithCalibrations(Long id);

    Optional<ThermoRecorder> findBySerialNumber(String serialNumber);

    List<ThermoRecorder> findByStatus(RecorderStatus status);

    ThermoRecorder save(ThermoRecorder thermoRecorder);

    void deleteById(Long id);

    boolean existsBySerialNumber(String serialNumber);

    /**
     * Aktualizuje status rejestratora na podstawie ważności ostatniego wzorcowania
     */
    void updateRecorderStatus(Long recorderId);

    /**
     * Aktualizuje statusy wszystkich rejestratorów
     */
    void updateAllRecorderStatuses();

    /**
     * Wyszukuje rejestratory dostępne dla aktualnie zalogowanego użytkownika.
     */
    List<ThermoRecorder> searchAccessibleRecorders(String query);
}
