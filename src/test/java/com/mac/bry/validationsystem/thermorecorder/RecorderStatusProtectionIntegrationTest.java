package com.mac.bry.validationsystem.thermorecorder;

import com.mac.bry.validationsystem.ValidationSystemApplication;
import com.mac.bry.validationsystem.calibration.Calibration;
import com.mac.bry.validationsystem.calibration.CalibrationRepository;
import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.department.DepartmentRepository;
import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.device.CoolingDeviceRepository;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.MeasurementSeriesRepository;
import com.mac.bry.validationsystem.measurement.MeasurementSeriesService;
import com.mac.bry.validationsystem.measurement.RecorderPosition;
import com.mac.bry.validationsystem.measurement.Vi2FileDecoder;
import com.mac.bry.validationsystem.measurement.HtmlTestoFileDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ValidationSystemApplication.class)
@ActiveProfiles("test")
@Transactional
class RecorderStatusProtectionIntegrationTest {

    @Autowired
    private MeasurementSeriesService measurementSeriesService;

    @Autowired
    private MeasurementSeriesRepository measurementSeriesRepository;

    @Autowired
    private ThermoRecorderRepository thermoRecorderRepository;

    @Autowired
    private CoolingDeviceRepository coolingDeviceRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CalibrationRepository calibrationRepository;

    @MockBean
    private Vi2FileDecoder vi2Decoder;

    @MockBean
    private HtmlTestoFileDecoder htmlDecoder;

    private ThermoRecorder activeRecorder;
    private ThermoRecorder inactiveRecorder;
    private ThermoRecorder underCalibrationRecorder;
    private CoolingDevice testDevice;
    private Department testDept;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setName("Test Company");
        company = companyRepository.save(company);

        testDept = new Department();
        testDept.setName("Test Dept");
        testDept.setAbbreviation("TDEPT");
        testDept.setCompany(company);
        testDept = departmentRepository.save(testDept);

        testDevice = coolingDeviceRepository.save(CoolingDevice.builder()
                .inventoryNumber("DEV-001")
                .name("Test Device")
                .department(testDept)
                .chamberType(com.mac.bry.validationsystem.device.ChamberType.FRIDGE) // Added missing mandatory field
                .build());

        activeRecorder = thermoRecorderRepository.save(ThermoRecorder.builder()
                .serialNumber("REC-ACTIVE")
                .model("Testo 174T")
                .status(RecorderStatus.ACTIVE)
                .department(testDept)
                .build());

        inactiveRecorder = thermoRecorderRepository.save(ThermoRecorder.builder()
                .serialNumber("REC-INACTIVE")
                .model("Testo 174T")
                .status(RecorderStatus.INACTIVE)
                .department(testDept)
                .build());

