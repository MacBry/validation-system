package com.mac.bry.validationsystem.security;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.department.DepartmentRepository;
import com.mac.bry.validationsystem.device.ChamberType;
import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.device.CoolingDeviceRepository;
import com.mac.bry.validationsystem.device.CoolingDeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class DataFilteringPerformanceTest {

    @Autowired
    private CoolingDeviceService coolingDeviceService;

    @Autowired
    private CoolingDeviceRepository coolingDeviceRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private CompanyRepository companyRepository;

    private User testUser;
    private Department testDept;

    @BeforeEach
    void setUp() {
        // Prepare valid company and dept
        Company comp = new Company();
        comp.setName("Perf Company");
        comp = companyRepository.save(comp);

        testDept = new Department();
        testDept.setName("Perf Dept");
        testDept.setAbbreviation("PD");
        testDept.setCompany(comp);
        testDept = departmentRepository.save(testDept);

        // Zabezpieczenie przed wielokrotnym wstawianiem danych, jeśli testy by się
        // powtarzały
        if (coolingDeviceRepository.count() < 1000) {
            List<CoolingDevice> batch = new ArrayList<>();
            for (int i = 0; i < 2000; i++) {
                CoolingDevice dev = new CoolingDevice();
                dev.setName("Perf Device " + i);
                dev.setInventoryNumber("PERF-SN" + i);
                dev.setChamberType(ChamberType.FRIDGE);
                dev.setDepartment(testDept);
                batch.add(dev);

                // Optymalizacja batchowania zapisu
                if (i % 500 == 0) {
                    coolingDeviceRepository.saveAllAndFlush(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                coolingDeviceRepository.saveAllAndFlush(batch);
            }
        }

        testUser = new User();
        testUser.setUsername("perfuser");
        testUser.setPassword("pass");

        UserPermissionsCache cache = new UserPermissionsCache();
        // Dajemy mu dostęp tylko do nowo utworzonego departamentu
        cache.setAllowedDepartmentIds(new HashSet<>(Set.of(testDept.getId())));
        testUser.setPermissionsCache(cache);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(testUser, null,
                testUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void dataFilteringPerformance() {
        // Rozgrzewka dla JVM (JIT)
        coolingDeviceService.getAllAccessibleDevices();

        long start = System.currentTimeMillis();

        List<CoolingDevice> devices = coolingDeviceService.getAllAccessibleDevices();

        long duration = System.currentTimeMillis() - start;

        // Czas filtrowania i ładowania na bardzo dużej puli (z połączonymi left joinami
        // i security checkami)
        // Nie powinien przekraczać 500ms dla jednego zapytania
        System.out.println("Data filtering query took: " + duration + " ms");
        assertTrue(duration < 500, "Filtrowanie danych zajęło za długo: " + duration + "ms! Oczekiwane poniżej 500ms.");
    }
}
