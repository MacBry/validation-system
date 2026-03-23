package com.mac.bry.validationsystem.security.controller;

import com.mac.bry.validationsystem.company.CompanyService;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.service.AuditService;
import com.mac.bry.validationsystem.security.service.SecurityService;
import com.mac.bry.validationsystem.security.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PasswordExpiryController.class, properties = "spring.thymeleaf.enabled=false")
@org.springframework.context.annotation.Import(PasswordExpiryControllerTest.SecurityConfig.class)
class PasswordExpiryControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class SecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private SecurityService securityService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private CompanyService companyService;

    private User testUser;
    private User expiredUser;
    private User expiringSoonUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        expiredUser = new User();
        expiredUser.setId(2L);
        expiredUser.setUsername("expireduser");
        expiredUser.setEmail("expired@example.com");
        expiredUser.setPasswordExpiresAt(LocalDateTime.now().minusDays(5));

        expiringSoonUser = new User();
        expiringSoonUser.setId(3L);
        expiringSoonUser.setUsername("expiringsoon");
        expiringSoonUser.setEmail("soon@example.com");
        expiringSoonUser.setPasswordExpiresAt(LocalDateTime.now().plusDays(3));
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void passwordExpiryManagement_ShouldDisplayManagementPage() throws Exception {
        // Given
        List<User> expiredUsers = Arrays.asList(expiredUser);
        List<User> expiringSoonUsers = Arrays.asList(expiringSoonUser);
        List<User> expiringIn30DaysUsers = Arrays.asList(testUser);

        when(userService.findUsersWithExpiredPasswords()).thenReturn(expiredUsers);
        when(userService.findUsersWithPasswordsExpiringInDays(7)).thenReturn(expiringSoonUsers);
        when(userService.findUsersWithPasswordsExpiringInDays(30)).thenReturn(expiringIn30DaysUsers);

        // When & Then
        mockMvc.perform(get("/admin/password-expiry"))
                .andExpect(status().isOk())
                .andExpect(view().name("security/admin/password-expiry"))
                .andExpect(model().attributeExists("expiredUsers"))
                .andExpect(model().attributeExists("expiringSoon"))
                .andExpect(model().attributeExists("expiringIn30Days"))
                .andExpect(model().attribute("expiredUsers", expiredUsers))
                .andExpect(model().attribute("expiringSoon", expiringSoonUsers))
                .andExpect(model().attribute("expiringIn30Days", expiringIn30DaysUsers));

        verify(userService).findUsersWithExpiredPasswords();
        verify(userService).findUsersWithPasswordsExpiringInDays(7);
        verify(userService).findUsersWithPasswordsExpiringInDays(30);
    }

    @Test
    @WithMockUser(roles = { "USER" })
    void passwordExpiryManagement_ShouldDenyAccessForRegularUser() throws Exception {
        // When & Then
        mockMvc.perform(get("/admin/password-expiry"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void extendPasswordExpiry_ShouldExtendPasswordAndReturnSuccessMessage() throws Exception {
        // Given
        when(userService.findById(1L)).thenReturn(testUser);
        doNothing().when(userService).extendPasswordExpiry(1L, 30);

        // When & Then
        mockMvc.perform(post("/admin/password-expiry/extend/1")
                .with(csrf())
                .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ważność hasła przedłużona o 30 dni"));

        verify(userService).findById(1L);
        verify(userService).extendPasswordExpiry(1L, 30);
        verify(auditService).logOperation("User", 1L, "PASSWORD_EXPIRY_EXTENDED", "Extended by 30 days", null);
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void extendPasswordExpiry_WithNonExistentUser_ShouldReturnErrorMessage() throws Exception {
        // Given
        when(userService.findById(999L)).thenReturn(null);

        // When & Then
        mockMvc.perform(post("/admin/password-expiry/extend/999")
                .with(csrf())
                .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(content().string("Użytkownik nie został znaleziony"));

        verify(userService).findById(999L);
        verify(userService, never()).extendPasswordExpiry(anyLong(), anyInt());
        verify(auditService, never()).logOperation(anyString(), any(), anyString(), any(), any());
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void setPasswordExpiryPolicy_ShouldUpdatePolicyAndRedirect() throws Exception {
        // Given
        when(userService.findById(1L)).thenReturn(testUser);
        doNothing().when(userService).setPasswordExpiryPolicy(1L, 180);

        // When & Then
        mockMvc.perform(post("/admin/password-expiry/set-policy/1")
                .with(csrf())
                .param("expiryDays", "180"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/password-expiry"))
                .andExpect(flash().attribute("success",
                        "Ustawiono politykę wygaszania: 180 dni dla użytkownika testuser"));

        verify(userService).findById(1L);
        verify(userService).setPasswordExpiryPolicy(1L, 180);
        verify(auditService).logOperation("User", 1L, "PASSWORD_EXPIRY_POLICY_CHANGED", "Set to 180 days", null);
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void setPasswordExpiryPolicy_WithNullValue_ShouldDisableExpiry() throws Exception {
        // Given
        when(userService.findById(1L)).thenReturn(testUser);
        doNothing().when(userService).setPasswordExpiryPolicy(1L, null);

        // When & Then
        mockMvc.perform(post("/admin/password-expiry/set-policy/1")
                .with(csrf())
                .param("expiryDays", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/password-expiry"))
                .andExpect(flash().attribute("success",
                        "Wyłączono wygaszanie hasła dla użytkownika testuser"));

        verify(userService).findById(1L);
        verify(userService).setPasswordExpiryPolicy(1L, null);
        verify(auditService).logOperation("User", 1L, "PASSWORD_EXPIRY_POLICY_CHANGED", "Set to null days", null);
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void forceExpiredPasswordChanges_ShouldForceChangesAndRedirect() throws Exception {
        // Given
        List<User> expiredUsers = Arrays.asList(expiredUser, testUser);
        when(userService.findUsersWithExpiredPasswords()).thenReturn(expiredUsers);
        doNothing().when(userService).forcePasswordChangeForExpiredUsers();

        // When & Then
        mockMvc.perform(post("/admin/password-expiry/force-expired")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/password-expiry"))
                .andExpect(flash().attribute("success",
                        "Wymuszono zmianę hasła dla 2 użytkowników z wygasłymi hasłami"));

        verify(userService).findUsersWithExpiredPasswords();
        verify(userService).forcePasswordChangeForExpiredUsers();
        verify(auditService).logOperation("System", null, "FORCE_EXPIRED_PASSWORD_CHANGES",
                "Affected 2 users", null);
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void getPasswordExpiryStats_ShouldReturnCorrectStats() throws Exception {
        // Given
        List<User> expiredUsers = Arrays.asList(expiredUser);
        List<User> expiring7Days = Arrays.asList(expiringSoonUser);
        List<User> expiring30Days = Arrays.asList(testUser);

        when(userService.findUsersWithExpiredPasswords()).thenReturn(expiredUsers);
        when(userService.findUsersWithPasswordsExpiringInDays(7)).thenReturn(expiring7Days);
        when(userService.findUsersWithPasswordsExpiringInDays(30)).thenReturn(expiring30Days);

        // When & Then
        mockMvc.perform(get("/admin/password-expiry/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(1))
                .andExpect(jsonPath("$.expiring7Days").value(1))
                .andExpect(jsonPath("$.expiring30Days").value(1));

        verify(userService).findUsersWithExpiredPasswords();
        verify(userService).findUsersWithPasswordsExpiringInDays(7);
        verify(userService).findUsersWithPasswordsExpiringInDays(30);
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void setPasswordExpiryPolicy_WithServiceException_ShouldReturnErrorFlash() throws Exception {
        // Given
        when(userService.findById(1L)).thenReturn(testUser);
        doThrow(new RuntimeException("Database error")).when(userService).setPasswordExpiryPolicy(1L, 90);

        // When & Then
        mockMvc.perform(post("/admin/password-expiry/set-policy/1")
                .with(csrf())
                .param("expiryDays", "90"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/password-expiry"))
                .andExpect(flash().attribute("error", "Błąd podczas ustawiania polityki: Database error"));

        verify(userService).findById(1L);
        verify(userService).setPasswordExpiryPolicy(1L, 90);
        verify(auditService, never()).logOperation(anyString(), any(), anyString(), any(), any());
    }

    @Test
    @WithMockUser(roles = { "SUPER_ADMIN" })
    void passwordExpiryManagement_ShouldAllowAccessForSuperAdmin() throws Exception {
        // Given
        when(userService.findUsersWithExpiredPasswords()).thenReturn(Arrays.asList());
        when(userService.findUsersWithPasswordsExpiringInDays(anyInt())).thenReturn(Arrays.asList());

        // When & Then
        mockMvc.perform(get("/admin/password-expiry"))
                .andExpect(status().isOk())
                .andExpect(view().name("security/admin/password-expiry"));
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void extendPasswordExpiry_WithServiceException_ShouldReturnErrorMessage() throws Exception {
        // Given
        when(userService.findById(1L)).thenReturn(testUser);
        doThrow(new RuntimeException("Service error")).when(userService).extendPasswordExpiry(1L, 30);

        // When & Then
        mockMvc.perform(post("/admin/password-expiry/extend/1")
                .with(csrf())
                .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(content().string("Błąd: Service error"));

        verify(userService).findById(1L);
        verify(userService).extendPasswordExpiry(1L, 30);
        verify(auditService, never()).logOperation(anyString(), any(), anyString(), any(), any());
    }
}