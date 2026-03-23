package com.mac.bry.validationsystem.laboratory;

import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.department.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LaboratoryServiceImpl implements LaboratoryService {

    private final LaboratoryRepository laboratoryRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    public List<Laboratory> findAll() {
        log.debug("Pobieranie wszystkich pracowni");
        return laboratoryRepository.findAll();
    }

    @Override
    public List<Laboratory> getAllowedLaboratories(java.util.Set<Long> allowedLabIds,
            java.util.Set<Long> allowedDeptIds, java.util.Set<Long> allowedCompanyIds) {
        log.debug("Pobieranie dozwolonych pracowni dla LabIds: {}, DeptIds: {}, CompanyIds: {}", allowedLabIds,
                allowedDeptIds, allowedCompanyIds);

        if (allowedCompanyIds == null) { // Super Admin
            return laboratoryRepository.findAll();
        }

        java.util.Set<Laboratory> result = new java.util.HashSet<>();

        // 1. Pracownie do których ma bezpośrednie uprawnienie
        if (allowedLabIds != null && !allowedLabIds.isEmpty()) {
            result.addAll(laboratoryRepository.findAllByIdInOrderByFullNameAsc(allowedLabIds));
        }

        // 2. Pracownie należące do dozwolonych działów
        if (allowedDeptIds != null && !allowedDeptIds.isEmpty()) {
            result.addAll(laboratoryRepository.findAllByDepartmentIdInOrderByFullNameAsc(allowedDeptIds));
        }

        // 3. Pracownie należące do dozwolonych firm
        if (allowedCompanyIds != null && !allowedCompanyIds.isEmpty()) {
            result.addAll(laboratoryRepository.findAllByDepartmentCompanyIdInOrderByFullNameAsc(allowedCompanyIds));
        }

        return result.stream()
                .sorted(java.util.Comparator.comparing(Laboratory::getFullName))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public Optional<Laboratory> findById(Long id) {
        log.debug("Pobieranie pracowni o id: {}", id);
        return laboratoryRepository.findById(id);
    }

    @Override
    public Optional<Laboratory> findByAbbreviation(String abbreviation) {
        log.debug("Pobieranie pracowni o skrócie: {}", abbreviation);
        return laboratoryRepository.findByAbbreviation(abbreviation);
    }

    @Override
    @Transactional
    public Laboratory save(Laboratory laboratory) {
        log.debug("Zapisywanie pracowni: {}", laboratory);
        return laboratoryRepository.save(laboratory);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        log.debug("Usuwanie pracowni o id: {}", id);
        laboratoryRepository.deleteById(id);
    }

    @Override
    public boolean existsByFullName(String fullName) {
        return laboratoryRepository.existsByFullName(fullName);
    }

    @Override
    public boolean existsByAbbreviation(String abbreviation) {
        return laboratoryRepository.existsByAbbreviation(abbreviation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Laboratory> getLaboratoriesByDepartment(Long departmentId) {
        log.debug("Pobieranie pracowni dla działu ID: {}", departmentId);

        // Pobierz dział
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono działu o ID: " + departmentId));

        return laboratoryRepository.findByDepartmentOrderByFullNameAsc(department);
    }
}
