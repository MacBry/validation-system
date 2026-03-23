package com.mac.bry.validationsystem.department;

import java.util.List;

/**
 * Serwis do zarządzania działami
 */
public interface DepartmentService {

    /**
     * Zwraca działy na podstawie dozwolonych ID działów i firm
     */
    List<Department> getAllowedDepartments(java.util.Set<Long> allowedDeptIds, java.util.Set<Long> allowedCompanyIds);

    /**
     * Pobiera wszystkie działy
     */
    List<Department> getAllDepartments();

    /**
     * Pobiera działy firmy
     */
    List<Department> getDepartmentsByCompany(Long companyId);

    /**
     * Pobiera dział według ID
     */
    Department getDepartmentById(Long id);

    /**
     * Tworzy nowy dział
     */
    Department createDepartment(DepartmentDto dto);

    /**
     * Aktualizuje dział
     */
    Department updateDepartment(Long id, DepartmentDto dto);

    /**
     * Usuwa dział
     * 
     * @throws IllegalStateException jeśli dział ma przypisane urządzenia lub
     *                               rejestratory
     */
    void deleteDepartment(Long id);

    /**
     * Pobiera działy które mają pracownie
     */
    List<Department> getDepartmentsWithLaboratories();
}
