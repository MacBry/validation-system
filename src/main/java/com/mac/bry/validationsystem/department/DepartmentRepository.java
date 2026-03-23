package com.mac.bry.validationsystem.department;

import com.mac.bry.validationsystem.company.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository dla działów
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /**
     * Znajduje wszystkie działy firmy posortowane po nazwie
     */
    List<Department> findByCompanyOrderByNameAsc(Company company);

    /**
     * Znajduje wszystkie działy które mają pracownie
     */
    List<Department> findByHasLaboratoriesTrue();

    /**
     * Znajduje dział po skrócie
     */
    Optional<Department> findByAbbreviation(String abbreviation);

    /**
     * Znajduje wszystkie działy o podanych ID posortowane po nazwie
     */
    List<Department> findAllByIdInOrderByNameAsc(java.util.Collection<Long> ids);

    /**
     * Znajduje wszystkie działy posortowane po nazwie
     */
    List<Department> findAllByOrderByNameAsc();

    /**
     * Znajduje wszystkie działy należące do podanych firm posortowane po nazwie
     */
    List<Department> findAllByCompanyIdInOrderByNameAsc(java.util.Collection<Long> companyIds);
}
