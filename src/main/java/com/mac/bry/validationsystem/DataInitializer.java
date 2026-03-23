package com.mac.bry.validationsystem;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.department.DepartmentRepository;
import com.mac.bry.validationsystem.laboratory.Laboratory;
import com.mac.bry.validationsystem.laboratory.LaboratoryRepository;
import com.mac.bry.validationsystem.materialtype.MaterialType;
import com.mac.bry.validationsystem.materialtype.MaterialTypeRepository;
import com.mac.bry.validationsystem.security.PermissionType;
import com.mac.bry.validationsystem.security.Role;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.RoleRepository;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import com.mac.bry.validationsystem.security.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Profile;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final DepartmentRepository departmentRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final MaterialTypeRepository materialTypeRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Ensure company exists
        if (companyRepository.count() == 0) {
            log.info("Inicjalizacja domyślnej firmy...");
            Company company = new Company();
            company.setName("Regionalne Centrum Krwiodawstwa i Krwiolecznictwa w Poznaniu");
            company.setAddress("ul. Marcelińska 44, 60-354 Poznań");
            companyRepository.save(company);
            log.info("Zapisano domyślną firmę");
        }

        // Initialize Administrator
        if (userRepository.count() == 0) {
            log.info("Inicjalizacja administratora systemowego...");
            initializeAdminUser();
            log.info("Inicjalizacja administratora zakończona");
        }

        // Initialize departments first
        if (departmentRepository.count() == 0) {
            log.info("Inicjalizacja wydziałów...");
            initializeDepartments();
            log.info("Inicjalizacja wydziałów zakończona");
        } else {
            log.info("Wydziały już istnieją, pomijam inicjalizację");
        }

        if (laboratoryRepository.count() == 0) {
            log.info("Inicjalizacja danych pracowni...");
            initializeLaboratories();
            log.info("Inicjalizacja danych zakończona");
        } else {
            log.info("Dane pracowni już istnieją, pomijam inicjalizację");
        }

        if (materialTypeRepository.count() == 0) {
            log.info("Inicjalizacja typów materiałów...");
            initializeMaterialTypes();
            log.info("Inicjalizacja typów materiałów zakończona");
        } else {
            log.info("Typy materiałów już istnieją, pomijam inicjalizację");
        }
    }

    private void initializeDepartments() {
        Company company = companyRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Brak firmy w bazie danych"));

        Department defaultDept = new Department();
        defaultDept.setName("Wydział Testów");
        defaultDept.setAbbreviation("WT");
        defaultDept.setDescription("Domyślny wydział do testów i inicjalizacji");
        defaultDept.setCompany(company);
        defaultDept.setHasLaboratories(true);
        departmentRepository.save(defaultDept);
        log.info("Zapisano domyślny wydział");
    }

    private void initializeLaboratories() {
        // Get default department
        Department defaultDept = departmentRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Brak wydziału w bazie danych"));

        List<Laboratory> laboratories = Arrays.asList(
                Laboratory.builder()
                        .department(defaultDept)
                        .fullName("Laboratorium Zgodności Tkankowej HLA z Pracownią Diagnostyki Genetycznej")
                        .abbreviation("LZTHLAPDG")
                        .build(),
                Laboratory.builder()
                        .department(defaultDept)
                        .fullName("Pracownia Analiz Lekarskich z Pracownią Zewnętrzną")
                        .abbreviation("PALPZ")
                        .build(),
                Laboratory.builder()
                        .department(defaultDept)
                        .fullName("Pracownia Badań Czynników Zakaźnych Metodami Serologicznymi")
                        .abbreviation("PBCZMS")
                        .build(),
                Laboratory.builder()
                        .department(defaultDept)
                        .fullName("Pracownia Badań Czynników Zakaźnych Metodami Biologii Molekularnej")
                        .abbreviation("PBCZMBM")
                        .build(),
                Laboratory.builder()
                        .department(defaultDept)
                        .fullName("Ośrodek Redystrybucji Surowic")
                        .abbreviation("ORS")
                        .build(),
                Laboratory.builder()
                        .department(defaultDept)
                        .fullName("Pracownia Diagnostyki Mikrobiologicznej")
                        .abbreviation("PDM")
                        .build(),
                Laboratory.builder()
                        .department(defaultDept)
                        .fullName("Pracownia Medycyny Spersonalizowanej i Terapii Komórkowej")
                        .abbreviation("PMSITK")
                        .build());

        laboratoryRepository.saveAll(laboratories);
        log.info("Zapisano {} pracowni", laboratories.size());
    }

    private void initializeMaterialTypes() {
        List<MaterialType> materials = Arrays.asList(
                MaterialType.builder()
                        .name("Odczynniki")
                        .description("Odczynniki laboratoryjne")
                        .minStorageTemp(2.0)
                        .maxStorageTemp(8.0)
                        .active(true)
                        .build(),
                MaterialType.builder()
                        .name("Próby")
                        .description("Próbki biologiczne")
                        .minStorageTemp(-25.0)
                        .maxStorageTemp(-15.0)
                        .active(true)
                        .build(),
                MaterialType.builder()
                        .name("Leki")
                        .description("Produkty lecznicze")
                        .minStorageTemp(2.0)
                        .maxStorageTemp(8.0)
                        .active(true)
                        .build());

        materialTypeRepository.saveAll(materials);
        log.info("Zapisano {} typów materiałów", materials.size());
    }

    private void initializeAdminUser() {
        // Create ROLE_ADMIN if not exists
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName("ROLE_ADMIN");
                    role.setDescription("Administrator Systemowy");
                    return roleRepository.save(role);
                });

        // Create Admin User
        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@validationsystem.com");
        admin.setPassword(passwordEncoder.encode("***REMOVED***"));
        admin.setFirstName("Administrator");
        admin.setLastName("Systemowy");
        admin.getRoles().add(adminRole);
        admin.setEnabled(true);
        admin.setMustChangePassword(false);
        admin.setPasswordChangedAt(java.time.LocalDateTime.now());
        admin.setPasswordExpiryDays(90);

        User savedAdmin = userRepository.save(admin);
        log.info("Utworzono użytkownika admin");

        // Grant FULL_COMPANY access to the first company
        Company defaultCompany = companyRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Nie można nadać uprawnień - brak firmy"));

        permissionService.grantFullCompanyAccess(savedAdmin.getId(), defaultCompany, savedAdmin.getId());
        log.info("Nadano uprawnienia FULL_COMPANY dla admina");
    }
}
