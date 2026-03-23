package com.mac.bry.validationsystem.device;

import com.mac.bry.validationsystem.laboratory.Laboratory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface CoolingDeviceService {

    List<CoolingDevice> findAll();

    /**
     * FIX #2: Zwraca urządzenia dostępne dla aktualnie zalogowanego użytkownika.
     * Super Admin widzi wszystkie. Inni tylko swoje.
     */
    Page<CoolingDevice> getAllAccessibleDevices(Pageable pageable);

    /**
     * Zwraca pełną listę wszystkich dostępnych urządzeń (bez paginacji).
     * Używane m.in. w dropdownach.
     */
    List<CoolingDevice> getAllAccessibleDevices();

    Optional<CoolingDevice> findById(Long id);

    Optional<CoolingDevice> findByInventoryNumber(String inventoryNumber);

    List<CoolingDevice> findByLaboratory(Laboratory laboratory);

    List<CoolingDevice> findByChamberType(ChamberType chamberType);

    CoolingDevice save(CoolingDevice coolingDevice);

    void deleteById(Long id);

    boolean existsByInventoryNumber(String inventoryNumber);

    CoolingDevice addValidationPlanNumber(Long deviceId, Integer year, Integer planNumber);

    void removeValidationPlanNumber(Long deviceId, Long planNumberId);

    DeviceValidationStatus getValidationStatus(Long deviceId);

    /**
     * Wyszukuje urządzenia dostępne dla aktualnie zalogowanego użytkownika.
     */
    List<CoolingDevice> searchAccessibleDevices(String query);
}
