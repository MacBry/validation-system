package com.mac.bry.validationsystem.company;

import com.mac.bry.validationsystem.audit.EnversRevisionService;
import com.mac.bry.validationsystem.audit.FieldDiffDto;
import com.mac.bry.validationsystem.audit.RevisionInfoDto;
import com.mac.bry.validationsystem.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Controller
@RequestMapping("/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final SecurityService securityService;
    private final EnversRevisionService enversRevisionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN')")
    public String listCompanies(Model model) {
        log.debug("Wyświetlanie listy firm");

        Set<Long> allowedIds = securityService.getAllowedCompanyIds();
        List<Company> companies;

        if (allowedIds == null) {
            companies = companyService.getAllCompanies();
        } else {
            companies = companyService.getAllowedCompanies(allowedIds);
        }

        model.addAttribute("companies", companies);
        return "company/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public String showNewForm(Model model) {
        log.debug("Wyświetlanie formularza nowej firmy");
        model.addAttribute("company", new CompanyFormBean());
        model.addAttribute("isEdit", false);
        return "company/form";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToCompany(#id)")
    public String showDetails(@PathVariable Long id, Model model) {
        log.debug("Wyświetlanie szczegółów firmy: {}", id);
        Company company = companyService.getCompanyById(id);
        model.addAttribute("company", company);
        return "company/details";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToCompany(#id)")
    public String showEditForm(@PathVariable Long id, Model model) {
        log.debug("Wyświetlanie formularza edycji firmy: {}", id);
        Company company = companyService.getCompanyById(id);

        CompanyFormBean formBean = new CompanyFormBean();
        formBean.setName(company.getName());
        formBean.setAddress(company.getAddress());

        model.addAttribute("company", formBean);
        model.addAttribute("companyId", id);
        model.addAttribute("isEdit", true);
        return "company/form";
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public String createCompany(@ModelAttribute CompanyFormBean formBean,
            RedirectAttributes redirectAttributes,
            Model model) {
        log.debug("Tworzenie nowej firmy: {}", formBean.getName());

        try {
            Company saved = companyService.createCompany(formBean.getName(), formBean.getAddress());
            log.info("Utworzono firmę: {} (ID: {})", saved.getName(), saved.getId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Firma '" + saved.getName() + "' została utworzona pomyślnie");
            return "redirect:/companies";
        } catch (Exception e) {
            log.error("Błąd podczas tworzenia firmy", e);
            model.addAttribute("errorMessage", "Błąd podczas tworzenia firmy: " + e.getMessage());
            model.addAttribute("isEdit", false);
            return "company/form";
        }
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToCompany(#id)")
    public String updateCompany(@PathVariable Long id,
            @ModelAttribute CompanyFormBean formBean,
            RedirectAttributes redirectAttributes,
            Model model) {
        log.debug("Aktualizacja firmy: {}", id);

        try {
            Company existing = companyService.getCompanyById(id);
            Map<String, Object> oldSnapshot = Map.of("name", existing.getName(),
                    "address", existing.getAddress() != null ? existing.getAddress() : "");
            Company updated = companyService.updateCompany(id, formBean.getName(), formBean.getAddress());
            log.info("Zaktualizowano firmę: {} (ID: {})", updated.getName(), updated.getId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Firma '" + updated.getName() + "' została zaktualizowana pomyślnie");
            return "redirect:/companies/" + id;
        } catch (Exception e) {
            log.error("Błąd podczas aktualizacji firmy", e);
            model.addAttribute("errorMessage", "Błąd podczas aktualizacji firmy: " + e.getMessage());
            model.addAttribute("companyId", id);
            model.addAttribute("isEdit", true);
            return "company/form";
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public String deleteCompany(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.debug("Usuwanie firmy: {}", id);

        try {
            Company company = companyService.getCompanyById(id);
            companyService.deleteCompany(id);
            log.info("Usunięto firmę: {} (ID: {})", company.getName(), id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Firma '" + company.getName() + "' została usunięta pomyślnie");
        } catch (Exception e) {
            log.error("Błąd podczas usuwania firmy: {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Błąd podczas usuwania firmy: " + e.getMessage());
        }

        return "redirect:/companies";
    }

    /**
     * Envers: lista rewizji dla firmy (AJAX/JSON)
     */
    @GetMapping("/{id}/history")
    @ResponseBody
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToCompany(#id)")
    public List<RevisionInfoDto> getCompanyRevisions(@PathVariable Long id) {
        log.debug("Pobieranie historii Envers dla firmy ID: {}", id);
        return enversRevisionService.getRevisionHistory(Company.class, id);
    }

    /**
     * Envers: diff pól między rewizją N a N-1 (AJAX/JSON)
     */
    @GetMapping("/{id}/history/{revNum}")
    @ResponseBody
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToCompany(#id)")
    public List<FieldDiffDto> getCompanyRevisionDiff(@PathVariable Long id, @PathVariable int revNum) {
        log.debug("Pobieranie diff rewizji {} dla firmy ID: {}", revNum, id);
        Map<String, String> labels = Map.of(
                "name", "Nazwa firmy",
                "address", "Adres");
        return enversRevisionService.getDetailedDiff(Company.class, id, revNum, labels);
    }

    /**
     * Form bean do formularza firmy
     */
    public static class CompanyFormBean {
        private String name;
        private String address;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }
    }
}
