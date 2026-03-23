package com.mac.bry.validationsystem.department;

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
class DepartmentRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DepartmentRepository repository;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setName("Dept Test Co");
        entityManager.persist(company);
        entityManager.flush();
    }

    @Test
    @DisplayName("Should find departments by company ordered by name")
    void shouldFindByCompany() {
        Department d2 = new Department();
        d2.setName("B");
        d2.setCompany(company);
        d2.setAbbreviation("B");
        
        Department d1 = new Department();
        d1.setName("A");
        d1.setCompany(company);
        d1.setAbbreviation("A");

        entityManager.persist(d2);
        entityManager.persist(d1);
        entityManager.flush();

        List<Department> result = repository.findByCompanyOrderByNameAsc(company);

        assertEquals(2, result.size());
        assertEquals("A", result.get(0).getName());
        assertEquals("B", result.get(1).getName());
    }

    @Test
    @DisplayName("Should find departments with laboratories")
    void shouldFindByHasLaboratories() {
        Department d1 = new Department();
        d1.setName("LabDept");
        d1.setCompany(company);
        d1.setAbbreviation("LD");
        d1.setHasLaboratories(true);

        Department d2 = new Department();
        d2.setName("NoLabDept");
        d2.setCompany(company);
        d2.setAbbreviation("NL");
        d2.setHasLaboratories(false);

        entityManager.persist(d1);
        entityManager.persist(d2);
        entityManager.flush();

        List<Department> result = repository.findByHasLaboratoriesTrue();

        assertEquals(1, result.size());
        assertEquals("LabDept", result.get(0).getName());
    }
}
