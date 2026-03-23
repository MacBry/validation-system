package com.mac.bry.validationsystem.company;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private CompanyServiceImpl companyService;

    private Company testCompany;

    @BeforeEach
    void setUp() {
        testCompany = new Company();
        testCompany.setId(1L);
        testCompany.setName("Test Company");
        testCompany.setAddress("Test Address");
    }

    @Test
    @DisplayName("Should return main company (first found)")
    void shouldReturnMainCompany() {
        // Given
        when(companyRepository.findAll()).thenReturn(Collections.singletonList(testCompany));

        // When
        Company result = companyService.getMainCompany();

        // Then
        assertNotNull(result);
        assertEquals(testCompany.getId(), result.getId());
        verify(companyRepository).findAll();
    }

    @Test
    @DisplayName("Should throw error when no main company exists")
    void shouldThrowErrorWhenNoMainCompany() {
        // Given
        when(companyRepository.findAll()).thenReturn(Collections.emptyList());

        // When & Then
        assertThrows(IllegalStateException.class, () -> companyService.getMainCompany());
    }

    @Test
    @DisplayName("Should return all companies")
    void shouldReturnAllCompanies() {
        // Given
        when(companyRepository.findAll()).thenReturn(Arrays.asList(testCompany));

        // When
        List<Company> result = companyService.getAllCompanies();

        // Then
        assertEquals(1, result.size());
        assertEquals(testCompany.getName(), result.get(0).getName());
    }

    @Test
    @DisplayName("Should return allowed companies")
    void shouldReturnAllowedCompanies() {
        // Test null allowedIds
        when(companyRepository.findAll()).thenReturn(Arrays.asList(testCompany));
        List<Company> result1 = companyService.getAllowedCompanies(null);
        assertEquals(1, result1.size());

        // Test specific IDs
        when(companyRepository.findAllById(any())).thenReturn(Arrays.asList(testCompany));
        List<Company> result2 = companyService.getAllowedCompanies(Set.of(1L));
        assertEquals(1, result2.size());
    }

    @Test
    @DisplayName("Should get company by ID")
    void shouldGetCompanyById() {
        // Given
        when(companyRepository.findById(1L)).thenReturn(Optional.of(testCompany));

        // When
        Company result = companyService.getCompanyById(1L);

        // Then
        assertEquals(testCompany.getName(), result.getName());
    }

    @Test
    @DisplayName("Should create company")
    void shouldCreateCompany() {
        // Given
        when(companyRepository.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Company result = companyService.createCompany("New", "Address");

        // Then
        assertEquals("New", result.getName());
        assertEquals("Address", result.getAddress());
        verify(companyRepository).save(any(Company.class));
    }

    @Test
    @DisplayName("Should update company")
    void shouldUpdateCompany() {
        // Given
        when(companyRepository.findById(1L)).thenReturn(Optional.of(testCompany));
        when(companyRepository.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Company result = companyService.updateCompany(1L, "Updated", "New Address");

        // Then
        assertEquals("Updated", result.getName());
        assertEquals("New Address", result.getAddress());
    }

    @Test
    @DisplayName("Should delete company")
    void shouldDeleteCompany() {
        // Given
        when(companyRepository.findById(1L)).thenReturn(Optional.of(testCompany));

        // When
        companyService.deleteCompany(1L);

        // Then
        verify(companyRepository).delete(testCompany);
    }
}
