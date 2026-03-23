package com.mac.bry.validationsystem.calibration;

import com.mac.bry.validationsystem.security.handler.CustomAuthenticationFailureHandler;
import com.mac.bry.validationsystem.security.handler.CustomAuthenticationSuccessHandler;
import com.mac.bry.validationsystem.security.service.CustomUserDetailsService;
import com.mac.bry.validationsystem.security.service.SessionManagementService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CertificateFileController.class, properties = "spring.thymeleaf.enabled=false")
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(CertificateFileControllerTest.SecurityConfig.class)
class CertificateFileControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class SecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CalibrationService calibrationService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private CustomAuthenticationSuccessHandler successHandler;

    @MockBean
    private CustomAuthenticationFailureHandler failureHandler;

    @MockBean
    private SessionManagementService sessionManagementService;
    
    @MockBean
    private com.mac.bry.validationsystem.security.service.AuditService auditService;

    @MockBean
    private com.mac.bry.validationsystem.security.service.SecurityService securityService;

    @MockBean
    private com.mac.bry.validationsystem.company.CompanyService companyService;


    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void viewCertificate_WithAdmin_ShouldAttemptAccess() throws Exception {
        Calibration calibration = new Calibration();
        calibration.setId(1L);
        calibration.setCertificateFilePath("test.pdf");

        Mockito.when(calibrationService.findById(1L)).thenReturn(Optional.of(calibration));

        // Note: This might fail on actual file reading but we test the Security/Controller logic start
        mockMvc.perform(get("/certificates/1"))
                .andExpect(status().isNotFound()); // NotFound because the file "test.pdf" doesn't exist
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void viewCertificate_WithRegularUser_ShouldBeForbidden() throws Exception {
        mockMvc.perform(get("/certificates/1"))
                .andExpect(status().isForbidden());
    }
    
    @Test
    @WithMockUser(username = "quality", roles = {"QUALITY_MANAGER"})
    void viewCertificate_WithQualityManager_ShouldAttemptAccess() throws Exception {
        Calibration calibration = new Calibration();
        calibration.setId(1L);
        calibration.setCertificateFilePath("test.pdf");

        Mockito.when(calibrationService.findById(1L)).thenReturn(Optional.of(calibration));

        mockMvc.perform(get("/certificates/1"))
                .andExpect(status().isNotFound());
    }
}
