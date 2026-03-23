package com.mac.bry.validationsystem.device;

import jakarta.validation.constraints.Min;
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
public class CoolingDeviceDto {

    private Long id;

    @NotBlank(message = "Numer inwentarzowy jest wymagany")
    private String inventoryNumber;

    @NotBlank(message = "Nazwa urządzenia jest wymagana")
    private String name;

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
     * Niektóre działy nie mają pracowni
     */
    private Long laboratoryId;

    private String laboratoryName;

    @NotNull(message = "Typ komory jest wymagany")
    private ChamberType chamberType;

    /**
     * @deprecated Użyj materialTypeId
     */
    @Deprecated
    private StoredMaterial storedMaterial;

    @NotNull(message = "Typ materiału jest wymagany")
    private Long materialTypeId;

    private String materialTypeName; // Dla wyświetlania

    private Double minOperatingTemp;

    private Double maxOperatingTemp;

    /**
     * Objętość urządzenia w metrach sześciennych (PDA TR-64)
     */
    @Min(value = 0, message = "Objętość musi być większa od 0")
    private Double volume;

    /**
     * Klasyfikacja kubatury według PDA TR-64 i WHO
     */
    private VolumeCategory volumeCategory;

    // Pola dla dodawania nowego numeru RPW w formularzu
    @Min(value = 2000, message = "Rok nie może być wcześniejszy niż 2000")
    private Integer newPlanYear;

    @Min(value = 1, message = "Numer RPW musi być większy od 0")
    private Integer newPlanNumber;

    public static CoolingDeviceDto fromEntity(CoolingDevice device) {
        if (device == null) {
            return null;
        }

        return CoolingDeviceDto.builder()
                .id(device.getId())
                .inventoryNumber(device.getInventoryNumber())
                .name(device.getName())
                .departmentId(device.getDepartment() != null ? device.getDepartment().getId() : null)
                .departmentName(device.getDepartment() != null ? device.getDepartment().getName() : null)
                .departmentAbbreviation(
                        device.getDepartment() != null ? device.getDepartment().getAbbreviation() : null)
                .laboratoryId(device.getLaboratory() != null ? device.getLaboratory().getId() : null)
                .laboratoryName(device.getLaboratory() != null ? device.getLaboratory().getFullName() : null)
                .chamberType(device.getChamberType())
                .storedMaterial(device.getStoredMaterial())
                .materialTypeId(device.getMaterialType() != null ? device.getMaterialType().getId() : null)
                .materialTypeName(device.getMaterialType() != null ? device.getMaterialType().getName() : null)
                .minOperatingTemp(device.getMinOperatingTemp())
                .maxOperatingTemp(device.getMaxOperatingTemp())
                .volume(device.getVolume())
                .volumeCategory(device.getVolumeCategory())
                .build();
    }

    /**
     * Automatycznie ustawia klasę kubatury na podstawie objętości
     */
    public void updateVolumeCategoryFromVolume() {
        if (volume != null && volume > 0) {
            this.volumeCategory = VolumeCategory.fromVolume(volume);
        }
    }
}
