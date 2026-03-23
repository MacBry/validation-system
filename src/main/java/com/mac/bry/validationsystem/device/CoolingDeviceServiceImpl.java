package com.mac.bry.validationsystem.device;

import com.mac.bry.validationsystem.laboratory.Laboratory;
import com.mac.bry.validationsystem.measurement.MeasurementSeriesRepository;
import com.mac.bry.validationsystem.security.service.SecurityService;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationRepository;
import com.mac.bry.validationsystem.validation.ValidationStatus;
import com.mac.bry.validationsystem.validationplan.ValidationPlanNumber;
import com.mac.bry.validationsystem.validationplan.ValidationPlanNumberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CoolingDeviceServiceImpl implements CoolingDeviceService {

    private final CoolingDeviceRepository coolingDeviceRepository;
    private final ValidationPlanNumberRepository validationPlanNumberRepository;
    private final ValidationRepository validationRepository;
    private final MeasurementSeriesRepository measurementSeriesRepository;
    private final SecurityService securityService;

    @Override
    public List<CoolingDevice> findAll() {
        log.debug("Pobieranie wszystkich urządzeń chłodniczych");
        return coolingDeviceRepository.findAll();
    }

    /**
     * FIX #2: Zwraca urządzenia dostępne dla aktualnie zalogowanego użytkownika.
     * Super Admin widzi wszystkie, inni widzą tylko urządzenia ze swoich
     * firm/działów/laboratoriów.
     */
    @Override
    public Page<CoolingDevice> getAllAccessibleDevices(Pageable pageable) {
        boolean isSuperAdmin = securityService.isSuperAdmin();
        var companyIds = securityService.getAllowedCompanyIds();
        var deptIds = securityService.getDepartmentIdsWithImplicitAccess();
        var labIds = securityService.getAllowedLaboratoryIds();

        // Dodaj domyślne sortowanie jeśli brak
        if (pageable.getSort().isUnsorted()) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by("inventoryNumber").ascending());
        }

        // Zabezpieczenie pustymi kolekcjami gdy brak uprawnień
        java.util.Collection<Long> safeCompanyIds = companyIds != null ? companyIds
                : java.util.Collections.<Long>emptySet();
        java.util.Collection<Long> safeDeptIds = deptIds != null ? deptIds : java.util.Collections.<Long>emptySet();
        java.util.Collection<Long> safeLabIds = labIds != null ? labIds : java.util.Collections.<Long>emptySet();

        log.debug(
                "Pobieranie urządzeń dostępnych dla użytkownika (paginacja): page={}, size={}, companyIds={}, deptIds={}, labIds={}",
                pageable.getPageNumber(), pageable.getPageSize(), safeCompanyIds, safeDeptIds, safeLabIds);

        return coolingDeviceRepository.findAllAccessible(isSuperAdmin, safeCompanyIds, safeDeptIds, safeLabIds,
                pageable);
    }

    @Override
    public List<CoolingDevice> getAllAccessibleDevices() {
        boolean isSuperAdmin = securityService.isSuperAdmin();
        if (isSuperAdmin) {
            return coolingDeviceRepository.findAll(Sort.by("inventoryNumber").ascending());
        }
        var companyIds = securityService.getAllowedCompanyIds();
        var deptIds = securityService.getDepartmentIdsWithImplicitAccess();
        var labIds = securityService.getAllowedLaboratoryIds();
        java.util.Collection<Long> safeCompanyIds = companyIds != null ? companyIds : java.util.Collections.emptySet();
        java.util.Collection<Long> safeDeptIds = deptIds != null ? deptIds : java.util.Collections.emptySet();
        java.util.Collection<Long> safeLabIds = labIds != null ? labIds : java.util.Collections.emptySet();

        return coolingDeviceRepository.findAllAccessible(isSuperAdmin, safeCompanyIds, safeDeptIds, safeLabIds,
                PageRequest.of(0, Integer.MAX_VALUE, Sort.by("inventoryNumber").ascending())).getContent();
    }

    @Override
    public Optional<CoolingDevice> findById(Long id) {
        log.debug("Pobieranie urządzenia chłodniczego o id: {}", id);
        return coolingDeviceRepository.findById(id);
    }

    @Override
    public Optional<CoolingDevice> findByInventoryNumber(String inventoryNumber) {
        log.debug("Pobieranie urządzenia chłodniczego o numerze inwentarzowym: {}", inventoryNumber);
        return coolingDeviceRepository.findByInventoryNumber(inventoryNumber);
    }

    @Override
    public List<CoolingDevice> findByLaboratory(Laboratory laboratory) {
        log.debug("Pobieranie urządzeń chłodniczych dla pracowni: {}", laboratory.getAbbreviation());
        return coolingDeviceRepository.findByLaboratory(laboratory);
    }

    @Override
    public List<CoolingDevice> findByChamberType(ChamberType chamberType) {
        log.debug("Pobieranie urządzeń chłodniczych typu: {}", chamberType);
        return coolingDeviceRepository.findByChamberType(chamberType);
    }

    @Override
    @Transactional
    public CoolingDevice save(CoolingDevice coolingDevice) {
        log.debug("Zapisywanie urządzenia chłodniczego: {}", coolingDevice);
        return coolingDeviceRepository.save(coolingDevice);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        log.debug("Usuwanie urządzenia chłodniczego o id: {}", id);
        coolingDeviceRepository.deleteById(id);
    }

    @Override
    public boolean existsByInventoryNumber(String inventoryNumber) {
        return coolingDeviceRepository.existsByInventoryNumber(inventoryNumber);
    }

    @Override
    @Transactional
    public CoolingDevice addValidationPlanNumber(Long deviceId, Integer year, Integer planNumber) {
        log.debug("Dodawanie numeru RPW {} dla roku {} do urządzenia o id: {}",
                planNumber, year, deviceId);

        CoolingDevice device = coolingDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono urządzenia o id: " + deviceId));

        if (validationPlanNumberRepository.existsByCoolingDeviceAndYear(device, year)) {
            throw new IllegalArgumentException("Numer RPW dla roku " + year + " już istnieje dla tego urządzenia");
        }

        ValidationPlanNumber vpn = ValidationPlanNumber.builder()
                .year(year)
                .planNumber(planNumber)
                .build();

        device.addValidationPlanNumber(vpn);
        return coolingDeviceRepository.save(device);
    }

    @Override
    @Transactional
    public void removeValidationPlanNumber(Long deviceId, Long planNumberId) {
        log.debug("Usuwanie numeru RPW o id: {} z urządzenia o id: {}", planNumberId, deviceId);

        CoolingDevice device = coolingDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono urządzenia o id: " + deviceId));

        ValidationPlanNumber vpn = validationPlanNumberRepository.findById(planNumberId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono numeru RPW o id: " + planNumberId));

        device.removeValidationPlanNumber(vpn);
        coolingDeviceRepository.save(device);
    }

    /**
     * Sprawdza status walidacji dla urządzenia
     */
    @Override
    public DeviceValidationStatus getValidationStatus(Long deviceId) {
        log.debug("Sprawdzanie statusu walidacji dla urządzenia: {}", deviceId);

        DeviceValidationStatus status = new DeviceValidationStatus();
        status.setDeviceId(deviceId);

        // Znajdź najnowszą walidację
        Validation latestValidation = validationRepository.findLatestByDeviceId(deviceId);

        if (latestValidation == null) {
            status.setHasValidValidation(false);
            status.setInvalidReason("NO_VALIDATION");
            return status;
        }

        status.setLatestValidationDate(latestValidation.getCreatedDate());

        // Znajdź datę ostatnich pomiarów w seriach dla tego urządzenia
        LocalDateTime latestMeasurement = measurementSeriesRepository
                .findTopByCoolingDeviceIdOrderByLastMeasurementTimeDesc(deviceId)
                .map(series -> series.getLastMeasurementTime())
                .orElse(null);

        status.setLatestMeasurementDate(latestMeasurement);

        if (latestMeasurement == null) {
            status.setHasValidValidation(false);
            status.setInvalidReason("NO_VALIDATION");
            return status;
        }

        // Oblicz różnicę w dniach między ostatnimi pomiarami a walidacją
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(latestValidation.getCreatedDate(), latestMeasurement);
        status.setDaysSinceLastValidation(daysBetween);

        // Walidacja jest ważna TYLKO jeśli:
        // 1. Status to APPROVED (zatwierdzona)
        // 2. NIE jest starsza niż rok (365 dni) od ostatnich pomiarów
        boolean isApproved = latestValidation.getStatus() == ValidationStatus.APPROVED;
        boolean isNotExpired = daysBetween <= 365;

        // Określ powód nieważności
        if (!isApproved) {
            // Sprawdź konkretny status
            if (latestValidation.getStatus() == ValidationStatus.DRAFT) {
                status.setInvalidReason("DRAFT");
            } else if (latestValidation.getStatus() == ValidationStatus.REJECTED) {
                status.setInvalidReason("REJECTED");
            } else if (latestValidation.getStatus() == ValidationStatus.COMPLETED) {
                status.setInvalidReason("COMPLETED");
            }
            status.setHasValidValidation(false);
        } else if (!isNotExpired) {
            status.setInvalidReason("EXPIRED");
            status.setHasValidValidation(false);
        } else {
            // Wszystko OK
            status.setHasValidValidation(true);
            status.setInvalidReason(null);
        }

        return status;
    }

    @Override
    public List<CoolingDevice> searchAccessibleDevices(String query) {
        boolean isSuperAdmin = securityService.isSuperAdmin();
        var companyIds = securityService.getAllowedCompanyIds();
        var deptIds = securityService.getDepartmentIdsWithImplicitAccess();
        var labIds = securityService.getAllowedLaboratoryIds();

        java.util.Collection<Long> safeCompanyIds = companyIds != null ? companyIds : java.util.Collections.emptySet();
        java.util.Collection<Long> safeDeptIds = deptIds != null ? deptIds : java.util.Collections.emptySet();
        java.util.Collection<Long> safeLabIds = labIds != null ? labIds : java.util.Collections.emptySet();

        return coolingDeviceRepository.searchAccessible(query, isSuperAdmin, safeCompanyIds, safeDeptIds, safeLabIds);
    }
}
