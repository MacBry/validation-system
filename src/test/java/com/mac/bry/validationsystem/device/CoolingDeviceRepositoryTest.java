package com.mac.bry.validationsystem.device;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.department.DepartmentRepository;
import com.mac.bry.validationsystem.laboratory.Laboratory;
import com.mac.bry.validationsystem.laboratory.LaboratoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CoolingDeviceRepositoryTest {

    @Autowired
    private CoolingDeviceRepository coolingDeviceRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private LaboratoryRepository laboratoryRepository;

    private Company company1;
    private Department dept1;
    private Laboratory lab1;
    private CoolingDevice device1;

    @BeforeEach
    void setUp() {
        company1 = new Company();
        company1.setName("Company 1");
        company1.setCreatedDate(LocalDateTime.now());
        company1 = companyRepository.save(company1);

        dept1 = new Department();
        dept1.setName("Dept 1");
        dept1.setAbbreviation("D1");
        dept1.setCompany(company1);
        dept1 = departmentRepository.save(dept1);

        lab1 = new Laboratory();
        lab1.setFullName("Lab 1");
        lab1.setAbbreviation("L1");
        lab1.setDepartment(dept1);
        lab1 = laboratoryRepository.save(lab1);

        device1 = CoolingDevice.builder()
                .inventoryNumber("DEV-001")
                .name("Refrigerator Alpha")
                .department(dept1)
                .laboratory(lab1)
                .chamberType(ChamberType.FRIDGE)
                .build();
        coolingDeviceRepository.save(device1);
    }

    @Test
    @DisplayName("Should find all devices for super admin")
    void shouldFindAllForSuperAdmin() {
        // When
        Page<CoolingDevice> result = coolingDeviceRepository.findAllAccessible(
                true, Set.of(), Set.of(), Set.of(), PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Should find device by company ID")
    void shouldFindByCompanyId() {
        // When
        Page<CoolingDevice> result = coolingDeviceRepository.findAllAccessible(
                false, Set.of(company1.getId()), Set.of(), Set.of(), PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Should find device by department ID")
    void shouldByDeptId() {
        // When
        Page<CoolingDevice> result = coolingDeviceRepository.findAllAccessible(
                false, Set.of(), Set.of(dept1.getId()), Set.of(), PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Should find device by laboratory ID")
    void shouldByLabId() {
        // When
        Page<CoolingDevice> result = coolingDeviceRepository.findAllAccessible(
                false, Set.of(), Set.of(), Set.of(lab1.getId()), PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Should return empty list for user with no access")
    void shouldReturnEmptyForNoAccess() {
        // When
        Page<CoolingDevice> result = coolingDeviceRepository.findAllAccessible(
                false, Set.of(99L), Set.of(99L), Set.of(99L), PageRequest.of(0, 10));

        // Then
        assertEquals(0, result.getTotalElements());
    }

    @Test
    @DisplayName("Should search device by name")
    void shouldSearchByName() {
        // When
        List<CoolingDevice> result = coolingDeviceRepository.searchAccessible(
                "Alpha", true, Set.of(), Set.of(), Set.of());

        // Then
        assertEquals(1, result.size());
        assertEquals("Refrigerator Alpha", result.get(0).getName());
    }

    @Test
    @DisplayName("Should search device by inventory number")
    void shouldSearchByInventoryNumber() {
        // When
        List<CoolingDevice> result = coolingDeviceRepository.searchAccessible(
                "DEV-001", true, Set.of(), Set.of(), Set.of());

        // Then
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should find by ID with relations fetched")
    void shouldFindByIdWithRelations() {
        // When
        CoolingDevice result = coolingDeviceRepository.findByIdWithRelations(device1.getId()).orElse(null);

        // Then
        assertNotNull(result);
        assertEquals("Dept 1", result.getDepartment().getName());
        assertEquals("Lab 1", result.getLaboratory().getFullName());
    }
}
