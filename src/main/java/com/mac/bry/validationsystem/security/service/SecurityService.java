package com.mac.bry.validationsystem.security.service;

import com.mac.bry.validationsystem.device.CoolingDeviceRepository;
import com.mac.bry.validationsystem.validation.ValidationRepository;
import com.mac.bry.validationsystem.department.DepartmentRepository;
import com.mac.bry.validationsystem.laboratory.LaboratoryRepository;
import com.mac.bry.validationsystem.measurement.MeasurementSeriesRepository;
import com.mac.bry.validationsystem.security.repository.UserPermissionRepository;
import com.mac.bry.validationsystem.security.Role;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.UserPermissionsCache;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * FIX #1 + #2: Zoptymalizowane sprawdzanie uprawnień z użyciem JSON Cache
 * Przystosowane do użycia z @PreAuthorize w warstwie kontrolerów (Day 10).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityService {

    private final CoolingDeviceRepository deviceRepository;
    private final ValidationRepository validationRepository;
    private final MeasurementSeriesRepository seriesRepository;
    private final DepartmentRepository departmentRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final ThermoRecorderRepository thermoRecorderRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        }
        return null;
    }

    public boolean isSuperAdmin(User user) {
        if (user == null || user.getRoles() == null)
            return false;
        for (Role role : user.getRoles()) {
            if ("ROLE_SUPER_ADMIN".equals(role.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sprawdza czy aktualnie zalogowany użytkownik jest Super Adminem.
     */
    public boolean isSuperAdmin() {
        return isSuperAdmin(getCurrentUser());
    }

    public boolean isCompanyAdmin(User user) {
        if (user == null || user.getRoles() == null)
            return false;
        for (Role role : user.getRoles()) {
            if ("ROLE_COMPANY_ADMIN".equals(role.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean isCompanyAdmin() {
        return isCompanyAdmin(getCurrentUser());
    }

    /**
     * Zwraca ID wszystkich firm w których użytkownik ma jakiekolwiek uprawnienie
     * (FULL_COMPANY, FULL_DEPARTMENT lub SPECIFIC_LABORATORY).
     * Używane do wyświetlania kontekstu firmy w nawigacji.
     * Null = Super Admin (ma dostęp do wszystkich).
     */
    public Set<Long> getAllCompanyIdsWithAnyAccess() {
        User user = getCurrentUser();
        if (user == null) return Collections.emptySet();
        if (isSuperAdmin(user)) return null;

        UserPermissionsCache cache = user.getPermissionsCache();
        if (cache == null || cache.getCompanyPermissionLevels() == null) return Collections.emptySet();
        return cache.getCompanyPermissionLevels().keySet();
    }

    /**
     * Zwraca ID firm do których ma dostęp aktualny użytkownik.
     * Null = Super Admin (ma dostęp do wszystkich).
     */
    public Set<Long> getAllowedCompanyIds() {
        User user = getCurrentUser();
        if (user == null)
            return Collections.emptySet();

        if (isSuperAdmin(user)) {
            return null; // null = brak filtrowania = wszystkie firmy
        }

        UserPermissionsCache cache = user.getPermissionsCache();
        return cache != null ? cache.getAllowedCompanyIds() : Collections.emptySet();
    }

    /**
     * Zwraca ID działów do których ma dostęp aktualny użytkownik.
     * Null = Super Admin (ma dostęp do wszystkich).
     */
    public Set<Long> getAllowedDepartmentIds() {
        User user = getCurrentUser();
        if (user == null)
            return Collections.emptySet();
        if (isSuperAdmin(user))
            return null;

        UserPermissionsCache cache = user.getPermissionsCache();
        return cache != null ? cache.getAllowedDepartmentIds() : Collections.emptySet();
    }

    /**
     * Zwraca ID działów do których użytkownik ma dostęp bezpośredni LUB pośredni
     * (przez pracownię).
     * Używane m.in. do filtrowania rejestratorów.
     */
    public Set<Long> getDepartmentIdsWithImplicitAccess() {
        User user = getCurrentUser();
        if (user == null)
            return Collections.emptySet();
        if (isSuperAdmin(user))
            return null;

        UserPermissionsCache cache = user.getPermissionsCache();
        if (cache == null)
            return Collections.emptySet();

        Set<Long> deptIds = new HashSet<>(cache.getAllowedDepartmentIds());

        // Dodaj działy wynikające z przypisanych laboratoriów
        Set<Long> labIds = cache.getAllowedLaboratoryIds();
        if (labIds != null && !labIds.isEmpty()) {
            laboratoryRepository.findAllById(labIds).forEach(lab -> {
                if (lab.getDepartment() != null) {
                    deptIds.add(lab.getDepartment().getId());
                }
            });
        }

        return deptIds;
    }

    /**
     * Zwraca ID laboratoriów do których ma dostęp aktualny użytkownik.
     * Null = Super Admin (ma dostęp do wszystkich).
     */
    public Set<Long> getAllowedLaboratoryIds() {
        User user = getCurrentUser();
        if (user == null)
            return Collections.emptySet();
        if (isSuperAdmin(user))
            return null;

        UserPermissionsCache cache = user.getPermissionsCache();
        return cache != null ? cache.getAllowedLaboratoryIds() : Collections.emptySet();
    }

    public boolean hasAccessToCompany(Long companyId) {
        User user = getCurrentUser();
        if (user == null)
            return false;
        if (isSuperAdmin(user))
            return true;

        UserPermissionsCache cache = user.getPermissionsCache();
        return cache != null && cache.hasAccessToCompany(companyId);
    }

    /**
     * Sprawdza czy admin ma dostęp do zarządzania konkretnym użytkownikiem.
     */
    public boolean hasAccessToUser(Long targetUserId) {
        if (targetUserId == null)
            return false;
        if (isSuperAdmin())
            return true;

        Set<Long> allowedCompanyIds = getAllowedCompanyIds();
        if (allowedCompanyIds == null || allowedCompanyIds.isEmpty()) {
            return false;
        }

        // Użytkownik jest "widoczny" dla admina, jeśli ma jakiekolwiek uprawnienie w
        // firmie admina
        return userPermissionRepository.existsByUserIdAndCompanyIds(targetUserId, allowedCompanyIds);
    }

    public boolean hasAccessToDepartment(Long departmentId) {
        User user = getCurrentUser();
        if (user == null)
            return false;
        if (isSuperAdmin(user))
            return true;

        UserPermissionsCache cache = user.getPermissionsCache();
        if (cache == null)
            return false;

        // 1. Bezpośrednie uprawnienie do działu
        if (cache.hasAccessToDepartment(departmentId))
            return true;

        // 2. Sprawdź czy ma uprawnienie do całej firmy (Parent check)
        return departmentRepository.findById(departmentId)
                .map(dept -> cache.hasAccessToCompany(dept.getCompany().getId()))
                .orElse(false);
    }

    public boolean hasAccessToLaboratory(Long laboratoryId) {
        User user = getCurrentUser();
        if (user == null)
            return false;
        if (isSuperAdmin(user))
            return true;

        UserPermissionsCache cache = user.getPermissionsCache();
        if (cache == null)
            return false;

        // 1. Bezpośrednie uprawnienie do pracowni
        if (cache.hasAccessToLaboratory(laboratoryId))
            return true;

        // 2. Sprawdź hierarchię (Dział -> Firma)
        return laboratoryRepository.findById(laboratoryId)
                .map(lab -> {
                    if (lab.getDepartment() == null)
                        return false;
                    // Uprawnienie do działu
                    if (cache.hasAccessToDepartment(lab.getDepartment().getId()))
                        return true;
                    // Uprawnienie do firmy
                    if (lab.getDepartment().getCompany() == null)
                        return false;
                    return cache.hasAccessToCompany(lab.getDepartment().getCompany().getId());
                })
                .orElse(false);
    }

    /**
     * Sprawdza czy użytkownik ma uprawnienie do "zobaczenia/użycia" działu (np. w
     * listach wyboru),
     * co obejmuje posiadanie dostępu do Działu, Firmy nadrzędnej LUB dowolnej
     * pracowni w tym dziale.
     */
    public boolean hasAnyAccessToDepartment(Long departmentId) {
        if (hasAccessToDepartment(departmentId))
            return true;

        User user = getCurrentUser();
        if (user == null || user.getPermissionsCache() == null)
            return false;

        UserPermissionsCache cache = user.getPermissionsCache();
        // Sprawdź czy użytkownik ma dostęp do jakiejkolwiek pracowni w tym dziale
        return laboratoryRepository.findByDepartmentId(departmentId).stream()
                .anyMatch(lab -> cache.hasAccessToLaboratory(lab.getId()));
    }

    /**
     * Dzień 10: Metody pomocnicze dla @PreAuthorize działające na poziomie encji
     */
    @Transactional(readOnly = true)
    public boolean canManageDevice(Long deviceId) {
        if (deviceId == null)
            return false;
        if (isSuperAdmin())
            return true;

        return deviceRepository.findById(deviceId)
                .map(device -> {
                    // 1. Dostęp na poziomie Działu (lub Firmy)
                    if (device.getDepartment() != null
                            && hasAccessToDepartment(device.getDepartment().getId())) {
                        return true;
                    }
                    // 2. Dostęp na poziomie Pracowni
                    if (device.getLaboratory() != null) {
                        return hasAccessToLaboratory(device.getLaboratory().getId());
                    }
                    log.warn("Urządzenie ID={} nie ma przypisanego działu ani pracowni — odmowa dostępu", deviceId);
                    return false;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasAccessToThermoRecorder(Long recorderId) {
        if (recorderId == null)
            return false;
        if (isSuperAdmin())
            return true;

        return thermoRecorderRepository.findById(recorderId)
                .map(recorder -> {
                    // 1. Sprawdź dostęp do firmy
                    if (hasAccessToCompany(recorder.getDepartment().getCompany().getId())) {
                        return true;
                    }
                    // 2. Sprawdź czy dział rejestratora jest w zbiorze "dostępnych" (w tym przez
                    // laboratorium)
                    Set<Long> allowedDepts = getDepartmentIdsWithImplicitAccess();
                    return allowedDepts != null && allowedDepts.contains(recorder.getDepartment().getId());
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canManageValidation(Long validationId) {
        if (validationId == null)
            return false;
        if (isSuperAdmin())
            return true;

        return validationRepository.findById(validationId)
                // Walidacja nie ma bezpośredniego powiązania z pracownią, sprawdzamy przez
                // urządzenie z pierwszej serii
                .map(v -> {
                    if (v.getMeasurementSeries() == null || v.getMeasurementSeries().isEmpty()) {
                        return false; // Nie powinno się zdarzyć
                    }
                    // Jeśli ma dostęp do urządzenia, to ma dostęp do walidacji
                    return canManageDevice(v.getMeasurementSeries().iterator().next().getCoolingDevice().getId());
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canManageMeasurementSeries(Long seriesId) {
        if (seriesId == null)
            return false;
        if (isSuperAdmin())
            return true;

        return seriesRepository.findById(seriesId)
                .map(s -> canManageDevice(s.getCoolingDevice().getId()))
                .orElse(false);
    }

    /**
     * Sprawdza czy użytkownik ma dostęp do WSZYSTKICH podanych serii pomiarowych.
     */
    @Transactional(readOnly = true)
    public boolean canManageAllMeasurementSeries(java.util.List<Long> seriesIds) {
        if (seriesIds == null || seriesIds.isEmpty())
            return false;
        if (isSuperAdmin())
            return true;

        for (Long id : seriesIds) {
            if (!canManageMeasurementSeries(id)) {
                return false;
            }
        }
        return true;
    }
}
