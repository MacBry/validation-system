package com.mac.bry.validationsystem.materialtype;

import com.mac.bry.validationsystem.audit.EnversRevisionService;
import com.mac.bry.validationsystem.audit.FieldDiffDto;
import com.mac.bry.validationsystem.audit.RevisionInfoDto;
import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import com.mac.bry.validationsystem.security.service.AuditService;
import com.mac.bry.validationsystem.security.service.SecurityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Controller
@RequestMapping("/material-types")
@RequiredArgsConstructor
@Slf4j
public class MaterialTypeController {

    private final MaterialTypeService materialTypeService;
    private final SecurityService securityService;
    private final CompanyRepository companyRepository;
    private final AuditService auditService;
    private final EnversRevisionService enversRevisionService;

    /**
     * Lista wszystkich typów materiałów
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN', 'ROLE_USER')")
    public String list(Model model) {
        log.debug("Wyświetlanie listy typów materiałów");
        Set<Long> allowedCompanyIds = securityService.getAllowedCompanyIds();
        List<MaterialType> materials = materialTypeService.findAll(allowedCompanyIds);
        model.addAttribute("materials", materials);
        return "materialtype/list";
    }

    /**
     * Formularz dodawania nowego typu materiału
     */
    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN')")
    public String showCreateForm(Model model) {
        log.debug("Wyświetlanie formularza nowego typu materiału");
        model.addAttribute("materialDto", new MaterialTypeDto());
        return "materialtype/form";
    }

    /**
     * Zapisywanie nowego typu materiału
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN')")
    public String create(@Valid @ModelAttribute("materialDto") MaterialTypeDto dto,
            BindingResult result,
            RedirectAttributes redirectAttributes) {
        log.debug("Tworzenie nowego typu materiału: {}", dto);

        if (result.hasErrors()) {
            return "materialtype/form";
        }

        Long currentCompanyId = null;
        if (securityService.isCompanyAdmin()) {
            Set<Long> companyIds = securityService.getAllowedCompanyIds();
            if (companyIds != null && !companyIds.isEmpty()) {
                currentCompanyId = companyIds.iterator().next(); // Bierzemy pierwszą (zwykle jedyną) firmę
            }
        }

        if (materialTypeService.existsByName(dto.getName(), currentCompanyId)) {
            result.rejectValue("name", "error.materialType",
                    "Materiał o tej nazwie już istnieje w Twojej organizacji");
            return "materialtype/form";
        }

        MaterialType material = dto.toEntity();
        if (currentCompanyId != null) {
            companyRepository.findById(currentCompanyId).ifPresent(material::setCompany);
        }

        materialTypeService.save(material);

        redirectAttributes.addFlashAttribute("successMessage",
                "Typ materiału został pomyślnie utworzony");
        return "redirect:/material-types";
    }

    /**
     * Formularz edycji typu materiału
     */
    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN')")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Wyświetlanie formularza edycji typu materiału o id: {}", id);

