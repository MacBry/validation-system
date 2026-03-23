package com.mac.bry.validationsystem.thermorecorder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThermoRecorderDto {

    private Long id;

    @NotBlank(message = "Numer seryjny jest wymagany")
    private String serialNumber;

    @NotBlank(message = "Model jest wymagany")
    private String model;

    private RecorderStatus status; // Status ustawiany automatycznie
    
    /**
     * Dział - WYMAGANE
     * NOWE POLE!
     */
    @NotNull(message = "Dział jest wymagany")
    private Long departmentId;
    
    private String departmentName;
    
    private String departmentAbbreviation;
    
    /**
     * Pracownia - OPCJONALNE
     * NOWE POLE!
     */
    private Long laboratoryId;
    
    private String laboratoryName;
}
