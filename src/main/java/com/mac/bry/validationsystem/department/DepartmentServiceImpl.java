package com.mac.bry.validationsystem.department;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import com.mac.bry.validationsystem.device.CoolingDeviceRepository;
import com.mac.bry.validationsystem.laboratory.LaboratoryRepository;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementacja serwisu do zarządzania działami
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final CompanyRepository companyRepository;
    private final CoolingDeviceRepository deviceRepository;
    private final ThermoRecorderRepository recorderRepository;
    private final LaboratoryRepository laboratoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        log.debug("Pobieranie wszystkich działów");
        return departmentRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Department> getAllowedDepartments(java.util.Set<Long> allowedDeptIds,
            java.util.Set<Long> allowedCompanyIds) {
        log.debug("Pobieranie dozwolonych działów dla DeptIds: {}, CompanyIds: {}", allowedDeptIds, allowedCompanyIds);

        if (allowedCompanyIds == null) { // Super Admin
            return departmentRepository.findAllByOrderByNameAsc();
        }

        java.util.Set<Department> result = new java.util.HashSet<>();

        // 1. Działy do których ma bezpośrednie uprawnienie
        if (allowedDeptIds != null && !allowedDeptIds.isEmpty()) {
            result.addAll(departmentRepository.findAllByIdInOrderByNameAsc(allowedDeptIds));
        }

        // 2. Działy należące do dozwolonych firm
        if (allowedCompanyIds != null && !allowedCompanyIds.isEmpty()) {
            result.addAll(departmentRepository.findAllByCompanyIdInOrderByNameAsc(allowedCompanyIds));
        }

        return result.stream()
                .sorted(java.util.Comparator.comparing(Department::getName))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Department> getDepartmentsByCompany(Long companyId) {
        log.debug("Pobieranie działów dla firmy ID: {}", companyId);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono firmy o ID: " + companyId));
        return departmentRepository.findByCompanyOrderByNameAsc(company);
    }

    @Override
    @Transactional(readOnly = true)
    public Department getDepartmentById(Long id) {
        log.debug("Pobieranie działu o ID: {}", id);
        return departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono działu o ID: " + id));
    }

    @Override
    @Transactional
    public Department createDepartment(DepartmentDto dto) {
        log.info("Tworzenie nowego działu: {}", dto.name());

        // Walidacja - sprawdź czy skrót nie jest zajęty
        if (departmentRepository.findByAbbreviation(dto.abbreviation()).isPresent()) {
            throw new IllegalArgumentException(
                    "Dział o skrócie '" + dto.abbreviation() + "' już istnieje!");
        }

        // Pobierz firmę
        Company company = companyRepository.findById(dto.companyId())
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono firmy o ID: " + dto.companyId()));

        // Utwórz dział
        Department department = new Department();
        department.setCompany(company);
        department.setName(dto.name());
        department.setAbbreviation(dto.abbreviation());
        department.setDescription(dto.description());
        department.setHasLaboratories(dto.hasLaboratories() != null ? dto.hasLaboratories() : false);

        Department saved = departmentRepository.save(department);
        log.info("Utworzono dział: {} (ID: {})", saved.getName(), saved.getId());

        return saved;
    }

    @Override
    @Transactional
    public Department updateDepartment(Long id, DepartmentDto dto) {
        log.info("Aktualizacja działu ID: {}", id);

        Department department = getDepartmentById(id);

        // Walidacja skrótu (jeśli się zmienił)
        if (!department.getAbbreviation().equals(dto.abbreviation())) {
            if (departmentRepository.findByAbbreviation(dto.abbreviation()).isPresent()) {
                throw new IllegalArgumentException(
                        "Dział o skrócie '" + dto.abbreviation() + "' już istnieje!");
            }
        }

        // Aktualizuj pola
        department.setName(dto.name());
        department.setAbbreviation(dto.abbreviation());
        department.setDescription(dto.description());
        department.setHasLaboratories(dto.hasLaboratories() != null ? dto.hasLaboratories() : false);

        Department updated = departmentRepository.save(department);
        log.info("Zaktualizowano dział: {}", updated.getName());

        return updated;
    }

    @Override
    @Transactional
    public void deleteDepartment(Long id) {
        log.info("Próba usunięcia działu ID: {}", id);

        Department department = getDepartmentById(id);

        // Walidacja - sprawdź czy nie ma przypisanych urządzeń
        long devicesCount = deviceRepository.findByDepartment(department).size();
        if (devicesCount > 0) {
            throw new IllegalStateException(
                    "Nie można usunąć działu '" + department.getName() + "' - przypisanych jest " +
                            devicesCount + " urządzeń!");
        }

        // Walidacja - sprawdź czy nie ma przypisanych rejestratorów
        long recordersCount = recorderRepository.findByDepartment(department).size();
        if (recordersCount > 0) {
            throw new IllegalStateException(
                    "Nie można usunąć działu '" + department.getName() + "' - przypisanych jest " +
                            recordersCount + " rejestratorów!");
        }

        // Walidacja - sprawdź czy nie ma pracowni
        long laboratoriesCount = laboratoryRepository.findByDepartmentOrderByFullNameAsc(department).size();
        if (laboratoriesCount > 0) {
            throw new IllegalStateException(
                    "Nie można usunąć działu '" + department.getName() + "' - ma " +
                            laboratoriesCount + " pracowni! Usuń najpierw pracownie.");
        }

        departmentRepository.delete(department);
        log.info("Usunięto dział: {}", department.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Department> getDepartmentsWithLaboratories() {
        log.debug("Pobieranie działów które mają pracownie");
        return departmentRepository.findByHasLaboratoriesTrue();
    }
}
