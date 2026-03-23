package com.mac.bry.validationsystem.calibration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalibrationDto {

    private Long id;

    @NotNull(message = "Data wzorcowania jest wymagana")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate calibrationDate;

    @NotBlank(message = "Numer świadectwa wzorcowania jest wymagany")
    private String certificateNumber;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate validUntil;

    private String certificateFilePath;

    private Long thermoRecorderId;

    private List<CalibrationPointDto> points;
}
