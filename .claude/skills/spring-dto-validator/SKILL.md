---
name: spring-dto-validator
description: Generate and validate DTOs with Jakarta validation following validation-system patterns
context: fork
agent: spring-boot-backend-dev
disable-model-invocation: false
allowed-tools: Read, Write, Bash(mvn clean compile)
---

# Spring DTO Validator

Generate **Data Transfer Objects (DTO)** with comprehensive validation following validation-system v2.11.0 patterns.

## DTO Architecture

### 1. DTO Class (`XxxDto.java`)

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoolingDeviceDto {

    private Long id;

    @NotBlank(message = "Numer inwentarzowy jest wymagany")
    @Size(max = 50, message = "Numer nie może przekraczać 50 znaków")
    private String inventoryNumber;

    @NotBlank(message = "Nazwa urządzenia jest wymagana")
    @Size(min = 3, max = 100, message = "Nazwa: 3-100 znaków")
    private String name;

    @NotNull(message = "Dział jest wymagany")
    private Long departmentId;

    private Long laboratoryId;  // Optional

    @NotNull(message = "Typ komory jest wymagany")
    @Enumerated(EnumType.STRING)
    private ChamberType chamberType;

    @NotNull(message = "Typ materiału jest wymagany")
    private Long materialTypeId;

    @DecimalMin(value = "0.1", message = "Objętość musi być > 0")
    @DecimalMax(value = "1000", message = "Objętość musi być < 1000 m³")
    private Double volume;

    private VolumeCategory volumeCategory;

    // ========== CONVERSION METHODS ==========

    public static CoolingDeviceDto fromEntity(CoolingDevice device) {
        if (device == null) return null;

        return CoolingDeviceDto.builder()
            .id(device.getId())
            .inventoryNumber(device.getInventoryNumber())
            .name(device.getName())
            .departmentId(device.getDepartment() != null ? device.getDepartment().getId() : null)
            .laboratoryId(device.getLaboratory() != null ? device.getLaboratory().getId() : null)
            .chamberType(device.getChamberType())
            .materialTypeId(device.getMaterialType() != null ? device.getMaterialType().getId() : null)
            .volume(device.getVolume())
            .volumeCategory(device.getVolumeCategory())
            .build();
    }

    public CoolingDevice toEntity() {
        return CoolingDevice.builder()
            .id(this.id)
            .inventoryNumber(this.inventoryNumber)
            .name(this.name)
            // ... map other fields
            .build();
    }

    // ========== BUSINESS LOGIC ==========

    public void updateVolumeCategoryFromVolume() {
        if (volume != null && volume > 0) {
            this.volumeCategory = VolumeCategory.fromVolume(volume);
        }
    }
}
```

## Validation Annotations (Jakarta)

| Annotation | Purpose | Example |
|---|---|---|
| `@NotBlank` | String not null/empty | Name, description |
| `@NotNull` | Not null | Required IDs, enums |
| `@NotEmpty` | Collection not empty | List of items |
| `@Size` | String/Collection size | `@Size(min=3, max=100)` |
| `@DecimalMin/Max` | Numeric range | Temperatures, volume |
| `@Min/Max` | Integer range | Year, count |
| `@Email` | Email format | Email fields |
| `@Pattern` | Regex pattern | `@Pattern(regexp="^[A-Z0-9]$")` |
| `@Future` | Date is future | Expiry dates |
| `@Past` | Date is past | Birth dates |

## Usage in Controller

```java
@PostMapping("/devices")
public String saveDevice(
    @Valid @ModelAttribute CoolingDeviceDto dto,  // ← @Valid triggers validation
    BindingResult result,
    RedirectAttributes attrs) {

    if (result.hasErrors()) {
        // Validation failed - form shows errors
        return "device/form";  // Re-render with error messages
    }

    // Validation passed
    CoolingDevice device = dto.toEntity();
    service.save(device);
    attrs.addFlashAttribute("successMessage", "Urządzenie zapisane!");
    return "redirect:/devices";
}
```

## Usage in Thymeleaf

```html
<!-- Form with DTO binding -->
<form th:object="${deviceDto}" method="POST" th:action="@{/devices}">

    <!-- Input with error display -->
    <input type="text" th:field="*{inventoryNumber}" class="vcc-form-input">
    <span th:if="${#fields.hasErrors('inventoryNumber')}"
          th:errors="*{inventoryNumber}" class="vcc-form-error">
    </span>

    <!-- Select with options -->
    <select th:field="*{chamberType}" class="vcc-form-select">
        <option th:each="type : ${T(com.mac.bry.validationsystem.device.ChamberType).values()}"
                th:value="${type}" th:text="${type.displayName}">
        </option>
    </select>

    <button type="submit" class="vcc-btn vcc-btn--primary">Zapisz</button>
</form>
```

## Steps

1. **Read** existing DTO pattern from `src/main/java/com/mac/bry/validationsystem/device/CoolingDeviceDto.java`
2. **Analyze** entity fields and determine which require validation
3. **Generate** DTO class with @Data, @NoArgsConstructor, @AllArgsConstructor, @Builder
4. **Add** validation annotations based on business rules
5. **Implement** `fromEntity()` static factory method
6. **Implement** `toEntity()` conversion method
7. **Test** with `mvn clean compile`

## Validation Checklist

- [ ] DTO has @Data, @NoArgsConstructor, @AllArgsConstructor, @Builder
- [ ] Required fields have @NotBlank or @NotNull
- [ ] String fields have @Size with min/max
- [ ] Numeric fields have @DecimalMin/@DecimalMax or @Min/@Max
- [ ] fromEntity() static factory implemented
- [ ] toEntity() conversion method implemented
- [ ] Custom validation logic added (e.g., updateVolumeCategoryFromVolume)
- [ ] Error messages in Polish
- [ ] Compiles: `mvn clean compile`

## Key Patterns

- **Immutability**: DTOs are mutable (unlike records) for form binding
- **Conversion**: Always include fromEntity() and toEntity()
- **Security**: Never expose passwords/certificates in DTO
- **Inheritance**: DTOs don't extend entities (clean separation)
- **Composition**: Use nested DTOs for complex structures (avoid flattening)
