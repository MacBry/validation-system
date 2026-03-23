package com.mac.bry.validationsystem.security.controller;

import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.RoleRepository;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import com.mac.bry.validationsystem.security.service.UserService;
import com.mac.bry.validationsystem.security.service.SessionManagementService;
import com.mac.bry.validationsystem.security.handler.CsrfViolationHandler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class, properties = "spring.thymeleaf.enabled=false")
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(UserControllerTest.SecurityConfig.class)
class UserControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class SecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private SessionManagementService sessionManagementService;

    @MockBean
    private CsrfViolationHandler csrfViolationHandler;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private RoleRepository roleRepository;

    @MockBean
    private com.mac.bry.validationsystem.security.service.CustomUserDetailsService customUserDetailsService;

    @MockBean
    private com.mac.bry.validationsystem.security.handler.CustomAuthenticationSuccessHandler successHandler;

    @MockBean
    private com.mac.bry.validationsystem.security.handler.CustomAuthenticationFailureHandler failureHandler;

    @MockBean
    private com.mac.bry.validationsystem.security.service.LoginHistoryService loginHistoryService;

    @MockBean
    private com.mac.bry.validationsystem.security.service.RateLimiterService rateLimiterService;

    @MockBean
    private com.mac.bry.validationsystem.security.service.AuditService auditService;

    @MockBean
    private com.mac.bry.validationsystem.security.service.SecurityService securityService;

    @MockBean
    private com.mac.bry.validationsystem.security.service.PermissionService permissionService;

    @MockBean
    private com.mac.bry.validationsystem.security.repository.UserPermissionRepository userPermissionRepository;

    @MockBean
    private com.mac.bry.validationsystem.security.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @MockBean
    private com.mac.bry.validationsystem.company.CompanyRepository companyRepository;

    @MockBean
    private com.mac.bry.validationsystem.company.CompanyService companyService;

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void listUsers_WithAdminRole_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("security/users/list"));
    }

    @Test
    @WithMockUser(username = "user", roles = { "USER" })
    void listUsers_WithUserRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void toggleEnabled_WithValidUser_ShouldRedirectBack() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEnabled(true);

        Mockito.when(securityService.hasAccessToUser(1L)).thenReturn(true);
        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/users/1/toggle-enabled").with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("success", "Zmieniono status aktywności konta."));

        Mockito.verify(userService).setUserEnabled(1L, false);
    }
}
