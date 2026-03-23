package com.mac.bry.validationsystem.calibration;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalibrationPointDto {
    private Long id;
    private BigDecimal temperatureValue;
    private BigDecimal systematicError;
    private BigDecimal uncertainty;
}
