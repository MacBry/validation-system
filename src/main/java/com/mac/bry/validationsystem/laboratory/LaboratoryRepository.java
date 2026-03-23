package com.mac.bry.validationsystem.laboratory;

import com.mac.bry.validationsystem.department.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LaboratoryRepository extends JpaRepository<Laboratory, Long> {

    Optional<Laboratory> findByAbbreviation(String abbreviation);

    boolean existsByFullName(String fullName);

    boolean existsByAbbreviation(String abbreviation);

    /**
     * Znajduje wszystkie pracownie działu posortowane po nazwie
     * NOWA METODA!
     */
    List<Laboratory> findByDepartmentOrderByFullNameAsc(Department department);

    List<Laboratory> findByDepartmentId(Long departmentId);

    /**
     * Znajduje wszystkie pracownie o podanych ID posortowane po nazwie
     */
    List<Laboratory> findAllByIdInOrderByFullNameAsc(java.util.Collection<Long> ids);

    /**
     * Znajduje wszystkie pracownie należące do podanych działów posortowane po
     * nazwie
     */
    List<Laboratory> findAllByDepartmentIdInOrderByFullNameAsc(java.util.Collection<Long> departmentIds);

    /**
     * Znajduje wszystkie pracownie należące do działów z podanych firm posortowane
     * po nazwie
     */
    List<Laboratory> findAllByDepartmentCompanyIdInOrderByFullNameAsc(java.util.Collection<Long> companyIds);
}
