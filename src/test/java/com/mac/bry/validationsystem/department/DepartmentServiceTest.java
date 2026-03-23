package com.mac.bry.validationsystem.department;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import com.mac.bry.validationsystem.device.CoolingDeviceRepository;
import com.mac.bry.validationsystem.laboratory.LaboratoryRepository;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorderRepository;
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
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CoolingDeviceRepository deviceRepository;
    @Mock
    private ThermoRecorderRepository recorderRepository;
    @Mock
    private LaboratoryRepository laboratoryRepository;

    @InjectMocks
    private DepartmentServiceImpl departmentService;

    private Company testCompany;
    private Department testDept;

    @BeforeEach
    void setUp() {
        testCompany = new Company();
        testCompany.setId(1L);
        testCompany.setName("Test Co");

        testDept = new Department();
        testDept.setId(1L);
        testDept.setName("Test Dept");
        testDept.setAbbreviation("TD");
        testDept.setCompany(testCompany);
    }

    @Test
    @DisplayName("Should return allowed departments based on user rights")
    void shouldReturnAllowedDepartments() {
        // Super Admin case
        when(departmentRepository.findAllByOrderByNameAsc()).thenReturn(Collections.singletonList(testDept));
        List<Department> adminResult = departmentService.getAllowedDepartments(null, null);
        assertEquals(1, adminResult.size());

        // Limited user case
        when(departmentRepository.findAllByIdInOrderByNameAsc(any())).thenReturn(Collections.singletonList(testDept));
        when(departmentRepository.findAllByCompanyIdInOrderByNameAsc(any())).thenReturn(Collections.emptyList());
        List<Department> userResult = departmentService.getAllowedDepartments(Set.of(1L), Set.of(1L));
        assertEquals(1, userResult.size());
    }

    @Test
    @DisplayName("Should create department successfully")
    void shouldCreateDepartment() {
        // Given
        DepartmentDto dto = new DepartmentDto(null, 1L, "Test Co", "New", "NW", "Desc", true, 0);
        when(departmentRepository.findByAbbreviation("NW")).thenReturn(Optional.empty());
        when(companyRepository.findById(1L)).thenReturn(Optional.of(testCompany));
        when(departmentRepository.save(any(Department.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Department result = departmentService.createDepartment(dto);

        // Then
        assertNotNull(result);
        assertEquals("NW", result.getAbbreviation());
        verify(departmentRepository).save(any(Department.class));
    }

    @Test
    @DisplayName("Should throw exception if abbreviation already exists")
    void shouldThrowWhenAbbreviationExists() {
        DepartmentDto dto = new DepartmentDto(null, 1L, "Test Co", "New", "TD", "Desc", true, 0);
        when(departmentRepository.findByAbbreviation("TD")).thenReturn(Optional.of(testDept));

        assertThrows(IllegalArgumentException.class, () -> departmentService.createDepartment(dto));
    }

    @Test
    @DisplayName("Should update department")
    void shouldUpdateDepartment() {
        DepartmentDto dto = new DepartmentDto(1L, 1L, "Test Co", "Updated", "TD", "New Desc", false, 0);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDept));
        when(departmentRepository.save(any(Department.class))).thenAnswer(i -> i.getArgument(0));

        Department result = departmentService.updateDepartment(1L, dto);

        assertEquals("Updated", result.getName());
        assertFalse(result.getHasLaboratories());
    }

    @Test
    @DisplayName("Should block deletion if departments has children")
    void shouldBlockDeletionWithChildren() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDept));
        
        // Mocking devices exist
        when(deviceRepository.findByDepartment(testDept)).thenReturn(Collections.singletonList(mock(com.mac.bry.validationsystem.device.CoolingDevice.class)));
        
        assertThrows(IllegalStateException.class, () -> departmentService.deleteDepartment(1L));
        
        // Mocking no devices but recorders exist
        when(deviceRepository.findByDepartment(testDept)).thenReturn(Collections.emptyList());
        when(recorderRepository.findByDepartment(testDept)).thenReturn(Collections.singletonList(mock(com.mac.bry.validationsystem.thermorecorder.ThermoRecorder.class)));
        
        assertThrows(IllegalStateException.class, () -> departmentService.deleteDepartment(1L));
    }
}