        underCalibrationRecorder = thermoRecorderRepository.save(ThermoRecorder.builder()
                .serialNumber("REC-UNDER")
                .model("Testo 174T")
                .status(RecorderStatus.UNDER_CALIBRATION)
                .department(testDept)
                .build());
    }

    @Test
    @DisplayName("Should block upload for INACTIVE recorder")
    void shouldBlockUploadForInactiveRecorder() throws IOException {
        // Given
        MeasurementSeries mockSeries = new MeasurementSeries();
        mockSeries.setRecorderSerialNumber("REC-INACTIVE");
        when(vi2Decoder.parseVi2File(any(), any())).thenReturn(mockSeries);

        MockMultipartFile file = new MockMultipartFile("files", "test.vi2", "application/octet-stream", "dummy".getBytes());

        // When & Then
        IOException exception = assertThrows(IOException.class, () -> 
            measurementSeriesService.uploadVi2Files(new MockMultipartFile[]{file}, RecorderPosition.TOP_FRONT_LEFT, testDevice.getId(), false)
        );

        assertTrue(exception.getMessage().contains("jest nieaktywny"));
    }

    @Test
    @DisplayName("Should block upload for UNDER_CALIBRATION recorder")
    void shouldBlockUploadForUnderCalibrationRecorder() throws IOException {
        // Given
        MeasurementSeries mockSeries = new MeasurementSeries();
        mockSeries.setRecorderSerialNumber("REC-UNDER");
        when(vi2Decoder.parseVi2File(any(), any())).thenReturn(mockSeries);

        MockMultipartFile file = new MockMultipartFile("files", "test.vi2", "application/octet-stream", "dummy".getBytes());

        // When & Then
        IOException exception = assertThrows(IOException.class, () -> 
            measurementSeriesService.uploadVi2Files(new MockMultipartFile[]{file}, RecorderPosition.TOP_FRONT_LEFT, testDevice.getId(), false)
        );

        assertTrue(exception.getMessage().contains("Wysłano do wzorcowania"));
    }

    @Test
    @DisplayName("Should exclude INACTIVE and UNDER_CALIBRATION recorders from selection in wizard")
    void shouldExcludeInvalidRecordersFromRepositoryQueries() {
        // Given - create series for each recorder
        createSeriesForRecorder(activeRecorder, "S1");
        createSeriesForRecorder(inactiveRecorder, "S2");
        createSeriesForRecorder(underCalibrationRecorder, "S3");

        // When
        List<MeasurementSeries> activeSeries = measurementSeriesRepository.findByCoolingDeviceIdAndUsedInValidationFalseOrderByUploadDateDesc(testDevice.getId());
        List<MeasurementSeries> allAccessible = measurementSeriesRepository.findAllAccessible(true, null, null, null);

        // Then
        assertEquals(1, activeSeries.size(), "Only active recorder series should be visible");
        assertEquals("REC-ACTIVE", activeSeries.get(0).getThermoRecorder().getSerialNumber());

        // Check allAccessible (used in some UI parts)
        long nonActiveCount = allAccessible.stream()
                .filter(ms -> ms.getThermoRecorder().getStatus() != RecorderStatus.ACTIVE)
                .count();
        assertEquals(0, nonActiveCount, "Repository should only return series with ACTIVE recorders");
    }

    @Test
    @DisplayName("Should suppress notifications for recorders UNDER_CALIBRATION")
    void shouldSuppressNotificationsForUnderCalibration() {
        // Given - recorder with expired calibration
        activeRecorder.setStatus(RecorderStatus.UNDER_CALIBRATION);
        thermoRecorderRepository.save(activeRecorder);

        calibrationRepository.save(Calibration.builder()
                .thermoRecorder(activeRecorder)
                .calibrationDate(LocalDate.now().minusYears(2))
                .validUntil(LocalDate.now().minusMonths(1)) // Expired
                .certificateNumber("CERT-EXP")
                .build());

        // When
        var notifications = calibrationRepository.findLatestExpiringCalibrations(
                LocalDate.now().minusDays(1), 
                LocalDate.now().plusDays(30));

        // Then
        boolean found = notifications.stream()
                .anyMatch(c -> c.getThermoRecorder().getSerialNumber().equals("REC-ACTIVE"));
        assertFalse(found, "Notification should be suppressed for UNDER_CALIBRATION recorder");
    }

    private void createSeriesForRecorder(ThermoRecorder recorder, String filename) {
        MeasurementSeries series = new MeasurementSeries();
        series.setThermoRecorder(recorder);
        series.setCoolingDevice(testDevice);
        series.setOriginalFilename(filename);
        series.setUsedInValidation(false);
        series.setUploadDate(java.time.LocalDateTime.now());
        
        // MeasurementSeries requires some fields to be non-null for DB constraints
        series.setFirstMeasurementTime(java.time.LocalDateTime.now().minusHours(24));
        series.setLastMeasurementTime(java.time.LocalDateTime.now());
        series.setMinTemperature(2.0);
        series.setMaxTemperature(8.0);
        series.setAvgTemperature(5.0);
        series.setMeasurementCount(100);
        
        measurementSeriesRepository.save(series);
    }
}
