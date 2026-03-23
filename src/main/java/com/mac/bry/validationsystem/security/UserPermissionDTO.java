package com.mac.bry.validationsystem.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO do przechowywania informacji o uprawnieniu w cache.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionDTO {
    private Long permissionId;
    private PermissionType type;
    private Long companyId;
    private String companyName;
    private Long departmentId;
    private String departmentName;
    private Long laboratoryId;
    private String laboratoryName;
}
