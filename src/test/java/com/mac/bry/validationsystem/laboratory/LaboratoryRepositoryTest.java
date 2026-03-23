package com.mac.bry.validationsystem.laboratory;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.department.Department;
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
class LaboratoryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LaboratoryRepository repository;

    private Company company;
    private Department department;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setName("Org Unit Test Co");
        entityManager.persist(company);

        department = new Department();
        department.setName("Org Dept");
        department.setCompany(company);
        department.setAbbreviation("OD");
        entityManager.persist(department);
        
        entityManager.flush();
    }

    @Test
    @DisplayName("Should find laboratories by department ordered by full name")
    void shouldFindByDepartmentOrdered() {
        Laboratory l2 = new Laboratory();
        l2.setFullName("B Lab");
        l2.setAbbreviation("B");
        l2.setDepartment(department);

        Laboratory l1 = new Laboratory();
        l1.setFullName("A Lab");
        l1.setAbbreviation("A");
        l1.setDepartment(department);

        entityManager.persist(l2);
        entityManager.persist(l1);
        entityManager.flush();

        List<Laboratory> result = repository.findByDepartmentOrderByFullNameAsc(department);

        assertEquals(2, result.size());
        assertEquals("A Lab", result.get(0).getFullName());
        assertEquals("B Lab", result.get(1).getFullName());
    }

    @Test
    @DisplayName("Should find laboratories by company ID")
    void shouldFindByCompanyId() {
        Laboratory l1 = new Laboratory();
        l1.setFullName("Company Lab");
        l1.setAbbreviation("CL");
        l1.setDepartment(department);
        
        entityManager.persist(l1);
        entityManager.flush();

        List<Laboratory> result = repository.findAllByDepartmentCompanyIdInOrderByFullNameAsc(List.of(company.getId()));

        assertEquals(1, result.size());
        assertEquals("Company Lab", result.get(0).getFullName());
    }
}
