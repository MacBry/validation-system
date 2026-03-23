package com.mac.bry.validationsystem.deviation;

import com.mac.bry.validationsystem.measurement.MeasurementPoint;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviationDetectionServiceTest {

    @Mock
    private DeviationEventRepository deviationEventRepository;
    @Mock
    private ValidationRepository validationRepository;

    @InjectMocks
    private DeviationDetectionService deviationDetectionService;

    private Validation testValidation;
    private MeasurementSeries gridSeries;
    private MeasurementSeries referenceSeries;

    @BeforeEach
    void setUp() {
        testValidation = new Validation();
        testValidation.setId(1L);

        gridSeries = new MeasurementSeries();
        gridSeries.setId(10L);
        gridSeries.setIsReferenceRecorder(false);
        gridSeries.setLowerLimit(2.0);
        gridSeries.setUpperLimit(8.0);

        referenceSeries = new MeasurementSeries();
        referenceSeries.setId(11L);
        referenceSeries.setIsReferenceRecorder(true);

        testValidation.setMeasurementSeries(Arrays.asList(gridSeries, referenceSeries));
    }

    @Test
    @DisplayName("Should detect violations and save events")
    void shouldDetectAndSave() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        List<MeasurementPoint> points = new ArrayList<>();
        points.add(new MeasurementPoint(now, 5.0));        // OK
        points.add(new MeasurementPoint(now.plusMinutes(1), 9.0)); // ABOVE (start)
        points.add(new MeasurementPoint(now.plusMinutes(2), 10.0));// ABOVE (peak)
        points.add(new MeasurementPoint(now.plusMinutes(10), 5.0));// OK (end)
        
        gridSeries.setMeasurementPoints(points);
        when(validationRepository.findById(1L)).thenReturn(Optional.of(testValidation));

        // When
        List<DeviationEvent> result = deviationDetectionService.detectAndSave(1L);

        // Then
        assertEquals(1, result.size());
        DeviationEvent event = result.get(0);
        assertEquals(ViolationType.ABOVE_UPPER, event.getViolationType());
        assertEquals(10.0, event.getPeakTemperature());
        assertEquals(8.0, event.getViolatedLimit());
        assertEquals(9, event.getDurationMinutes()); // 1 to 10 is 9 mins (exclusive of end point that is OK)
        
        verify(deviationEventRepository).deleteByValidationId(1L);
        verify(deviationEventRepository).saveAll(any());
    }

    @Test
    @DisplayName("Should detect multiple violations in one series")
    void shouldDetectMultipleViolations() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        List<MeasurementPoint> points = new ArrayList<>();
        points.add(new MeasurementPoint(now, 1.0));               // BELOW
        points.add(new MeasurementPoint(now.plusMinutes(1), 5.0)); // OK
        points.add(new MeasurementPoint(now.plusMinutes(2), 9.0)); // ABOVE
        points.add(new MeasurementPoint(now.plusMinutes(3), 5.0)); // OK
        
        gridSeries.setMeasurementPoints(points);
        when(validationRepository.findById(1L)).thenReturn(Optional.of(testValidation));

        // When
        List<DeviationEvent> result = deviationDetectionService.detectAndSave(1L);

        // Then
        assertEquals(2, result.size());
        assertEquals(ViolationType.BELOW_LOWER, result.get(0).getViolationType());
        assertEquals(ViolationType.ABOVE_UPPER, result.get(1).getViolationType());
    }

    @Test
    @DisplayName("Should skip reference recorders and series without limits")
    void shouldSkipReferenceAndNoLimits() {
        // Given
        gridSeries.setLowerLimit(null);
        gridSeries.setUpperLimit(null);
        when(validationRepository.findById(1L)).thenReturn(Optional.of(testValidation));

        // When
        List<DeviationEvent> result = deviationDetectionService.detectAndSave(1L);

        // Then
        assertTrue(result.isEmpty());
        verify(deviationEventRepository, never()).saveAll(any());
    }
}
