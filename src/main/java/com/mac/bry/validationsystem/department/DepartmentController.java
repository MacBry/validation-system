package com.mac.bry.validationsystem.department;

import com.mac.bry.validationsystem.audit.EnversRevisionService;
import com.mac.bry.validationsystem.audit.FieldDiffDto;
import com.mac.bry.validationsystem.audit.RevisionInfoDto;
import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyService;
import com.mac.bry.validationsystem.security.service.AuditService;
import com.mac.bry.validationsystem.security.service.SecurityService;
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
import java.util.stream.Collectors;

/**
 * Kontroler do zarządzania działami
 */
@Slf4j
@Controller
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;
    private final CompanyService companyService;
    private final SecurityService securityService;
    private final AuditService auditService;
    private final EnversRevisionService enversRevisionService;

    /**
     * Wyświetla listę działów
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN')")
    public String listDepartments(Model model) {
        log.debug("Wyświetlanie listy działów");

        Set<Long> allowedIds = securityService.getAllowedDepartmentIds();
        Set<Long> allowedCompanyIds = securityService.getAllowedCompanyIds();
        List<Department> departments;

        if (allowedIds == null) {
            departments = departmentService.getAllowedDepartments(
                    securityService.getAllowedDepartmentIds(),
                    securityService.getAllowedCompanyIds());
        } else {
            departments = departmentService.getAllowedDepartments(allowedIds, allowedCompanyIds);
        }

        List<DepartmentDto> dtos = departments.stream()
                .map(DepartmentDto::fromEntity)
                .collect(Collectors.toList());

        model.addAttribute("departments", dtos);

        return "department/list";
    }

    /**
     * Wyświetla szczegóły działu
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToDepartment(#id)")
    public String showDetails(@PathVariable Long id, Model model) {
        log.debug("Wyświetlanie szczegółów działu ID: {}", id);
        model.addAttribute("department", departmentService.getDepartmentById(id));
        return "department/details";
    }

    /**
     * Envers: lista rewizji dla działu (AJAX/JSON)
     */
    @GetMapping("/{id}/history")
    @ResponseBody
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToDepartment(#id)")
    public List<RevisionInfoDto> getDepartmentRevisions(@PathVariable Long id) {
        log.debug("Pobieranie historii Envers dla działu ID: {}", id);
        return enversRevisionService.getRevisionHistory(Department.class, id);
    }

    /**
     * Envers: diff pól między rewizją N a N-1 (AJAX/JSON)
     */
    @GetMapping("/{id}/history/{revNum}")
    @ResponseBody
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToDepartment(#id)")
    public List<FieldDiffDto> getDepartmentRevisionDiff(@PathVariable Long id, @PathVariable int revNum) {
        log.debug("Pobieranie diff rewizji {} dla działu ID: {}", revNum, id);
        Map<String, String> labels = Map.of(
                "name", "Nazwa działu",
                "abbreviation", "Skrót",
                "description", "Opis",
                "company.name", "Firma",
                "hasLaboratories", "Posiada pracownie");
        return enversRevisionService.getDetailedDiff(Department.class, id, revNum, labels);
    }

    /**
     * Wyświetla formularz dodawania nowego działu
     */
    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN')")
    public String showNewForm(Model model) {
        log.debug("Wyświetlanie formularza nowego działu");

        // Pusty DTO dla formularza
        DepartmentDto dto = DepartmentDto.forForm(null, "", "", "", false);

        Set<Long> allowedCompanyIds = securityService.getAllowedCompanyIds();
        List<Company> companies;
        if (allowedCompanyIds == null) {
            companies = companyService.getAllCompanies();
        } else {
            companies = companyService.getAllowedCompanies(allowedCompanyIds);
        }

        model.addAttribute("department", dto);
        model.addAttribute("companies", companies);
        model.addAttribute("isEdit", false);

        return "department/form";
    }

    /**
     * Zapisuje nowy dział
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToCompany(#dto.companyId())")
    public String createDepartment(@ModelAttribute("department") DepartmentDto dto,
            BindingResult result,
            RedirectAttributes redirectAttributes) {
        log.info("Zapisywanie nowego działu: {}", dto.name());

        try {
            Department created = departmentService.createDepartment(dto);

            redirectAttributes.addFlashAttribute("success",
                    "Pomyślnie utworzono dział: " + created.getName());

            return "redirect:/departments";

        } catch (IllegalArgumentException e) {
            log.error("Błąd podczas tworzenia działu: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/departments/new";
        }
    }

    /**
     * Wyświetla formularz edycji działu
     */
    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToDepartment(#id)")
    public String showEditForm(@PathVariable Long id, Model model) {
        log.debug("Wyświetlanie formularza edycji działu ID: {}", id);

        Department department = departmentService.getDepartmentById(id);
        DepartmentDto dto = DepartmentDto.fromEntity(department);

        Set<Long> allowedCompanyIds = securityService.getAllowedCompanyIds();
        List<Company> companies;
        if (allowedCompanyIds == null) {
            companies = companyService.getAllCompanies();
        } else {
            companies = companyService.getAllowedCompanies(allowedCompanyIds);
        }

        model.addAttribute("department", dto);
        model.addAttribute("companies", companies);
        model.addAttribute("isEdit", true);

        return "department/form";
    }

    /**
     * Aktualizuje dział
     */
    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToDepartment(#id)")
    public String updateDepartment(@PathVariable Long id,
            @ModelAttribute("department") DepartmentDto dto,
            BindingResult result,
            RedirectAttributes redirectAttributes) {
        log.info("Aktualizacja działu ID: {}", id);

        try {
            Department existing = departmentService.getDepartmentById(id);
            Map<String, Object> oldSnapshot = Map.of("name", existing.getName(),
                    "abbreviation", existing.getAbbreviation() != null ? existing.getAbbreviation() : "");
            Department updated = departmentService.updateDepartment(id, dto);

            redirectAttributes.addFlashAttribute("success",
                    "Pomyślnie zaktualizowano dział: " + updated.getName());

            return "redirect:/departments";

        } catch (IllegalArgumentException e) {
            log.error("Błąd podczas aktualizacji działu: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/departments/" + id + "/edit";
        }
    }

    /**
     * Usuwa dział
     */
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or (@securityService.hasAccessToDepartment(#id) and hasRole('ROLE_COMPANY_ADMIN'))")
    public String deleteDepartment(@PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        log.info("Usuwanie działu ID: {}", id);

        try {
            Department department = departmentService.getDepartmentById(id);
            String departmentName = department.getName();

            departmentService.deleteDepartment(id);

            redirectAttributes.addFlashAttribute("success",
                    "Pomyślnie usunięto dział: " + departmentName);

        } catch (IllegalStateException e) {
            log.error("Nie można usunąć działu: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Błąd: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Nie znaleziono działu");
        }

        return "redirect:/departments";
    }

    /**
     * API endpoint - pobiera pracownie dla działu (AJAX)
     */
    @GetMapping("/{id}/laboratories")
    @ResponseBody
    public List<LaboratoryDto> getLaboratories(@PathVariable Long id) {
        log.debug("Pobieranie pracowni dla działu ID: {}", id);

        return departmentService.getDepartmentById(id)
                .getLaboratories()
                .stream()
                .map(lab -> new LaboratoryDto(lab.getId(), lab.getFullName(), lab.getAbbreviation()))
                .collect(Collectors.toList());
    }

    /**
     * DTO dla AJAX response (pracownie)
     */
    private record LaboratoryDto(Long id, String name, String abbreviation) {
    }
}
