package com.mac.bry.validationsystem.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository dla firm
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    
    /**
     * Znajduje firmę po nazwie
     */
    Optional<Company> findByName(String name);
}
