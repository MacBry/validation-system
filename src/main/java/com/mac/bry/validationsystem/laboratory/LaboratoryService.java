package com.mac.bry.validationsystem.laboratory;

import java.util.List;
import java.util.Optional;

public interface LaboratoryService {

    List<Laboratory> findAll();

    /**
     * Zwraca pracownie na podstawie dozwolonych ID pracowni, działów i firm
     */
    List<Laboratory> getAllowedLaboratories(java.util.Set<Long> allowedLabIds, java.util.Set<Long> allowedDeptIds,
            java.util.Set<Long> allowedCompanyIds);

    Optional<Laboratory> findById(Long id);

    Optional<Laboratory> findByAbbreviation(String abbreviation);

    Laboratory save(Laboratory laboratory);

    void deleteById(Long id);

    boolean existsByFullName(String fullName);

    boolean existsByAbbreviation(String abbreviation);

    /**
     * Pobiera pracownie działu
     * NOWA METODA!
     */
    List<Laboratory> getLaboratoriesByDepartment(Long departmentId);
}
