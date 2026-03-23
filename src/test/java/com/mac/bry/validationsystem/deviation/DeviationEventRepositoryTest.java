package com.mac.bry.validationsystem.deviation;

import com.mac.bry.validationsystem.device.ChamberType;
import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class DeviationEventRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DeviationEventRepository repository;

    private MeasurementSeries series;
    private Validation validation;

    @BeforeEach
    void setUp() {
        com.mac.bry.validationsystem.company.Company company = new com.mac.bry.validationsystem.company.Company();
        company.setName("Test Company");
        entityManager.persist(company);

        com.mac.bry.validationsystem.department.Department department = new com.mac.bry.validationsystem.department.Department();
        department.setName("Test Dept");
        department.setAbbreviation("TD");
        department.setCompany(company);
        entityManager.persist(department);

        CoolingDevice device = new CoolingDevice();
        device.setInventoryNumber("INV-123");
        device.setName("Test Device");
        device.setChamberType(ChamberType.FRIDGE);
        device.setDepartment(department);
        entityManager.persist(device);

        series = new MeasurementSeries();
        series.setOriginalFilename("test.vi2");
        series.setFirstMeasurementTime(LocalDateTime.now());
        series.setLastMeasurementTime(LocalDateTime.now().plusHours(1));
        series.setMinTemperature(5.0);
        series.setMaxTemperature(10.0);
        series.setAvgTemperature(7.0);
        series.setMeasurementCount(100);
        series.setUploadDate(LocalDateTime.now());
        series.setCoolingDevice(device);
        entityManager.persist(series);

        validation = new Validation();
        validation.setCoolingDevice(device);
        validation.setCreatedDate(LocalDateTime.now());
        validation.setStatus(ValidationStatus.DRAFT);
        entityManager.persist(validation);
        
        entityManager.flush();
    }

    @Test
    @DisplayName("Should find by validation ID and order by severity then start time")
    void shouldFindAndOrder() {
        // Given
        DeviationEvent e1 = DeviationEvent.builder()
                .validation(validation)
                .measurementSeries(series)
                .severity(DeviationSeverity.MINOR)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now())
                .durationMinutes(60L)
                .peakTemperature(10.0)
                .violatedLimit(8.0)
                .violationType(ViolationType.ABOVE_UPPER)
                .build();
        
        DeviationEvent e2 = DeviationEvent.builder()
                .validation(validation)
                .measurementSeries(series)
                .severity(DeviationSeverity.CRITICAL)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusHours(1))
                .durationMinutes(60L)
                .peakTemperature(10.0)
                .violatedLimit(8.0)
                .violationType(ViolationType.ABOVE_UPPER)
                .build();

        entityManager.persist(e1);
        entityManager.persist(e2);
        entityManager.flush();

        // When
        List<DeviationEvent> result = repository.findByValidationIdOrderBySeverityAscStartTimeAsc(validation.getId());

        // Then
        assertEquals(2, result.size());
        assertEquals(DeviationSeverity.CRITICAL, result.get(0).getSeverity()); // Binary order: CRITICAL < MAJOR < MINOR
        assertEquals(DeviationSeverity.MINOR, result.get(1).getSeverity());
    }

    @Test
    @DisplayName("Should count violations for specific validation")
    void shouldCountViolations() {
        DeviationEvent e1 = DeviationEvent.builder()
                .validation(validation)
                .measurementSeries(series)
                .severity(DeviationSeverity.MAJOR)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusMinutes(30))
                .durationMinutes(30L)
                .peakTemperature(10.0)
                .violatedLimit(8.0)
                .violationType(ViolationType.ABOVE_UPPER)
                .build();
        
        entityManager.persist(e1);
        entityManager.flush();

        assertEquals(1, repository.countByValidationId(validation.getId()));
        assertEquals(1, repository.countByValidationIdAndSeverity(validation.getId(), DeviationSeverity.MAJOR));
        assertEquals(0, repository.countByValidationIdAndSeverity(validation.getId(), DeviationSeverity.CRITICAL));
    }
}
