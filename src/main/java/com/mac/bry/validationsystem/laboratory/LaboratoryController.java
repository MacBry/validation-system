package com.mac.bry.validationsystem.laboratory;

import com.mac.bry.validationsystem.audit.EnversRevisionService;
import com.mac.bry.validationsystem.audit.FieldDiffDto;
import com.mac.bry.validationsystem.audit.RevisionInfoDto;
import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyService;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.department.DepartmentDto;
import com.mac.bry.validationsystem.department.DepartmentService;
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
import java.util.Set;

@Controller
@RequestMapping("/laboratories")
@RequiredArgsConstructor
@Slf4j
public class LaboratoryController {

    private final LaboratoryService laboratoryService;
    private final DepartmentService departmentService;
    private final CompanyService companyService;
    private final SecurityService securityService;
    private final AuditService auditService;
    private final EnversRevisionService enversRevisionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN')")
    public String list(Model model) {
        log.debug("Wyświetlanie listy pracowni");

        Set<Long> allowedIds = securityService.getAllowedLaboratoryIds();
        List<Laboratory> laboratories;

        if (allowedIds == null) {
            laboratories = laboratoryService.findAll();
        } else {
            laboratories = laboratoryService.getAllowedLaboratories(
                    allowedIds,
                    securityService.getAllowedDepartmentIds(),
                    securityService.getAllowedCompanyIds());
        }

        model.addAttribute("laboratories", laboratories);
        return "laboratory/list";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToLaboratory(#id)")
    public String showDetails(@PathVariable Long id, Model model,
            RedirectAttributes redirectAttributes) {
        log.debug("Wyświetlanie szczegółów pracowni ID: {}", id);
        return laboratoryService.findById(id)
                .map(lab -> {
                    model.addAttribute("laboratory", lab);
                    return "laboratory/details";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Nie znaleziono pracowni");
                    return "redirect:/laboratories";
                });
    }

    @GetMapping("/{id}/history")
    @ResponseBody
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToLaboratory(#id)")
    public List<RevisionInfoDto> getLaboratoryRevisions(@PathVariable Long id) {
        log.debug("Pobieranie historii Envers dla pracowni ID: {}", id);
        return enversRevisionService.getRevisionHistory(Laboratory.class, id);
    }

    @GetMapping("/{id}/history/{revNum}")
    @ResponseBody
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToLaboratory(#id)")
    public List<FieldDiffDto> getLaboratoryRevisionDiff(@PathVariable Long id, @PathVariable int revNum) {
        log.debug("Pobieranie diff rewizji {} dla pracowni ID: {}", revNum, id);
        Map<String, String> labels = Map.of(
                "fullName", "Pełna nazwa",
                "abbreviation", "Skrót",
                "department.name", "Dział");
        return enversRevisionService.getDetailedDiff(Laboratory.class, id, revNum, labels);
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN')")
    public String showCreateForm(Model model) {
        log.debug("Wyświetlanie formularza tworzenia pracowni");

        Set<Long> allowedCompanyIds = securityService.getAllowedCompanyIds();
        List<Company> companies;
        if (allowedCompanyIds == null) {
            companies = companyService.getAllCompanies();
        } else {
            companies = companyService.getAllowedCompanies(allowedCompanyIds);
        }

        Set<Long> allowedDeptIds = securityService.getAllowedDepartmentIds();
        Set<Long> allowedCompanyIdsInDeptSet = securityService.getAllowedCompanyIds();
        List<Department> departments;
        if (allowedDeptIds == null) {
            departments = departmentService.getAllDepartments();
        } else {
            departments = departmentService.getAllowedDepartments(allowedDeptIds, allowedCompanyIdsInDeptSet);
        }

        model.addAttribute("laboratory", new Laboratory());
        model.addAttribute("companies", companies);
        model.addAttribute("departments", departments.stream()
                .map(DepartmentDto::fromEntity)
                .toList());
        return "laboratory/form";
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToDepartment(#laboratory.department.id)")
    public String create(@Valid @ModelAttribute Laboratory laboratory,
            BindingResult result,
            RedirectAttributes redirectAttributes,
            Model model) {
        log.debug("Tworzenie nowej pracowni: {}", laboratory);

        if (result.hasErrors()) {
            prepareFormModel(model);
            return "laboratory/form";
        }

        // Walidacja: Pracownia MUSI mieć dział
        if (laboratory.getDepartment() == null || laboratory.getDepartment().getId() == null) {
            result.rejectValue("department", "error.laboratory",
                    "Pracownia musi być przypisana do działu");
            prepareFormModel(model);
            return "laboratory/form";
        }

        if (laboratoryService.existsByFullName(laboratory.getFullName())) {
            result.rejectValue("fullName", "error.laboratory",
                    "Pracownia o tej nazwie już istnieje");
            prepareFormModel(model);
            return "laboratory/form";
        }

        if (laboratoryService.existsByAbbreviation(laboratory.getAbbreviation())) {
            result.rejectValue("abbreviation", "error.laboratory",
                    "Pracownia o tym skrócie już istnieje");
            prepareFormModel(model);
            return "laboratory/form";
        }

        laboratoryService.save(laboratory);
        redirectAttributes.addFlashAttribute("successMessage",
                "Pracownia została pomyślnie utworzona");
        return "redirect:/laboratories";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToLaboratory(#id)")
    public String showEditForm(@PathVariable Long id, Model model,
            RedirectAttributes redirectAttributes) {
        log.debug("Wyświetlanie formularza edycji pracowni o id: {}", id);

        return laboratoryService.findById(id)
                .map(laboratory -> {
                    model.addAttribute("laboratory", laboratory);
                    prepareFormModel(model);
                    return "laboratory/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Nie znaleziono pracowni");
                    return "redirect:/laboratories";
                });
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToLaboratory(#id)")
    public String update(@PathVariable Long id,
            @Valid @ModelAttribute Laboratory laboratory,
            BindingResult result,
            RedirectAttributes redirectAttributes,
            Model model) {
        log.debug("Aktualizacja pracowni o id: {}", id);

        if (result.hasErrors()) {
            prepareFormModel(model);
            return "laboratory/form";
        }

        // Walidacja: Pracownia MUSI mieć dział
        if (laboratory.getDepartment() == null || laboratory.getDepartment().getId() == null) {
            result.rejectValue("department", "error.laboratory",
                    "Pracownia musi być przypisana do działu");
            prepareFormModel(model);
            return "laboratory/form";
        }

        return laboratoryService.findById(id)
                .map(existingLab -> {
                    Map<String, Object> oldSnapshot = Map.of("fullName", existingLab.getFullName(),
                            "abbreviation", existingLab.getAbbreviation() != null ? existingLab.getAbbreviation() : "");
                    existingLab.setFullName(laboratory.getFullName());
                    existingLab.setAbbreviation(laboratory.getAbbreviation());
                    existingLab.setDepartment(laboratory.getDepartment());
                    laboratoryService.save(existingLab);
                    redirectAttributes.addFlashAttribute("successMessage",
                            "Pracownia została pomyślnie zaktualizowana");
                    return "redirect:/laboratories";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Nie znaleziono pracowni");
                    return "redirect:/laboratories";
                });
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or (@securityService.hasAccessToLaboratory(#id) and hasRole('ROLE_COMPANY_ADMIN'))")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.debug("Usuwanie pracowni o id: {}", id);

        try {
            laboratoryService.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Pracownia została pomyślnie usunięta");
        } catch (Exception e) {
            log.error("Błąd podczas usuwania pracowni", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Nie można usunąć pracowni - prawdopodobnie jest powiązana z urządzeniami");
        }

        return "redirect:/laboratories";
    }

    private void prepareFormModel(Model model) {
        Set<Long> allowedCompanyIds = securityService.getAllowedCompanyIds();
        List<Company> companies;
        if (allowedCompanyIds == null) {
            companies = companyService.getAllCompanies();
        } else {
            companies = companyService.getAllowedCompanies(allowedCompanyIds);
        }

        Set<Long> allowedDeptIds = securityService.getAllowedDepartmentIds();
        List<Department> departments;
        if (allowedDeptIds == null) {
            departments = departmentService.getAllDepartments();
        } else {
            departments = departmentService.getAllowedDepartments(allowedDeptIds, allowedCompanyIds);
        }

        model.addAttribute("companies", companies);
        model.addAttribute("departments", departments.stream()
                .map(DepartmentDto::fromEntity)
                .toList());
    }
}
