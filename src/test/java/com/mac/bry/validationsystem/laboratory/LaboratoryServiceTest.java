package com.mac.bry.validationsystem.laboratory;

import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.department.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LaboratoryServiceTest {

    @Mock
    private LaboratoryRepository laboratoryRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private LaboratoryServiceImpl laboratoryService;

    private Department testDept;
    private Laboratory testLab;

    @BeforeEach
    void setUp() {
        testDept = new Department();
        testDept.setId(1L);
        testDept.setName("Test Dept");

        testLab = new Laboratory();
        testLab.setId(1L);
        testLab.setFullName("Test Laboratory");
        testLab.setAbbreviation("TL");
        testLab.setDepartment(testDept);
    }

    @Test
    @DisplayName("Should return allowed laboratories based on hierarchical rights")
    void shouldReturnAllowedLaboratories() {
        // Super Admin
        when(laboratoryRepository.findAll()).thenReturn(Collections.singletonList(testLab));
        List<Laboratory> adminResult = laboratoryService.getAllowedLaboratories(null, null, null);
        assertEquals(1, adminResult.size());

        // Regular user with limited access
        when(laboratoryRepository.findAllByIdInOrderByFullNameAsc(any())).thenReturn(Collections.singletonList(testLab));
        when(laboratoryRepository.findAllByDepartmentIdInOrderByFullNameAsc(any())).thenReturn(Collections.emptyList());
        when(laboratoryRepository.findAllByDepartmentCompanyIdInOrderByFullNameAsc(any())).thenReturn(Collections.emptyList());
        
        List<Laboratory> userResult = laboratoryService.getAllowedLaboratories(Set.of(1L), Set.of(1L), Set.of(1L));
        assertEquals(1, userResult.size());
    }

    @Test
    @DisplayName("Should get laboratories by department")
    void shouldGetByDepartment() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDept));
        when(laboratoryRepository.findByDepartmentOrderByFullNameAsc(testDept)).thenReturn(Collections.singletonList(testLab));

        List<Laboratory> result = laboratoryService.getLaboratoriesByDepartment(1L);

        assertEquals(1, result.size());
        assertEquals(testLab.getFullName(), result.get(0).getFullName());
    }

    @Test
    @DisplayName("Should delete laboratory by ID")
    void shouldDeleteById() {
        laboratoryService.deleteById(1L);
        verify(laboratoryRepository).deleteById(1L);
    }
}
