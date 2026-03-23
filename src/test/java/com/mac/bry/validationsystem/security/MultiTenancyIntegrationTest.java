package com.mac.bry.validationsystem.security;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.department.DepartmentRepository;
import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.device.CoolingDeviceRepository;
import com.mac.bry.validationsystem.device.CoolingDeviceService;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.mac.bry.validationsystem.device.ChamberType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.context.annotation.Import;
import com.mac.bry.validationsystem.config.TestMailConfig;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestMailConfig.class)
public class MultiTenancyIntegrationTest {

    @Autowired
    private CoolingDeviceService coolingDeviceService;

    @Autowired
    private CoolingDeviceRepository coolingDeviceRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.mac.bry.validationsystem.laboratory.LaboratoryRepository laboratoryRepository;

    @Autowired
    private com.mac.bry.validationsystem.thermorecorder.ThermoRecorderRepository thermoRecorderRepository;

    private User tenantUser;
    private Department dept1;
    private Department dept2;

    @BeforeEach
    void setUp() {
        coolingDeviceRepository.deleteAll();
        thermoRecorderRepository.deleteAll();
        laboratoryRepository.deleteAll();
        departmentRepository.deleteAll();
        companyRepository.deleteAll();
        userRepository.deleteAll();

        Company company = new Company();
        company.setName("Test Company");
        company = companyRepository.save(company);

        dept1 = new Department();
        dept1.setName("Department 1");
        dept1.setAbbreviation("D1");
        dept1.setCompany(company);
        dept1 = departmentRepository.save(dept1);

        dept2 = new Department();
        dept2.setName("Department 2");
        dept2.setAbbreviation("D2");
        dept2.setCompany(company);
        dept2 = departmentRepository.save(dept2);

        // Tworzymy użytkownika przypisanego tylko do Department 1 (via cache)
        tenantUser = new User();
        tenantUser.setUsername("tenantuser");
        tenantUser.setPassword("pass");
        tenantUser.setEmail("tenant@example.com");

        UserPermissionsCache cache = new UserPermissionsCache();
        cache.setAllowedDepartmentIds(new HashSet<>(Set.of(dept1.getId())));
        tenantUser.setPermissionsCache(cache);
        tenantUser = userRepository.save(tenantUser);

        // Security Context
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(tenantUser, null,
                tenantUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @Transactional
    void userSeesOnlyAssignedData() {
        CoolingDevice dev1 = new CoolingDevice();
        dev1.setName("Device 1 (D1)");
        dev1.setInventoryNumber("SN1");
        dev1.setChamberType(ChamberType.FRIDGE);
        dev1.setDepartment(dept1);
        coolingDeviceRepository.save(dev1);

        CoolingDevice dev2 = new CoolingDevice();
        dev2.setName("Device 2 (D1)");
        dev2.setInventoryNumber("SN2");
        dev2.setChamberType(ChamberType.FRIDGE);
        dev2.setDepartment(dept1);
        coolingDeviceRepository.save(dev2);

        CoolingDevice dev3 = new CoolingDevice();
        dev3.setName("Device 3 (D2)");
        dev3.setInventoryNumber("SN3");
        dev3.setChamberType(ChamberType.FRIDGE);
        dev3.setDepartment(dept2);
        coolingDeviceRepository.save(dev3);

        // Test
        List<CoolingDevice> devices = coolingDeviceService.getAllAccessibleDevices();

        assertEquals(2, devices.size(), "Should only return 2 devices from Department 1");
        assertTrue(devices.stream().allMatch(d -> d.getDepartment().getId().equals(dept1.getId())));
    }
}