        return materialTypeService.findById(id)
                .filter(m -> {
                    if (securityService.isSuperAdmin())
                        return true;
                    Set<Long> allowed = securityService.getAllowedCompanyIds();
                    return m.getCompany() != null && allowed != null && allowed.contains(m.getCompany().getId());
                })
                .map(material -> {
                    MaterialTypeDto dto = MaterialTypeDto.fromEntity(material);
                    model.addAttribute("materialDto", dto);
                    return "materialtype/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Nie znaleziono typu materiału lub brak uprawnień");
                    return "redirect:/material-types";
                });
    }

    /**
     * Aktualizacja typu materiału
     */
    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN')")
    public String update(@PathVariable Long id,
            @Valid @ModelAttribute("materialDto") MaterialTypeDto dto,
            BindingResult result,
            RedirectAttributes redirectAttributes) {
        log.debug("Aktualizacja typu materiału o id: {}", id);

        if (result.hasErrors()) {
            return "materialtype/form";
        }

        return materialTypeService.findById(id)
                .filter(m -> {
                    if (securityService.isSuperAdmin())
                        return true;
                    Set<Long> allowed = securityService.getAllowedCompanyIds();
                    return m.getCompany() != null && allowed != null && allowed.contains(m.getCompany().getId());
                })
                .map(existingMaterial -> {
                    Map<String, Object> oldSnapshot = Map.of("name", existingMaterial.getName(),
                            "minStorageTemp", existingMaterial.getMinStorageTemp(),
                            "maxStorageTemp", existingMaterial.getMaxStorageTemp());

                    Long companyId = existingMaterial.getCompany() != null ? existingMaterial.getCompany().getId()
                            : null;
                    // Sprawdź czy nazwa nie jest zajęta przez inny materiał w TEJ SAMEJ firmie
                    if (!existingMaterial.getName().equals(dto.getName()) &&
                            materialTypeService.existsByName(dto.getName(), companyId)) {
                        result.rejectValue("name", "error.materialType",
                                "Materiał o tej nazwie już istnieje");
                        return "materialtype/form";
                    }

                    existingMaterial.setName(dto.getName());
                    existingMaterial.setDescription(dto.getDescription());
                    existingMaterial.setMinStorageTemp(dto.getMinStorageTemp());
                    existingMaterial.setMaxStorageTemp(dto.getMaxStorageTemp());
                    existingMaterial.setActivationEnergy(dto.getActivationEnergy());
                    existingMaterial.setStandardSource(dto.getStandardSource());
                    existingMaterial.setApplication(dto.getApplication());
                    existingMaterial.setActive(dto.getActive());

                    materialTypeService.save(existingMaterial);

                    redirectAttributes.addFlashAttribute("successMessage",
                            "Typ materiału został pomyślnie zaktualizowany");
                    return "redirect:/material-types";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Nie znaleziono typu materiału lub brak uprawnień");
                    return "redirect:/material-types";
                });
    }

    /**
     * Usuwanie typu materiału
     */
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN')")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.debug("Usuwanie typu materiału o id: {}", id);

        java.util.Optional<MaterialType> materialOpt = materialTypeService.findById(id);
        if (materialOpt.isPresent()) {
            MaterialType material = materialOpt.get();
            if (!securityService.isSuperAdmin()) {
                Set<Long> allowed = securityService.getAllowedCompanyIds();
                if (material.getCompany() == null || allowed == null
                        || !allowed.contains(material.getCompany().getId())) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Brak uprawnień do usunięcia tego materiału");
                    return "redirect:/material-types";
                }
            }

            try {
                materialTypeService.deleteById(id);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Typ materiału został pomyślnie usunięty");
            } catch (Exception e) {
                log.error("Błąd podczas usuwania typu materiału: {}", e.getMessage());
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Nie można usunąć typu materiału - prawdopodobnie jest używany przez urządzenia");
            }
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Nie znaleziono typu materiału");
        }

        return "redirect:/material-types";
    }

    /**
     * Szczegóły typu materiału z historią Envers.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN', 'ROLE_USER')")
    public String details(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return materialTypeService.findById(id)
                .map(material -> {
                    model.addAttribute("material", material);
                    return "materialtype/details";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Nie znaleziono typu materiału");
                    return "redirect:/material-types";
                });
    }

    /**
     * Envers: lista rewizji dla typu materiału (AJAX/JSON).
     */
    @GetMapping("/{id}/history")
    @ResponseBody
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN', 'ROLE_USER')")
    public List<RevisionInfoDto> getMaterialTypeRevisions(@PathVariable Long id) {
        log.debug("Pobieranie historii Envers dla typu materiału ID: {}", id);
        return enversRevisionService.getRevisionHistory(MaterialType.class, id);
    }

    /**
     * Envers: diff pól między rewizją N a N-1 (AJAX/JSON).
     */
    @GetMapping("/{id}/history/{revNum}")
    @ResponseBody
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN', 'ROLE_USER')")
    public List<FieldDiffDto> getMaterialTypeRevisionDiff(@PathVariable Long id, @PathVariable int revNum) {
        log.debug("Pobieranie diff rewizji {} dla typu materiału ID: {}", revNum, id);
        Map<String, String> labels = Map.ofEntries(
                Map.entry("name",             "Nazwa materiału"),
                Map.entry("description",      "Opis"),
                Map.entry("minStorageTemp",   "Temp. min (°C)"),
                Map.entry("maxStorageTemp",   "Temp. max (°C)"),
                Map.entry("activationEnergy", "Energia aktywacji (kJ/mol)"),
                Map.entry("standardSource",   "Standard / Źródło"),
                Map.entry("application",      "Zastosowanie"),
                Map.entry("active",           "Aktywny"));
        return enversRevisionService.getDetailedDiff(MaterialType.class, id, revNum, labels);
    }
}
