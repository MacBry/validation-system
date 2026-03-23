package com.mac.bry.validationsystem.materialtype;

import com.mac.bry.validationsystem.company.Company;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class MaterialTypeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MaterialTypeRepository repository;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setName("Material Test Co");
        entityManager.persist(company);
        entityManager.flush();
    }

    @Test
    @DisplayName("Should find materials by company ordered by name")
    void shouldFindByCompany() {
        MaterialType m1 = MaterialType.builder()
                .name("Z Material")
                .company(company)
                .active(true)
                .build();
        
        MaterialType m2 = MaterialType.builder()
                .name("A Material")
                .company(company)
                .active(true)
                .build();

        MaterialType global = MaterialType.builder()
                .name("G Material")
                .company(null)
                .active(true)
                .build();

        entityManager.persist(m1);
        entityManager.persist(m2);
        entityManager.persist(global);
        entityManager.flush();

        List<MaterialType> result = repository.findAllByCompanyIdInOrCompanyIsNullOrderByNameAsc(List.of(company.getId()));

        assertEquals(3, result.size());
        assertEquals("A Material", result.get(0).getName());
        assertEquals("G Material", result.get(1).getName());
        assertEquals("Z Material", result.get(2).getName());
    }

    @Test
    @DisplayName("Should check existence globally or by company")
    void shouldCheckExistence() {
        MaterialType global = MaterialType.builder()
                .name("Global Item")
                .company(null)
                .build();
        entityManager.persist(global);
        entityManager.flush();

        assertTrue(repository.existsByNameAndCompanyIsNull("Global Item"));
        assertFalse(repository.existsByNameAndCompanyId("Global Item", company.getId()));
    }
}
