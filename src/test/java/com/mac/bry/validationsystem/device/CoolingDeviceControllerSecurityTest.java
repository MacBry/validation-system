package com.mac.bry.validationsystem.device;

import com.mac.bry.validationsystem.department.DepartmentService;
import com.mac.bry.validationsystem.laboratory.LaboratoryService;
import com.mac.bry.validationsystem.materialtype.MaterialTypeService;
import com.mac.bry.validationsystem.security.service.SecurityService;
import com.mac.bry.validationsystem.validation.ValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(CoolingDeviceController.class)
@EnableMethodSecurity
public class CoolingDeviceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CoolingDeviceService coolingDeviceService;

    @MockBean
    private DepartmentService departmentService;

    @MockBean
    private LaboratoryService laboratoryService;

    @MockBean
    private MaterialTypeService materialTypeService;

    @MockBean
    private ValidationService validationService;

    @MockBean
    private com.mac.bry.validationsystem.audit.EnversRevisionService enversRevisionService;

    @MockBean
    private com.mac.bry.validationsystem.company.CompanyService companyService;

    @MockBean
    private com.mac.bry.validationsystem.security.service.AuditService auditService;

    // Musimy mockować SecurityService bo używamy go w SpEL:
    // @PreAuthorize("@securityService...")
    @MockBean(name = "securityService")
    private SecurityService securityService;

    @Test
    @WithMockUser(username = "zwyklyUser", roles = "USER")
    public void shouldAllowAccessWhenSecurityServiceReturnsTrue() throws Exception {
        // Given: użytkownik ma uprawnienia (SecurityService.canManageDevice zwraca
        // true)
        when(securityService.canManageDevice(1L)).thenReturn(true);

        // When/Then: dostęp jest dozwolony (zwraca OK, mock urządzenia)
        // Oczekujemy statusu powiązania (albo rzuci błąd view, ale nie 403 Forbidden)
        mockMvc.perform(get("/devices/1"))
                .andExpect(status().isFound()); // redirection bo urządzenie mockowane rzuca Empty (orElse redirect)
    }

    @Test
    @WithMockUser(username = "zwyklyUser", roles = "USER")
    public void shouldDenyAccessWhenSecurityServiceReturnsFalse() throws Exception {
        // Given: użytkownik NIE MA uprawnień (np. inna firma/dział)
        when(securityService.canManageDevice(1L)).thenReturn(false);

        // When/Then: dostęp odrzucony - 403 Forbidden
        mockMvc.perform(get("/devices/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "zwyklyUser", roles = "USER")
    public void shouldDenyPostWhenNoAccess() throws Exception {
        // Given: użytkownik uderza w endpoint usuwania bez uprawnień
        when(securityService.canManageDevice(2L)).thenReturn(false);

        // When/Then: dostęp odrzucony - 403 Forbidden
        mockMvc.perform(post("/devices/2/delete").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
