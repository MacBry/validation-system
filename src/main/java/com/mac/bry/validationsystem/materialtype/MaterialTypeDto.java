package com.mac.bry.validationsystem.materialtype;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialTypeDto {

    private Long id;

    @NotBlank(message = "Nazwa materiału jest wymagana")
    private String name;

    private String description;

    private Double minStorageTemp;

    private Double maxStorageTemp;

    private BigDecimal activationEnergy;

    private String standardSource;

    private String application;

    @Builder.Default
    private Boolean active = true;

    /**
     * Konwertuje encję na DTO
     */
    public static MaterialTypeDto fromEntity(MaterialType entity) {
        return MaterialTypeDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .minStorageTemp(entity.getMinStorageTemp())
                .maxStorageTemp(entity.getMaxStorageTemp())
                .activationEnergy(entity.getActivationEnergy())
                .standardSource(entity.getStandardSource())
                .application(entity.getApplication())
                .active(entity.getActive())
                .build();
    }

    /**
     * Konwertuje DTO na encję
     */
    public MaterialType toEntity() {
        return MaterialType.builder()
                .id(this.id)
                .name(this.name)
                .description(this.description)
                .minStorageTemp(this.minStorageTemp)
                .maxStorageTemp(this.maxStorageTemp)
                .activationEnergy(this.activationEnergy)
                .standardSource(this.standardSource)
                .application(this.application)
                .active(this.active != null ? this.active : true)
                .build();
    }
}
