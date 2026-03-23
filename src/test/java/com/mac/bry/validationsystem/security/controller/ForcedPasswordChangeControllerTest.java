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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
class ForcedPasswordChangeControllerTest {

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

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setMustChangePassword(true);
    }

    @Test
    @WithMockUser(username = "testuser")
    void showForcedPasswordChangeForm_WhenUserMustChangePassword_ShouldShowForm() throws Exception {
        // Given
        when(securityService.getCurrentUser()).thenReturn(testUser);

        // When & Then
        mockMvc.perform(get("/profile/forced-change-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("security/profile/forced-change-password"))
                .andExpect(model().attributeExists("forcedPasswordChangeDto"))
                .andExpect(model().attribute("user", testUser));

        verify(securityService, times(2)).getCurrentUser(); // Filter + Controller
    }

    @Test
    @WithMockUser(username = "testuser")
    void showForcedPasswordChangeForm_WhenUserDoesNotNeedToChangePassword_ShouldRedirectToHome() throws Exception {
        // Given
        testUser.setMustChangePassword(false);
        when(securityService.getCurrentUser()).thenReturn(testUser);

        // When & Then
        mockMvc.perform(get("/profile/forced-change-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(securityService, times(2)).getCurrentUser(); // Filter + Controller
    }

    @Test
    @WithMockUser(username = "testuser")
    void processForcedPasswordChange_WithValidData_ShouldChangePasswordAndRedirect() throws Exception {
        // Given
        String newPassword = "NewPassword123!";
        when(securityService.getCurrentUser()).thenReturn(testUser);

        // When & Then
        mockMvc.perform(post("/profile/forced-change-password")
                .with(csrf())
                .param("newPassword", newPassword)
                .param("confirmPassword", newPassword))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("success", "Hasło zostało pomyślnie zmienione. Możesz teraz korzystać z systemu."));

        verify(securityService, times(2)).getCurrentUser(); // Filter + Controller
        verify(userService).changePasswordForced(1L, newPassword);
        verify(auditService).logOperation("User", 1L, "FORCED_PASSWORD_CHANGE_COMPLETED", null, null);
    }

    @Test
    @WithMockUser(username = "testuser")
    void processForcedPasswordChange_WithMismatchedPasswords_ShouldReturnFormWithError() throws Exception {
        // Given
        when(securityService.getCurrentUser()).thenReturn(testUser);

        // When & Then
        mockMvc.perform(post("/profile/forced-change-password")
                .with(csrf())
                .param("newPassword", "NewPassword123!")
                .param("confirmPassword", "DifferentPassword123!"))
                .andExpect(status().isOk())
                .andExpect(view().name("security/profile/forced-change-password"))
                .andExpect(model().hasErrors())
                .andExpect(model().errorCount(1))
                .andExpect(model().attributeHasFieldErrors("forcedPasswordChangeDto", "confirmPassword"));

        verify(securityService, times(2)).getCurrentUser(); // Filter + Controller
        verify(userService, never()).changePasswordForced(anyLong(), anyString());
        verify(auditService, never()).logOperation(anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    @WithMockUser(username = "testuser")
    void processForcedPasswordChange_WithWeakPassword_ShouldReturnFormWithError() throws Exception {
        // Given
        String weakPassword = "weak";
        when(securityService.getCurrentUser()).thenReturn(testUser);
        doThrow(new IllegalArgumentException("Password must be at least 8 characters long"))
                .when(userService).changePasswordForced(1L, weakPassword);

        // When & Then
        mockMvc.perform(post("/profile/forced-change-password")
                .with(csrf())
                .param("newPassword", weakPassword)
                .param("confirmPassword", weakPassword))
                .andExpect(status().isOk())
                .andExpect(view().name("security/profile/forced-change-password"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("forcedPasswordChangeDto", "newPassword"));

        verify(securityService, times(2)).getCurrentUser(); // Filter + Controller
        verify(userService).changePasswordForced(1L, weakPassword);
        verify(auditService, never()).logOperation(anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    @WithMockUser(username = "testuser")
    void processForcedPasswordChange_WhenUserDoesNotNeedToChangePassword_ShouldRedirectToHome() throws Exception {
        // Given
        testUser.setMustChangePassword(false);
        when(securityService.getCurrentUser()).thenReturn(testUser);

        // When & Then
        mockMvc.perform(post("/profile/forced-change-password")
                .with(csrf())
                .param("newPassword", "NewPassword123!")
                .param("confirmPassword", "NewPassword123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(securityService, times(2)).getCurrentUser(); // Filter + Controller
        verify(userService, never()).changePasswordForced(anyLong(), anyString());
        verify(auditService, never()).logOperation(anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    @WithMockUser(username = "testuser")
    void processForcedPasswordChange_WithBlankPassword_ShouldReturnFormWithValidationError() throws Exception {
        // Given
        when(securityService.getCurrentUser()).thenReturn(testUser);

        // When & Then
        mockMvc.perform(post("/profile/forced-change-password")
                .with(csrf())
                .param("newPassword", "")
                .param("confirmPassword", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("security/profile/forced-change-password"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("forcedPasswordChangeDto", "newPassword"))
                .andExpect(model().attributeHasFieldErrors("forcedPasswordChangeDto", "confirmPassword"));

        verify(securityService, times(2)).getCurrentUser(); // Filter + Controller
        verify(userService, never()).changePasswordForced(anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = "testuser")
    void processForcedPasswordChange_WithServiceException_ShouldRedirectWithError() throws Exception {
        // Given
        String newPassword = "NewPassword123!";
        when(securityService.getCurrentUser()).thenReturn(testUser);
        doThrow(new RuntimeException("Database error"))
                .when(userService).changePasswordForced(1L, newPassword);

        // When & Then
        mockMvc.perform(post("/profile/forced-change-password")
                .with(csrf())
                .param("newPassword", newPassword)
                .param("confirmPassword", newPassword))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile/forced-change-password"))
                .andExpect(flash().attribute("error", "Wystąpił nieoczekiwany błąd podczas zmiany hasła. Spróbuj ponownie."));

        verify(securityService, times(2)).getCurrentUser(); // Filter + Controller
        verify(userService).changePasswordForced(1L, newPassword);
        verify(auditService, never()).logOperation(anyString(), anyLong(), anyString(), any(), any());
    }
}