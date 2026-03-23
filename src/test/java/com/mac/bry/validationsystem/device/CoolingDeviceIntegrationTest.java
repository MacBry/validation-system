package com.mac.bry.validationsystem.device;

import com.mac.bry.validationsystem.ValidationSystemApplication;
import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.department.DepartmentRepository;
import com.mac.bry.validationsystem.validationplan.ValidationPlanNumber;
import com.mac.bry.validationsystem.validationplan.ValidationPlanNumberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ValidationSystemApplication.class)
@ActiveProfiles("test")
@Transactional
class CoolingDeviceIntegrationTest {

    @Autowired
    private CoolingDeviceService coolingDeviceService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ValidationPlanNumberRepository validationPlanNumberRepository;

    private Department testDept;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setName("Integration Test Company");
        company.setCreatedDate(LocalDateTime.now());
        company = companyRepository.save(company);

        testDept = new Department();
        testDept.setName("Integration Test Dept");
        testDept.setAbbreviation("ITD");
        testDept.setCompany(company);
        testDept = departmentRepository.save(testDept);
    }

    @Test
    @DisplayName("Should perform full CRUD flow for CoolingDevice")
    void fullCrudFlow() {
        // 1. Create
        CoolingDevice device = CoolingDevice.builder()
                .inventoryNumber("INT-001")
                .name("Integration Fridge")
                .department(testDept)
                .chamberType(ChamberType.FRIDGE)
                .build();

        CoolingDevice saved = coolingDeviceService.save(device);
        assertNotNull(saved.getId());

        // 2. Read
        Optional<CoolingDevice> found = coolingDeviceService.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Integration Fridge", found.get().getName());

        // 3. Update
        saved.setName("Updated Fridge");
        coolingDeviceService.save(saved);
        
        Optional<CoolingDevice> updated = coolingDeviceService.findById(saved.getId());
        assertEquals("Updated Fridge", updated.get().getName());

        // 4. Delete
        coolingDeviceService.deleteById(saved.getId());
        assertFalse(coolingDeviceService.findById(saved.getId()).isPresent());
    }

    @Test
    @DisplayName("Should manage Validation Plan Numbers")
    void validationPlanManagement() {
        // Given
        CoolingDevice device = CoolingDevice.builder()
                .inventoryNumber("INT-002")
                .name("Plan Test Fridge")
                .department(testDept)
                .chamberType(ChamberType.FRIDGE)
                .build();
        device = coolingDeviceService.save(device);

        // When - Add plan
        coolingDeviceService.addValidationPlanNumber(device.getId(), 2024, 5);

        // Then
        CoolingDevice withPlan = coolingDeviceService.findById(device.getId()).get();
        assertEquals(1, withPlan.getValidationPlanNumbers().size());
        ValidationPlanNumber vpn = withPlan.getValidationPlanNumbers().get(0);
        assertEquals(2024, vpn.getYear());
        assertEquals(5, vpn.getPlanNumber());

        // When - Remove plan
        coolingDeviceService.removeValidationPlanNumber(device.getId(), vpn.getId());

        // Then
        CoolingDevice noPlan = coolingDeviceService.findById(device.getId()).get();
        assertTrue(noPlan.getValidationPlanNumbers().isEmpty());
    }
}
