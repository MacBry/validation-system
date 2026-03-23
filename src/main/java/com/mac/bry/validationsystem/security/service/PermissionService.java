package com.mac.bry.validationsystem.security.service;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.laboratory.Laboratory;
import com.mac.bry.validationsystem.security.PermissionType;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.UserPermission;
import com.mac.bry.validationsystem.security.UserPermissionDTO;
import com.mac.bry.validationsystem.security.UserPermissionsCache;
import com.mac.bry.validationsystem.security.repository.UserPermissionRepository;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final UserRepository userRepository;
    private final UserPermissionRepository userPermissionRepository;

    @Transactional
    public UserPermission grantFullCompanyAccess(Long userId, Company company, Long grantedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserPermission permission = new UserPermission();
        permission.setUser(user);
        permission.setCompany(company);
        permission.setPermissionType(PermissionType.FULL_COMPANY);
        permission.setGrantedBy(grantedBy);

        UserPermission saved = userPermissionRepository.save(permission);

        rebuildUserPermissionsCache(userId);
        return saved;
    }

    @Transactional
    public UserPermission grantDepartmentAccess(Long userId, Department department, Long grantedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserPermission permission = new UserPermission();
        permission.setUser(user);
        permission.setCompany(department.getCompany());
        permission.setDepartment(department);
        permission.setPermissionType(PermissionType.FULL_DEPARTMENT);
        permission.setGrantedBy(grantedBy);

        UserPermission saved = userPermissionRepository.save(permission);
        rebuildUserPermissionsCache(userId);
        return saved;
    }

    @Transactional
    public UserPermission grantLaboratoryAccess(Long userId, Laboratory laboratory, Long grantedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserPermission permission = new UserPermission();
        permission.setUser(user);
        permission.setCompany(laboratory.getDepartment().getCompany());
        permission.setDepartment(laboratory.getDepartment());
        permission.setLaboratory(laboratory);
        permission.setPermissionType(PermissionType.SPECIFIC_LABORATORY);
        permission.setGrantedBy(grantedBy);

        UserPermission saved = userPermissionRepository.save(permission);
        rebuildUserPermissionsCache(userId);
        return saved;
    }

    @Transactional
    public void revokePermission(Long permissionId) {
        UserPermission permission = userPermissionRepository.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found"));
        Long userId = permission.getUser().getId();
        userPermissionRepository.delete(permission);
        rebuildUserPermissionsCache(userId);
    }

    /**
     * FIX #1: The core rebuilding logic. Rebuilds the user's permission cache from
     * DB.
     */
    @Transactional
    public void rebuildUserPermissionsCache(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return;

        List<UserPermission> permissions = userPermissionRepository.findByUserId(userId);

        UserPermissionsCache cache = new UserPermissionsCache();
        cache.setLastUpdated(LocalDateTime.now());

        for (UserPermission p : permissions) {
            Long companyId = p.getCompany().getId();

            // Dodaj do listy DTO dla widoku
            UserPermissionDTO dto = UserPermissionDTO.builder()
                    .permissionId(p.getId())
                    .type(p.getPermissionType())
                    .companyId(companyId)
                    .companyName(p.getCompany().getName())
                    .departmentId(p.getDepartment() != null ? p.getDepartment().getId() : null)
                    .departmentName(p.getDepartment() != null ? p.getDepartment().getName() : null)
                    .laboratoryId(p.getLaboratory() != null ? p.getLaboratory().getId() : null)
                    .laboratoryName(p.getLaboratory() != null ? p.getLaboratory().getFullName() : null)
                    .build();
            cache.getPermissions().add(dto);

            if (p.getPermissionType() == PermissionType.FULL_COMPANY) {
                cache.getAllowedCompanyIds().add(companyId);
                cache.getCompanyPermissionLevels().put(companyId, PermissionType.FULL_COMPANY);
            } else if (p.getPermissionType() == PermissionType.FULL_DEPARTMENT && p.getDepartment() != null) {
                Long deptId = p.getDepartment().getId();
                cache.getAllowedDepartmentIds().add(deptId);

                // Track max permission level per company
                cache.getCompanyPermissionLevels().merge(
                        companyId,
                        PermissionType.FULL_DEPARTMENT,
                        (existing, newValue) -> existing == PermissionType.FULL_COMPANY ? existing : newValue);
            } else if (p.getPermissionType() == PermissionType.SPECIFIC_LABORATORY && p.getLaboratory() != null) {
                cache.getAllowedLaboratoryIds().add(p.getLaboratory().getId());

                cache.getCompanyPermissionLevels().merge(
                        companyId,
                        PermissionType.SPECIFIC_LABORATORY,
                        (existing, newValue) -> existing != null ? existing : newValue);
            }
        }

        user.setPermissionsCache(cache);
        user.syncPermissionsCache(); // Konwertuje DTO na JSON i nadpisuje wartość w obiekcie User

        userRepository.save(user); // JPA Update
    }
}
