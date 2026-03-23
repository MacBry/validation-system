package com.mac.bry.validationsystem.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FIX #1: Permissions Cache
 * DTO przechowujące zdenormalizowane uprawnienia dla szybkiego dostępu,
 * serializowane do JSON w tabeli users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionsCache {

    private LocalDateTime lastUpdated;

    // Zestawy ID do których użytkownik ma dostęp
    @Builder.Default
    private Set<Long> allowedCompanyIds = new HashSet<>();

    @Builder.Default
    private Set<Long> allowedDepartmentIds = new HashSet<>();

    @Builder.Default
    private Set<Long> allowedLaboratoryIds = new HashSet<>();

    // CompanyId -> Poziom najwyższych uprawnień dla łatwiejszego sprawdzania
    @Builder.Default
    private Map<Long, PermissionType> companyPermissionLevels = new HashMap<>();

    // Pełna lista uprawnień dla widoku UI
    @Builder.Default
    private List<UserPermissionDTO> permissions = new ArrayList<>();

    public boolean hasAccessToCompany(Long companyId) {
        return allowedCompanyIds.contains(companyId);
    }

    public boolean hasAccessToDepartment(Long departmentId) {
        return allowedDepartmentIds.contains(departmentId);
    }

    public boolean hasAccessToLaboratory(Long laboratoryId) {
        return allowedLaboratoryIds.contains(laboratoryId);
    }
}
