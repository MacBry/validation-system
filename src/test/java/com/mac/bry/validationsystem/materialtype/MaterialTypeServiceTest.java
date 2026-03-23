package com.mac.bry.validationsystem.materialtype;

import com.mac.bry.validationsystem.company.Company;
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
class MaterialTypeServiceTest {

    @Mock
    private MaterialTypeRepository materialTypeRepository;

    @InjectMocks
    private MaterialTypeServiceImpl materialTypeService;

    private Company testCompany;
    private MaterialType globalMaterial;
    private MaterialType companyMaterial;

    @BeforeEach
    void setUp() {
        testCompany = new Company();
        testCompany.setId(1L);
        testCompany.setName("Test Co");

        globalMaterial = MaterialType.builder()
                .id(10L)
                .name("Global Material")
                .company(null)
                .active(true)
                .build();

        companyMaterial = MaterialType.builder()
                .id(11L)
                .name("Company Material")
                .company(testCompany)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Should return all materials (global + company specfic)")
    void shouldFindAll() {
        // Super admin case
        when(materialTypeRepository.findAllByOrderByNameAsc()).thenReturn(Arrays.asList(globalMaterial, companyMaterial));
        List<MaterialType> adminResult = materialTypeService.findAll(null);
        assertEquals(2, adminResult.size());

        // Limited company user
        when(materialTypeRepository.findAllByCompanyIdInOrCompanyIsNullOrderByNameAsc(any()))
                .thenReturn(Arrays.asList(globalMaterial, companyMaterial));
        List<MaterialType> userResult = materialTypeService.findAll(Set.of(1L));
        assertEquals(2, userResult.size());
    }

    @Test
    @DisplayName("Should check existence by name and company")
    void shouldCheckExistence() {
        when(materialTypeRepository.existsByNameAndCompanyIsNull("Global Material")).thenReturn(true);
        assertTrue(materialTypeService.existsByName("Global Material", null));

        when(materialTypeRepository.existsByNameAndCompanyId("Local", 1L)).thenReturn(false);
        when(materialTypeRepository.existsByNameAndCompanyIsNull("Local")).thenReturn(true);
        assertTrue(materialTypeService.existsByName("Local", 1L)); // Exists globally
    }

    @Test
    @DisplayName("Should save material")
    void shouldSave() {
        when(materialTypeRepository.save(any())).thenReturn(companyMaterial);
        MaterialType result = materialTypeService.save(companyMaterial);
        assertNotNull(result);
        verify(materialTypeRepository).save(companyMaterial);
    }
}
