package com.mac.bry.validationsystem.security.service;

import com.mac.bry.validationsystem.security.Role;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.UserPermissionsCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @InjectMocks
    private SecurityService securityService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private com.mac.bry.validationsystem.department.DepartmentRepository departmentRepository;

    @Mock
    private com.mac.bry.validationsystem.laboratory.LaboratoryRepository laboratoryRepository;

    private User superAdmin;
    private User normalUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);

        // Setup Super Admin
        superAdmin = new User();
        Role adminRole = new Role();
        adminRole.setName("ROLE_SUPER_ADMIN");
        superAdmin.getRoles().add(adminRole);

        // Setup Normal User with Cache
        normalUser = new User();
        UserPermissionsCache cache = new UserPermissionsCache();
        cache.setAllowedCompanyIds(new HashSet<>(Set.of(1L, 2L)));
        cache.setAllowedDepartmentIds(new HashSet<>(Set.of(10L)));
        cache.setAllowedLaboratoryIds(new HashSet<>(Set.of(100L)));
        normalUser.setPermissionsCache(cache);
    }

    @Test
    void superAdminHasAccessToAll() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(superAdmin);

        assertTrue(securityService.hasAccessToCompany(999L));
        assertTrue(securityService.hasAccessToDepartment(999L));
        assertTrue(securityService.hasAccessToLaboratory(999L));
    }

    @Test
    void userWithCacheHasAccessToAssignedCompanies() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(normalUser);

        assertTrue(securityService.hasAccessToCompany(1L));
        assertTrue(securityService.hasAccessToCompany(2L));
        assertFalse(securityService.hasAccessToCompany(3L)); // Not in cache

        com.mac.bry.validationsystem.department.Department mockDept = new com.mac.bry.validationsystem.department.Department();
        com.mac.bry.validationsystem.company.Company mockCompany = new com.mac.bry.validationsystem.company.Company();
        mockCompany.setId(1L);
        mockDept.setCompany(mockCompany);
        org.mockito.Mockito.lenient().when(departmentRepository.findById(10L))
                .thenReturn(java.util.Optional.of(mockDept));

        assertTrue(securityService.hasAccessToDepartment(10L));
        assertFalse(securityService.hasAccessToDepartment(20L));

        assertTrue(securityService.hasAccessToLaboratory(100L));
        assertFalse(securityService.hasAccessToLaboratory(200L));
    }

    @Test
    void companyAdminHasAccessToOwnCompany() {
        User companyAdmin = new User();
        Role caRole = new Role();
        caRole.setName("ROLE_COMPANY_ADMIN");
        companyAdmin.getRoles().add(caRole);

        UserPermissionsCache cache = new UserPermissionsCache();
        cache.setAllowedCompanyIds(new HashSet<>(Set.of(500L)));
        companyAdmin.setPermissionsCache(cache);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(companyAdmin);

        assertTrue(securityService.hasAccessToCompany(500L));
        assertFalse(securityService.hasAccessToCompany(999L));
    }

    @Test
    void cacheLookupsAreFast() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(normalUser);

        long start = System.nanoTime();

        for (int i = 0; i < 10000; i++) {
            securityService.hasAccessToCompany(1L);
            securityService.hasAccessToDepartment(10L);
            securityService.hasAccessToLaboratory(100L);
        }

        long durationMs = (System.nanoTime() - start) / 1_000_000;

        // Czas wykonania 30 000 operacji wyciągnięcia z hashsetu (O(1)) na CI może
        // zająć do ~2500ms
        // ze względu na narzut na Mockito
        assertTrue(durationMs < 2500, "Cache lookups are too slow! Took " + durationMs + "ms");
    }
}
