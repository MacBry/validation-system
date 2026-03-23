package com.mac.bry.validationsystem.device;

import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.MeasurementSeriesRepository;
import com.mac.bry.validationsystem.security.service.SecurityService;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationRepository;
import com.mac.bry.validationsystem.validation.ValidationStatus;
import com.mac.bry.validationsystem.validationplan.ValidationPlanNumber;
import com.mac.bry.validationsystem.validationplan.ValidationPlanNumberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoolingDeviceServiceTest {

    @Mock
    private CoolingDeviceRepository coolingDeviceRepository;
    @Mock
    private ValidationPlanNumberRepository validationPlanNumberRepository;
    @Mock
    private ValidationRepository validationRepository;
    @Mock
    private MeasurementSeriesRepository measurementSeriesRepository;
    @Mock
    private SecurityService securityService;

    @InjectMocks
    private CoolingDeviceServiceImpl coolingDeviceService;

    private CoolingDevice device;

    @BeforeEach
    void setUp() {
        device = new CoolingDevice();
        device.setId(1L);
        device.setInventoryNumber("DEV-001");
        device.setName("Test Device");
    }

    @Test
    @DisplayName("Should return accessible devices for super admin")
    void shouldReturnAccessibleDevicesForSuperAdmin() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<CoolingDevice> page = new PageImpl<>(List.of(device));
        
        when(securityService.isSuperAdmin()).thenReturn(true);
        when(coolingDeviceRepository.findAllAccessible(eq(true), any(), any(), any(), any())).thenReturn(page);

        // When
        Page<CoolingDevice> result = coolingDeviceService.getAllAccessibleDevices(pageable);

        // Then
        assertEquals(1, result.getContent().size());
        verify(coolingDeviceRepository).findAllAccessible(eq(true), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should return accessible devices for regular user")
    void shouldReturnAccessibleDevicesForRegularUser() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        when(securityService.isSuperAdmin()).thenReturn(false);
        when(securityService.getAllowedCompanyIds()).thenReturn(Set.of(1L));
        when(securityService.getDepartmentIdsWithImplicitAccess()).thenReturn(Set.of(2L));
        when(securityService.getAllowedLaboratoryIds()).thenReturn(Set.of(3L));
        
        when(coolingDeviceRepository.findAllAccessible(eq(false), anyCollection(), anyCollection(), anyCollection(), any()))
                .thenReturn(Page.empty());

        // When
        coolingDeviceService.getAllAccessibleDevices(pageable);

        // Then
        verify(coolingDeviceRepository).findAllAccessible(eq(false), eq(Set.of(1L)), eq(Set.of(2L)), eq(Set.of(3L)), any());
    }

    @Test
    @DisplayName("Should add validation plan number successfully")
    void shouldAddValidationPlanNumberSuccessfully() {
        // Given
        when(coolingDeviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(validationPlanNumberRepository.existsByCoolingDeviceAndYear(device, 2024)).thenReturn(false);
        when(coolingDeviceRepository.save(device)).thenReturn(device);

        // When
        CoolingDevice result = coolingDeviceService.addValidationPlanNumber(1L, 2024, 123);

        // Then
        assertNotNull(result);
        verify(coolingDeviceRepository).save(device);
    }

    @Test
    @DisplayName("Should throw exception when adding plan for existing year")
    void shouldThrowExceptionWhenAddingPlanForExistingYear() {
        // Given
        when(coolingDeviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(validationPlanNumberRepository.existsByCoolingDeviceAndYear(device, 2024)).thenReturn(true);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            coolingDeviceService.addValidationPlanNumber(1L, 2024, 123)
        );
    }

    @Test
    @DisplayName("Should return status NO_VALIDATION when no validation exists")
    void shouldReturnNoValidationStatus() {
        // Given
        when(validationRepository.findLatestByDeviceId(1L)).thenReturn(null);

        // When
        DeviceValidationStatus status = coolingDeviceService.getValidationStatus(1L);

        // Then
        assertFalse(status.isHasValidValidation());
        assertEquals("NO_VALIDATION", status.getInvalidReason());
    }

    @Test
    @DisplayName("Should return status EXPIRED if approved validation is older than 1 year from latest measurement")
    void shouldReturnExpiredStatus() {
        // Given
        Validation latestValidation = new Validation();
        latestValidation.setStatus(ValidationStatus.APPROVED);
        latestValidation.setCreatedDate(LocalDateTime.now().minusDays(400));
        
        MeasurementSeries series = new MeasurementSeries();
        series.setLastMeasurementTime(LocalDateTime.now()); // measurement 400 days after validation

        when(validationRepository.findLatestByDeviceId(1L)).thenReturn(latestValidation);
        when(measurementSeriesRepository.findTopByCoolingDeviceIdOrderByLastMeasurementTimeDesc(1L))
                .thenReturn(Optional.of(series));

        // When
        DeviceValidationStatus status = coolingDeviceService.getValidationStatus(1L);

        // Then
        assertFalse(status.isHasValidValidation());
        assertEquals("EXPIRED", status.getInvalidReason());
    }

    @Test
    @DisplayName("Should return status VALID if approved validation is recent")
    void shouldReturnValidStatus() {
        // Given
        Validation latestValidation = new Validation();
        latestValidation.setStatus(ValidationStatus.APPROVED);
        latestValidation.setCreatedDate(LocalDateTime.now().minusDays(100));
        
        MeasurementSeries series = new MeasurementSeries();
        series.setLastMeasurementTime(LocalDateTime.now()); // only 100 days diff

        when(validationRepository.findLatestByDeviceId(1L)).thenReturn(latestValidation);
        when(measurementSeriesRepository.findTopByCoolingDeviceIdOrderByLastMeasurementTimeDesc(1L))
                .thenReturn(Optional.of(series));

        // When
        DeviceValidationStatus status = coolingDeviceService.getValidationStatus(1L);

        // Then
        assertTrue(status.isHasValidValidation());
        assertNull(status.getInvalidReason());
    }

    @Test
    @DisplayName("Should return invalid reason for DRAFT validation")
    void shouldReturnDraftReason() {
        // Given
        Validation latestValidation = new Validation();
        latestValidation.setStatus(ValidationStatus.DRAFT);
        latestValidation.setCreatedDate(LocalDateTime.now());
        
        MeasurementSeries series = new MeasurementSeries();
        series.setLastMeasurementTime(LocalDateTime.now());

        when(validationRepository.findLatestByDeviceId(1L)).thenReturn(latestValidation);
        when(measurementSeriesRepository.findTopByCoolingDeviceIdOrderByLastMeasurementTimeDesc(1L))
                .thenReturn(Optional.of(series));

        // When
        DeviceValidationStatus status = coolingDeviceService.getValidationStatus(1L);

        // Then
        assertFalse(status.isHasValidValidation());
        assertEquals("DRAFT", status.getInvalidReason());
    }
}
