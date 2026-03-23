package com.mac.bry.validationsystem.materialtype;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialTypeRepository extends JpaRepository<MaterialType, Long> {

    /**
     * Znajduje wszystkie materiały przypisane do konkretnych firm lub globalne
     * (null)
     */
    List<MaterialType> findAllByCompanyIdInOrCompanyIsNullOrderByNameAsc(java.util.Collection<Long> companyIds);

    /**
     * Znajduje wszystkie aktywne materiały dla firm lub globalne
     */
    List<MaterialType> findByActiveTrueAndCompanyIdInOrActiveTrueAndCompanyIsNullOrderByNameAsc(
            java.util.Collection<Long> companyIds);

    /**
     * Sprawdza czy materiał o podanej nazwie istnieje w ramach konkretnej firmy lub
     * globalnie
     */
    boolean existsByNameAndCompanyId(String name, Long companyId);

    /**
     * Sprawdza czy materiał o podanej nazwie istnieje jako globalny
     */
    boolean existsByNameAndCompanyIsNull(String name);

    /**
     * Znajduje wszystkie materiały posortowane po nazwie
     */
    List<MaterialType> findAllByOrderByNameAsc();
}
