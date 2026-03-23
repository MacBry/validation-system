package com.mac.bry.validationsystem.security.service;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.laboratory.Laboratory;
import com.mac.bry.validationsystem.security.PermissionType;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.UserPermission;
import com.mac.bry.validationsystem.security.repository.UserPermissionRepository;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @InjectMocks
    private PermissionService permissionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPermissionRepository userPermissionRepository;

    private User testUser;
    private Company testCompany;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("tester");

        testCompany = new Company();
        testCompany.setId(10L);
    }

    @Test
    void grantFullCompanyAccessAndRebuildCache() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userPermissionRepository.save(any(UserPermission.class))).thenAnswer(i -> {
            UserPermission up = i.getArgument(0);
            up.setId(100L);
            return up;
        });

        // Setup mock for rebuildUserPermissionsCache
        UserPermission mockPerm = new UserPermission();
        mockPerm.setCompany(testCompany);
        mockPerm.setPermissionType(PermissionType.FULL_COMPANY);
        mockPerm.setUser(testUser);

        when(userPermissionRepository.findByUserId(1L)).thenReturn(List.of(mockPerm));

        // Act
        permissionService.grantFullCompanyAccess(1L, testCompany, 2L);

        // Assert
        verify(userPermissionRepository, times(1)).save(any(UserPermission.class));
        verify(userRepository, times(2)).findById(1L); // Raz dla grant, raz dla rebuild

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertNotNull(savedUser.getPermissionsCache());
        assertTrue(savedUser.getPermissionsCache().getAllowedCompanyIds().contains(10L));
        assertNotNull(savedUser.getPermissionsCacheJson()); // Potwierdzenie że wykonano syncPermissionsCache
    }

    @Test
    void shouldGrantDepartmentAccessAndRebuildCache() {
        Department testDepartment = new Department();
        testDepartment.setId(20L);
        testDepartment.setCompany(testCompany);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userPermissionRepository.save(any(UserPermission.class))).thenAnswer(i -> {
            UserPermission up = i.getArgument(0);
            up.setId(101L);
            return up;
        });

        UserPermission mockPerm = new UserPermission();
        mockPerm.setCompany(testCompany);
        mockPerm.setDepartment(testDepartment);
        mockPerm.setPermissionType(PermissionType.FULL_DEPARTMENT);
        mockPerm.setUser(testUser);

        when(userPermissionRepository.findByUserId(1L)).thenReturn(List.of(mockPerm));

        permissionService.grantDepartmentAccess(1L, testDepartment, 2L);

        verify(userPermissionRepository, times(1)).save(any(UserPermission.class));
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertTrue(savedUser.getPermissionsCache().getAllowedDepartmentIds().contains(20L));
    }

    @Test
    void shouldGrantLaboratoryAccessAndRebuildCache() {
        Department testDepartment = new Department();
        testDepartment.setId(20L);
        testDepartment.setCompany(testCompany);

        Laboratory testLab = new Laboratory();
        testLab.setId(30L);
        testLab.setDepartment(testDepartment);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userPermissionRepository.save(any(UserPermission.class))).thenAnswer(i -> {
            UserPermission up = i.getArgument(0);
            up.setId(102L);
            return up;
        });

        UserPermission mockPerm = new UserPermission();
        mockPerm.setCompany(testCompany);
        mockPerm.setDepartment(testDepartment);
        mockPerm.setLaboratory(testLab);
        mockPerm.setPermissionType(PermissionType.SPECIFIC_LABORATORY);
        mockPerm.setUser(testUser);

        when(userPermissionRepository.findByUserId(1L)).thenReturn(List.of(mockPerm));

        permissionService.grantLaboratoryAccess(1L, testLab, 2L);

        verify(userPermissionRepository, times(1)).save(any(UserPermission.class));
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertTrue(savedUser.getPermissionsCache().getAllowedLaboratoryIds().contains(30L));
    }

    @Test
    void shouldRevokePermission() {
        UserPermission mockPerm = new UserPermission();
        mockPerm.setId(200L);
        mockPerm.setUser(testUser);

        when(userPermissionRepository.findById(200L)).thenReturn(Optional.of(mockPerm));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userPermissionRepository.findByUserId(1L)).thenReturn(List.of()); // No permissions after delete

        permissionService.revokePermission(200L);

        verify(userPermissionRepository, times(1)).delete(mockPerm);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertTrue(savedUser.getPermissionsCache().getAllowedCompanyIds().isEmpty());
    }
}
