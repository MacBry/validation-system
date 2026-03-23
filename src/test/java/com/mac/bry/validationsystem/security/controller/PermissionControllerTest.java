package com.mac.bry.validationsystem.security.controller;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.department.DepartmentRepository;
import com.mac.bry.validationsystem.laboratory.LaboratoryRepository;
import com.mac.bry.validationsystem.security.PermissionType;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import com.mac.bry.validationsystem.security.service.PermissionService;
import com.mac.bry.validationsystem.security.service.SecurityService;
import com.mac.bry.validationsystem.security.service.CsrfManagementService;
import com.mac.bry.validationsystem.security.handler.CsrfViolationHandler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PermissionController.class, properties = "spring.thymeleaf.enabled=false")
@org.springframework.context.annotation.Import(PermissionControllerTest.SecurityConfig.class)
class PermissionControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class SecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CsrfManagementService csrfManagementService;

    @MockBean
    private CsrfViolationHandler csrfViolationHandler;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private SecurityService securityService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private CompanyRepository companyRepository;

    @MockBean
    private DepartmentRepository departmentRepository;

    @MockBean
    private LaboratoryRepository laboratoryRepository;

    @MockBean
    private com.mac.bry.validationsystem.security.service.CustomUserDetailsService customUserDetailsService;

    @MockBean
    private com.mac.bry.validationsystem.security.service.LoginHistoryService loginHistoryService;

    @MockBean
    private com.mac.bry.validationsystem.security.service.RateLimiterService rateLimiterService;

    @MockBean
    private com.mac.bry.validationsystem.security.repository.UserPermissionRepository userPermissionRepository;

    @MockBean
    private com.mac.bry.validationsystem.security.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @MockBean
    private com.mac.bry.validationsystem.security.service.EmailService emailService;

    @MockBean
    private com.mac.bry.validationsystem.security.handler.CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @MockBean
    private com.mac.bry.validationsystem.company.CompanyService companyService;

    @MockBean
    private com.mac.bry.validationsystem.security.handler.CustomAuthenticationFailureHandler customAuthenticationFailureHandler;

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getDepartmentsAjax_ShouldReturnJsonArray() throws Exception {
        Company mockCompany = new Company();
        mockCompany.setId(10L);
        mockCompany.setName("TechCorp");

        Department mockDept = new Department();
        mockDept.setId(5L);
        mockDept.setName("IT");

        Mockito.when(companyRepository.findById(10L)).thenReturn(Optional.of(mockCompany));
        Mockito.when(departmentRepository.findByCompanyOrderByNameAsc(mockCompany)).thenReturn(List.of(mockDept));
        Mockito.when(securityService.hasAccessToCompany(10L)).thenReturn(true);

        mockMvc.perform(get("/permissions/api/departments")
                .param("companyId", "10")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].name").value("IT"));
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void grantCompanyAccess_ShouldCallServiceAndRedirect() throws Exception {
        User currentUser = new User();
        currentUser.setId(1L);

        Mockito.when(securityService.getCurrentUser()).thenReturn(currentUser);
        Company company = new Company();
        company.setId(2L);
        Mockito.when(companyRepository.findById(2L)).thenReturn(Optional.of(company));
        Mockito.when(securityService.hasAccessToUser(10L)).thenReturn(true);
        Mockito.when(securityService.hasAccessToCompany(2L)).thenReturn(true);

        mockMvc.perform(post("/permissions/grant")
                .param("userId", "10")
                .param("permissionType", "FULL_COMPANY")
                .param("companyId", "2")
                .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/users/10"))
                .andExpect(flash().attribute("success", "Uprawnienia zostały pomyślnie nadane."));

        Mockito.verify(permissionService).grantFullCompanyAccess(10L, company, 1L);
    }
}
