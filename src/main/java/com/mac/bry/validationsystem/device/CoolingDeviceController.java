package com.mac.bry.validationsystem.device;

import com.mac.bry.validationsystem.audit.EnversRevisionService;
import com.mac.bry.validationsystem.audit.FieldDiffDto;
import com.mac.bry.validationsystem.audit.RevisionInfoDto;
import com.mac.bry.validationsystem.company.CompanyService;
import com.mac.bry.validationsystem.department.DepartmentService;
import com.mac.bry.validationsystem.laboratory.Laboratory;
import com.mac.bry.validationsystem.laboratory.LaboratoryService;
import com.mac.bry.validationsystem.materialtype.MaterialType;
import com.mac.bry.validationsystem.materialtype.MaterialTypeService;
import com.mac.bry.validationsystem.security.service.AuditService;
import com.mac.bry.validationsystem.security.service.SecurityService;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationService;
import com.mac.bry.validationsystem.validation.ValidationStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/devices")
@RequiredArgsConstructor
@Slf4j
public class CoolingDeviceController {

    private final CoolingDeviceService coolingDeviceService;
    private final DepartmentService departmentService;
    private final CompanyService companyService;
    private final LaboratoryService laboratoryService;
    private final MaterialTypeService materialTypeService;
    private final ValidationService validationService;
    private final AuditService auditService;
    private final SecurityService securityService;
    private final EnversRevisionService enversRevisionService;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long laboratoryId,
            Model model) {
        log.debug("Wyświetlanie listy urządzeń chłodniczych (strona: {}, filtry: company={}, dept={}, lab={})", 
                page, companyId, departmentId, laboratoryId);
        
        Page<CoolingDevice> devicePage = coolingDeviceService.getAllAccessibleDevices(
                PageRequest.of(page, 20), companyId, departmentId, laboratoryId);

        model.addAttribute("devicePage", devicePage);
        model.addAttribute("devices", devicePage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", devicePage.getTotalPages());
        
        // Filtry
        model.addAttribute("companyId", companyId);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("laboratoryId", laboratoryId);
        
        // Dane do dropdownów filtrów
        model.addAttribute("companies", companyService.getAllowedCompanies(securityService.getAllowedCompanyIds()));
        
        if (companyId != null) {
            model.addAttribute("departments", departmentService.getDepartmentsByCompany(companyId));
        } else {
            model.addAttribute("departments", departmentService.getAllowedDepartments(
                    securityService.getDepartmentIdsWithImplicitAccess(), securityService.getAllowedCompanyIds()));
        }

        if (departmentId != null) {
            model.addAttribute("laboratories", laboratoryService.getLaboratoriesByDepartment(departmentId));
        }

        // Dodaj statusy walidacji dla każdego urządzenia na bieżącej stronie
        java.util.Map<Long, DeviceValidationStatus> validationStatuses = new java.util.HashMap<>();
        for (CoolingDevice device : devicePage.getContent()) {
            validationStatuses.put(device.getId(), coolingDeviceService.getValidationStatus(device.getId()));
        }
        model.addAttribute("validationStatuses", validationStatuses);

        return "device/list";
    }

    @PreAuthorize("@securityService.canManageDevice(#id)")
    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Szczegóły urządzenia o id: {}", id);

        return coolingDeviceService.findById(id)
                .map(device -> {
                    model.addAttribute("device", device);
                    // Pobierz logi audytowe
                    model.addAttribute("auditLogs", auditService.getLogsForEntity("CoolingDevice", id));

                    // Ładuj działy dla przycisku "Przenieś" (ADMIN+)
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    boolean canTransfer = auth != null && auth.getAuthorities().stream().anyMatch(a ->
                            Set.of("ROLE_SUPER_ADMIN", "ROLE_COMPANY_ADMIN", "ROLE_ADMIN")
                               .contains(a.getAuthority()));
                    if (canTransfer) {
                        model.addAttribute("canTransfer", true);
                        model.addAttribute("departments", departmentService.getAllowedDepartments(
                                securityService.getDepartmentIdsWithImplicitAccess(),
                                securityService.getAllowedCompanyIds()));
                    }

                    return "device/details";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Nie znaleziono urządzenia");
                    return "redirect:/devices";
                });
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        log.debug("Wyświetlanie formularza tworzenia urządzenia");
        model.addAttribute("deviceDto", new CoolingDeviceDto());
        model.addAttribute("departments", departmentService.getAllowedDepartments(
                securityService.getDepartmentIdsWithImplicitAccess(), securityService.getAllowedCompanyIds()));
        model.addAttribute("chamberTypes", ChamberType.values());
        model.addAttribute("materialTypes", materialTypeService.findAllActive(securityService.getAllowedCompanyIds()));
        model.addAttribute("volumeCategories", VolumeCategory.values());
        return "device/form";
    }

    @PreAuthorize("@securityService.hasAccessToDepartment(#deviceDto.departmentId) or " +
            "(#deviceDto.laboratoryId != null and @securityService.hasAccessToLaboratory(#deviceDto.laboratoryId))")
    @PostMapping
    public String create(@Valid @ModelAttribute("deviceDto") CoolingDeviceDto deviceDto,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {
        log.debug("Tworzenie nowego urządzenia: {}", deviceDto);

        if (result.hasErrors()) {
            model.addAttribute("departments", departmentService.getAllowedDepartments(
                    securityService.getDepartmentIdsWithImplicitAccess(), securityService.getAllowedCompanyIds()));
            model.addAttribute("chamberTypes", ChamberType.values());
            model.addAttribute("materialTypes",
                    materialTypeService.findAllActive(securityService.getAllowedCompanyIds()));
            model.addAttribute("volumeCategories", VolumeCategory.values());
            return "device/form";
        }

        if (coolingDeviceService.existsByInventoryNumber(deviceDto.getInventoryNumber())) {
            result.rejectValue("inventoryNumber", "error.device",
                    "Urządzenie o tym numerze inwentarzowym już istnieje");
            model.addAttribute("departments", departmentService.getAllowedDepartments(
                    securityService.getDepartmentIdsWithImplicitAccess(), securityService.getAllowedCompanyIds()));
            model.addAttribute("chamberTypes", ChamberType.values());
            model.addAttribute("materialTypes",
                    materialTypeService.findAllActive(securityService.getAllowedCompanyIds()));
            model.addAttribute("volumeCategories", VolumeCategory.values());
            return "device/form";
        }

        // Pobierz dział
        var department = departmentService.getDepartmentById(deviceDto.getDepartmentId());

        // Pobierz pracownię (jeśli podano)
        Laboratory laboratory = null;
        if (deviceDto.getLaboratoryId() != null) {
            laboratory = laboratoryService.findById(deviceDto.getLaboratoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono pracowni"));
        }

        MaterialType materialType = materialTypeService.findById(deviceDto.getMaterialTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono typu materiału"));

        CoolingDevice device = CoolingDevice.builder()
                .inventoryNumber(deviceDto.getInventoryNumber())
                .name(deviceDto.getName())
                .department(department)
                .laboratory(laboratory)
                .chamberType(deviceDto.getChamberType())
                .materialType(materialType)
                .minOperatingTemp(deviceDto.getMinOperatingTemp())
                .maxOperatingTemp(deviceDto.getMaxOperatingTemp())
                .volume(deviceDto.getVolume())
                .volumeCategory(deviceDto.getVolumeCategory())
                .build();

        // Auto-ustawianie kategorii kubatury na podstawie objętości (zgodnie z PDA TR-64)
        if (device.getVolume() != null && device.getVolumeCategory() == null) {
            device.updateVolumeCategoryFromVolume();
            log.debug("Auto-ustawiono kategorię kubatury: {} dla objętości: {} m³",
                    device.getVolumeCategory(), device.getVolume());
        }

        CoolingDevice savedDevice = coolingDeviceService.save(device);

        // Dodaj numer RPW jeśli podano
        if (deviceDto.getNewPlanYear() != null && deviceDto.getNewPlanNumber() != null) {
            try {
                coolingDeviceService.addValidationPlanNumber(
                        savedDevice.getId(),
                        deviceDto.getNewPlanYear(),
                        deviceDto.getNewPlanNumber());
            } catch (Exception e) {
                log.error("Błąd podczas dodawania numeru RPW", e);
            }
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Urządzenie zostało pomyślnie utworzone");
        return "redirect:/devices/" + savedDevice.getId();
    }

    @PreAuthorize("@securityService.canManageDevice(#id)")
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Wyświetlanie formularza edycji urządzenia o id: {}", id);

        return coolingDeviceService.findById(id)
                .map(device -> {
                    CoolingDeviceDto dto = CoolingDeviceDto.builder()
                            .id(device.getId())
                            .inventoryNumber(device.getInventoryNumber())
                            .name(device.getName())
                            .departmentId(device.getDepartment().getId())
                            .laboratoryId(device.getLaboratory() != null ? device.getLaboratory().getId() : null)
                            .chamberType(device.getChamberType())
                            .materialTypeId(device.getMaterialType() != null ? device.getMaterialType().getId() : null)
                            .minOperatingTemp(device.getMinOperatingTemp())
                            .maxOperatingTemp(device.getMaxOperatingTemp())
                            .volume(device.getVolume())
                            .volumeCategory(device.getVolumeCategory())
                            .build();

                    model.addAttribute("deviceDto", dto);
                    model.addAttribute("departments",
                            departmentService.getAllowedDepartments(
                                    securityService.getDepartmentIdsWithImplicitAccess(),
                                    securityService.getAllowedCompanyIds()));
                    model.addAttribute("chamberTypes", ChamberType.values());
                    model.addAttribute("materialTypes",
                            materialTypeService.findAllActive(securityService.getAllowedCompanyIds()));
                    model.addAttribute("volumeCategories", VolumeCategory.values());

                    // Jeśli urządzenie ma dział z pracowniami, załaduj je
                    if (device.getDepartment().getHasLaboratories()) {
                        model.addAttribute("laboratories",
                                laboratoryService.getLaboratoriesByDepartment(device.getDepartment().getId()));
                    }

                    return "device/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Nie znaleziono urządzenia");
                    return "redirect:/devices";
                });
    }

    @PreAuthorize("@securityService.canManageDevice(#id) and @securityService.hasAccessToDepartment(#deviceDto.departmentId)")
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
            @Valid @ModelAttribute("deviceDto") CoolingDeviceDto deviceDto,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {
        log.debug("Aktualizacja urządzenia o id: {}", id);

        if (result.hasErrors()) {
            model.addAttribute("departments", departmentService.getAllowedDepartments(
                    securityService.getDepartmentIdsWithImplicitAccess(), securityService.getAllowedCompanyIds()));
            model.addAttribute("chamberTypes", ChamberType.values());
            model.addAttribute("materialTypes",
                    materialTypeService.findAllActive(securityService.getAllowedCompanyIds()));
            model.addAttribute("volumeCategories", VolumeCategory.values());
            return "device/form";
        }

        return coolingDeviceService.findById(id)
                .map(existingDevice -> {
                    // Utwórz snapshot starego urządzenia do audytu
                    CoolingDeviceDto oldSnapshot = CoolingDeviceDto.builder()
                            .inventoryNumber(existingDevice.getInventoryNumber())
                            .name(existingDevice.getName())
                            .chamberType(existingDevice.getChamberType())
                            .minOperatingTemp(existingDevice.getMinOperatingTemp())
                            .maxOperatingTemp(existingDevice.getMaxOperatingTemp())
                            .build();

                    // Pobierz dział
                    var department = departmentService.getDepartmentById(deviceDto.getDepartmentId());

                    // Pobierz pracownię (jeśli podano)
                    Laboratory laboratory = null;
                    if (deviceDto.getLaboratoryId() != null) {
                        laboratory = laboratoryService.findById(deviceDto.getLaboratoryId())
                                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono pracowni"));
                    }

                    MaterialType materialType = materialTypeService.findById(deviceDto.getMaterialTypeId())
                            .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono typu materiału"));

                    existingDevice.setInventoryNumber(deviceDto.getInventoryNumber());
                    existingDevice.setName(deviceDto.getName());
                    existingDevice.setDepartment(department);
                    existingDevice.setLaboratory(laboratory);
                    existingDevice.setChamberType(deviceDto.getChamberType());
                    existingDevice.setMaterialType(materialType);
                    existingDevice.setMinOperatingTemp(deviceDto.getMinOperatingTemp());
                    existingDevice.setMaxOperatingTemp(deviceDto.getMaxOperatingTemp());

                    coolingDeviceService.save(existingDevice);

                    redirectAttributes.addFlashAttribute("successMessage",
                            "Urządzenie zostało pomyślnie zaktualizowane");
                    return "redirect:/devices/" + id;
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Nie znaleziono urządzenia");
                    return "redirect:/devices";
                });
    }

    @PreAuthorize("@securityService.canManageDevice(#id)")
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.debug("Usuwanie urządzenia o id: {}", id);

        try {
            coolingDeviceService.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Urządzenie zostało pomyślnie usunięte");
        } catch (Exception e) {
            log.error("Błąd podczas usuwania urządzenia", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Nie można usunąć urządzenia");
        }

        return "redirect:/devices";
    }

    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN', 'ROLE_ADMIN') and " +
                  "@securityService.canManageDevice(#id) and " +
                  "@securityService.hasAccessToDepartment(#departmentId)")
    @PostMapping("/{id}/transfer")
    public String transfer(@PathVariable Long id,
            @RequestParam Long departmentId,
            @RequestParam(required = false) Long laboratoryId,
            RedirectAttributes redirectAttributes) {
        log.debug("Przeniesienie urządzenia o id: {} do działu: {}, pracownia: {}", id, departmentId, laboratoryId);

        return coolingDeviceService.findById(id)
                .map(device -> {
                    var department = departmentService.getDepartmentById(departmentId);

                    Laboratory laboratory = null;
                    if (laboratoryId != null) {
                        laboratory = laboratoryService.findById(laboratoryId)
                                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono pracowni"));
                    }

                    device.setDepartment(department);
                    device.setLaboratory(laboratory);
                    coolingDeviceService.save(device);

                    redirectAttributes.addFlashAttribute("successMessage",
                            "Urządzenie zostało przeniesione do: " + department.getName() +
                            (laboratory != null ? " / " + laboratory.getFullName() : ""));
                    return "redirect:/devices/" + id;
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Nie znaleziono urządzenia");
                    return "redirect:/devices";
                });
    }

    @PreAuthorize("@securityService.canManageDevice(#id)")
    @PostMapping("/{id}/plan-numbers")
    public String addPlanNumber(@PathVariable Long id,
            @RequestParam Integer year,
            @RequestParam Integer planNumber,
            RedirectAttributes redirectAttributes) {
        log.debug("Dodawanie numeru RPW {} dla roku {} do urządzenia o id: {}", planNumber, year, id);

        try {
            coolingDeviceService.addValidationPlanNumber(id, year, planNumber);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Numer RPW został pomyślnie dodany");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Błąd podczas dodawania numeru RPW", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Nie można dodać numeru RPW");
        }

        return "redirect:/devices/" + id;
    }

    @PreAuthorize("@securityService.canManageDevice(#deviceId)")
    @PostMapping("/{deviceId}/plan-numbers/{planNumberId}/delete")
    public String removePlanNumber(@PathVariable Long deviceId,
            @PathVariable Long planNumberId,
            RedirectAttributes redirectAttributes) {
        log.debug("Usuwanie numeru RPW o id: {} z urządzenia o id: {}", planNumberId, deviceId);

        try {
            coolingDeviceService.removeValidationPlanNumber(deviceId, planNumberId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Numer RPW został pomyślnie usunięty");
        } catch (Exception e) {
            log.error("Błąd podczas usuwania numeru RPW", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Nie można usunąć numeru RPW");
        }

        return "redirect:/devices/" + deviceId;
    }

    @PreAuthorize("@securityService.canManageDevice(#id)")
    @GetMapping("/{id}/validations")
    public String deviceValidations(@PathVariable Long id,
            @RequestParam(required = false) ValidationStatus status,
            Model model,
            RedirectAttributes redirectAttributes) {
        log.debug("Wyświetlanie walidacji dla urządzenia o id: {} z filtrem statusu: {}", id, status);

        return coolingDeviceService.findById(id)
                .map(device -> {
                    model.addAttribute("device", device);

                    List<Validation> validations = validationService.findByDeviceId(id);

                    // Filtrowanie po statusie jeśli podano
                    if (status != null) {
                        validations = validations.stream()
                                .filter(v -> v.getStatus() == status)
                                .collect(java.util.stream.Collectors.toList());
                    }

                    model.addAttribute("validations", validations);
                    model.addAttribute("allStatuses", ValidationStatus.values());
                    model.addAttribute("selectedStatus", status);
                    return "device/validations";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Nie znaleziono urządzenia");
                    return "redirect:/devices";
                });
    }

    /**
     * API endpoint - pobiera pracownie dla działu (AJAX)
     */
    @GetMapping("/api/departments/{departmentId}/laboratories")
    @ResponseBody
    public List<LaboratoryDto> getLaboratoriesForDepartment(@PathVariable Long departmentId) {
        log.debug("Pobieranie pracowni dla działu ID: {}", departmentId);

        return laboratoryService.getLaboratoriesByDepartment(departmentId).stream()
                .map(lab -> new LaboratoryDto(lab.getId(), lab.getFullName(), lab.getAbbreviation()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Envers: lista rewizji dla urządzenia (AJAX/partial)
     */
    @GetMapping("/{id}/history")
    @ResponseBody
    public List<RevisionInfoDto> getDeviceRevisions(@PathVariable Long id) {
        log.debug("Pobieranie historii Envers dla urządzenia ID: {}", id);
        return enversRevisionService.getRevisionHistory(CoolingDevice.class, id);
    }

    /**
     * Envers: diff pól między rewizją N a N-1 (AJAX)
     */
    @GetMapping("/{id}/history/{revNum}")
    @ResponseBody
    public List<FieldDiffDto> getDeviceRevisionDiff(@PathVariable Long id, @PathVariable int revNum) {
        log.debug("Pobieranie diff rewizji {} dla urządzenia ID: {}", revNum, id);
        Map<String, String> labels = Map.ofEntries(
                Map.entry("inventoryNumber", "Numer inwentarzowy"),
                Map.entry("name", "Nazwa urządzenia"),
                Map.entry("chamberType", "Typ komory"),
                Map.entry("minOperatingTemp", "Temp. min (\u00b0C)"),
                Map.entry("maxOperatingTemp", "Temp. max (\u00b0C)"),
                Map.entry("volume", "Objętość (m\u00b3)"),
                Map.entry("volumeCategory", "Klasa kubatury"),
                Map.entry("department.name", "Dział"),
                Map.entry("laboratory.fullName", "Pracownia"),
                Map.entry("materialType.name", "Typ materiału"));
        return enversRevisionService.getDetailedDiff(CoolingDevice.class, id, revNum, labels);
    }

    /**
     * DTO dla AJAX response (pracownie)
     */
    private record LaboratoryDto(Long id, String name, String abbreviation) {
    }
}
