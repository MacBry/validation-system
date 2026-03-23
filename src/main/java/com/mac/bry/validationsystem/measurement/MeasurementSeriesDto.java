package com.mac.bry.validationsystem.measurement;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DTO dla wyświetlania serii pomiarowej w widoku
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementSeriesDto {

    private Long id;
    private String recorderSerialNumber;
    private String originalFilename;
    private LocalDateTime firstMeasurementTime;
    private LocalDateTime lastMeasurementTime;
    private Double minTemperature;
    private Double maxTemperature;
    private Double avgTemperature;
    private Double mktTemperature;
    private Double stdDeviation;
    private Double variance;
    private Double cvPercentage;
    private Double medianTemperature;
    private Double expandedUncertainty;
    private Double percentile5;
    private Double percentile95;
    private Integer measurementIntervalMinutes;
    private Long totalTimeOutOfRangeMinutes;
    private Long totalTimeInRangeMinutes;
    private Integer violationCount;
    private Long maxViolationDurationMinutes;
    private Double trendCoefficient;
    private Double adjustedTrendCoefficient;
    private String driftClassification;
    private Integer spikeCount;
    private Double lowerLimit;
    private Double upperLimit;
    private RecorderPosition recorderPosition;
    private Boolean isReferenceRecorder;
    private Integer measurementCount;
    private LocalDateTime uploadDate;

    // Dane urządzenia chłodniczego
    private Long coolingDeviceId;
    private String deviceName;
    private String deviceInventoryNumber;
    private String deviceLaboratoryName;
    private String deviceLaboratoryAbbreviation;
    private String deviceChamberType;
    private String deviceStoredMaterial;
    private String deviceRpwNumber; // Format: "42/LZTHLA/2026"

    // Dane rejestratora TESTO
    private Long thermoRecorderId;
    private String recorderModel;
    private String recorderStatus;

    // Ostatnie wzorcowanie rejestratora
    private java.time.LocalDate calibrationDate;
    private String calibrationCertificateNumber;
    private java.time.LocalDate calibrationValidUntil;
    private Boolean calibrationIsValid;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Formatuje datę pierwszego pomiaru
     */
    public String getFormattedFirstMeasurementTime() {
        return firstMeasurementTime != null ? firstMeasurementTime.format(DATE_TIME_FORMATTER) : "-";
    }

    /**
     * Formatuje datę ostatniego pomiaru
     */
    public String getFormattedLastMeasurementTime() {
        return lastMeasurementTime != null ? lastMeasurementTime.format(DATE_TIME_FORMATTER) : "-";
    }

    /**
     * Formatuje datę przesłania
     */
    public String getFormattedUploadDate() {
        return uploadDate != null ? uploadDate.format(DATE_TIME_FORMATTER) : "-";
    }

    /**
     * Zwraca czas trwania serii pomiarowej
     */
    public String getDuration() {
        if (firstMeasurementTime == null || lastMeasurementTime == null) {
            return "-";
        }
        long hours = java.time.Duration.between(firstMeasurementTime, lastMeasurementTime).toHours();
        long days = hours / 24;
        long remainingHours = hours % 24;

        if (days > 0) {
            return String.format("%d dni, %d godz.", days, remainingHours);
        } else {
            return String.format("%d godz.", hours);
        }
    }

    /**
     * Formatuje zakres temperatur
     */
    public String getTemperatureRange() {
        if (minTemperature != null && maxTemperature != null) {
            return String.format("%.1f°C - %.1f°C", minTemperature, maxTemperature);
        }
        return "-";
    }

    /**
     * Zwraca procent czasu poza zakresem
     */
    public String getOutOfRangePercentage() {
        if (totalTimeOutOfRangeMinutes == null || totalTimeInRangeMinutes == null)
            return "-";
        long total = totalTimeOutOfRangeMinutes + totalTimeInRangeMinutes;
        if (total == 0)
            return "0.0%";
        return String.format("%.1f%%", (totalTimeOutOfRangeMinutes * 100.0) / total);
    }

    /**
     * Zwraca procent czasu w zakresie
     */
    public String getInRangePercentage() {
        if (totalTimeOutOfRangeMinutes == null || totalTimeInRangeMinutes == null)
            return "-";
        long total = totalTimeOutOfRangeMinutes + totalTimeInRangeMinutes;
        if (total == 0)
            return "100.0%";
        return String.format("%.1f%%", (totalTimeInRangeMinutes * 100.0) / total);
    }

    /**
     * Formatuje czas trwania (minuty -> H h M min)
     */
    private String formatMinutes(Long minutes) {
        if (minutes == null)
            return "-";
        long h = minutes / 60;
        long m = minutes % 60;
        if (h > 0)
            return String.format("%dh %dmin", h, m);
        return String.format("%dmin", m);
    }

    public String getFormattedOutOfRangeTime() {
        return formatMinutes(totalTimeOutOfRangeMinutes);
    }

    public String getFormattedInRangeTime() {
        return formatMinutes(totalTimeInRangeMinutes);
    }

    public String getFormattedMaxViolationDuration() {
        return formatMinutes(maxViolationDurationMinutes);
    }

    /**
     * Zwraca status stabilności temperatury
     */
    public String getStabilityStatus() {
        if (trendCoefficient == null)
            return "-";
        // Kryterium: |b| < 0.1°C/24h
        double trend24h = Math.abs(trendCoefficient * 24.0);
        return trend24h < 0.1 ? "STABILNE ✅" : "NIESTABILNE ❌";
    }

    /**
     * Zwraca sformatowany trend w °C/24h
     */
    public String getFormattedTrend() {
        if (trendCoefficient == null)
            return "-";
        return String.format("%.3f°C/24h", trendCoefficient * 24.0);
    }

    /**
     * Zwraca polską nazwę klasyfikacji trendu (Metoda A+)
     */
    public String getDriftClassificationLabel() {
        if (driftClassification == null)
            return "-";
        switch (driftClassification) {
            case "DRIFT":
                return "Rzeczywisty DRIFT ❌";
            case "SPIKE":
                return "Warunkowo STABILNE (SPIKE) ⚠️";
            case "MIXED":
                return "Mieszany (DRIFT+SPIKE) ❌";
            case "STABLE":
                return "STABILNE ✅";
            default:
                return driftClassification;
        }
    }

    /**
     * Zwraca kolor powiązany z klasyfikacją (Metoda A+)
     */
    public String getDriftClassificationColor() {
        if (driftClassification == null)
            return "#6b7280"; // Gray
        switch (driftClassification) {
            case "DRIFT":
            case "MIXED":
                return "#ef4444"; // Red
            case "SPIKE":
                return "#f59e0b"; // Amber
            case "STABLE":
                return "#10b981"; // Green
            default:
                return "#6b7280";
        }
    }

    public String getFormattedAdjustedTrend() {
        if (adjustedTrendCoefficient == null)
            return "-";
        return String.format("%.3f°C/24h", adjustedTrendCoefficient * 24.0);
    }

    /**
     * Formatuje datę wzorcowania
     */
    public String getFormattedCalibrationDate() {
        return calibrationDate != null ? calibrationDate.format(DATE_FORMATTER) : "-";
    }

    /**
     * Formatuje datę ważności wzorcowania
     */
    public String getFormattedCalibrationValidUntil() {
        return calibrationValidUntil != null ? calibrationValidUntil.format(DATE_FORMATTER) : "-";
    }

    /**
     * Tworzy DTO z encji
     */
    public static MeasurementSeriesDto fromEntity(MeasurementSeries series) {
        MeasurementSeriesDto dto = new MeasurementSeriesDto();
        dto.setId(series.getId());
        dto.setRecorderSerialNumber(series.getRecorderSerialNumber());
        dto.setOriginalFilename(series.getOriginalFilename());
        dto.setFirstMeasurementTime(series.getFirstMeasurementTime());
        dto.setLastMeasurementTime(series.getLastMeasurementTime());
        dto.setMinTemperature(series.getMinTemperature());
        dto.setMaxTemperature(series.getMaxTemperature());
        dto.setAvgTemperature(series.getAvgTemperature());
        dto.setMktTemperature(series.getMktTemperature());
        dto.setStdDeviation(series.getStdDeviation());
        dto.setVariance(series.getVariance());
        dto.setCvPercentage(series.getCvPercentage());
        dto.setMedianTemperature(series.getMedianTemperature());
        dto.setExpandedUncertainty(series.getExpandedUncertainty());
        dto.setPercentile5(series.getPercentile5());
        dto.setPercentile95(series.getPercentile95());
        dto.setMeasurementIntervalMinutes(series.getMeasurementIntervalMinutes());
        dto.setTotalTimeOutOfRangeMinutes(series.getTotalTimeOutOfRangeMinutes());
        dto.setTotalTimeInRangeMinutes(series.getTotalTimeInRangeMinutes());
        dto.setViolationCount(series.getViolationCount());
        dto.setMaxViolationDurationMinutes(series.getMaxViolationDurationMinutes());
        dto.setTrendCoefficient(series.getTrendCoefficient());
        dto.setAdjustedTrendCoefficient(series.getAdjustedTrendCoefficient());
        dto.setDriftClassification(series.getDriftClassification());
        dto.setSpikeCount(series.getSpikeCount());
        dto.setLowerLimit(series.getLowerLimit());
        dto.setUpperLimit(series.getUpperLimit());
        dto.setRecorderPosition(series.getRecorderPosition());
        dto.setIsReferenceRecorder(series.getIsReferenceRecorder());
        dto.setMeasurementCount(series.getMeasurementCount());
        dto.setUploadDate(series.getUploadDate());

        // Mapowanie danych urządzenia chłodniczego
        if (series.getCoolingDevice() != null) {
            var device = series.getCoolingDevice();
            dto.setCoolingDeviceId(device.getId());
            dto.setDeviceName(device.getName());
            dto.setDeviceInventoryNumber(device.getInventoryNumber());

            if (device.getLaboratory() != null) {
                dto.setDeviceLaboratoryName(device.getLaboratory().getFullName());
                dto.setDeviceLaboratoryAbbreviation(device.getLaboratory().getAbbreviation());
            } else {
                dto.setDeviceLaboratoryName("-");
                dto.setDeviceLaboratoryAbbreviation("-");
            }

            if (device.getChamberType() != null) {
                dto.setDeviceChamberType(device.getChamberType().getDisplayName());
            } else {
                dto.setDeviceChamberType("-");
            }

            dto.setDeviceStoredMaterial(device.getMaterialName());

            // Formatowanie RPW: numer/skrótPracowni/rok
            if (device.getValidationPlanNumbers() != null && !device.getValidationPlanNumbers().isEmpty()) {
                var latestRpw = device.getValidationPlanNumbers().get(
                        device.getValidationPlanNumbers().size() - 1);

                String labAbbr = (device.getLaboratory() != null) ? device.getLaboratory().getAbbreviation() : "???";

                dto.setDeviceRpwNumber(String.format("%d/%s/%d",
                        latestRpw.getPlanNumber(),
                        labAbbr,
                        latestRpw.getYear()));
            }
        }

        // Mapowanie danych rejestratora TESTO
        if (series.getThermoRecorder() != null) {
            var recorder = series.getThermoRecorder();
            dto.setThermoRecorderId(recorder.getId());
            dto.setRecorderModel(recorder.getModel());
            dto.setRecorderStatus(recorder.getStatus().getDisplayName());

            // Ostatnie wzorcowanie
            if (!recorder.getCalibrations().isEmpty()) {
                var latestCalibration = recorder.getCalibrations().stream()
                        .max((c1, c2) -> c1.getCalibrationDate().compareTo(c2.getCalibrationDate()))
                        .orElse(null);

                if (latestCalibration != null) {
                    dto.setCalibrationDate(latestCalibration.getCalibrationDate());
                    dto.setCalibrationCertificateNumber(latestCalibration.getCertificateNumber());
                    dto.setCalibrationValidUntil(latestCalibration.getValidUntil());
                    dto.setCalibrationIsValid(latestCalibration.isValid());
                }
            }
        }

        return dto;
    }
}
