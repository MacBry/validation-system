package com.mac.bry.validationsystem.company;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CompanyRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    @DisplayName("Should correctly save and find company")
    void shouldSaveAndFind() {
        // Given
        Company company = new Company();
        company.setName("Persistence Test");
        company.setAddress("Street 1");
        
        // When
        Company saved = companyRepository.save(company);
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<Company> found = companyRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Persistence Test", found.get().getName());
    }
}
