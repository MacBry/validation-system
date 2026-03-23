package com.mac.bry.validationsystem.certificates;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyService;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.service.AuditService;
import com.mac.bry.validationsystem.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Slf4j
@Controller
@RequestMapping("/certificates")
@RequiredArgsConstructor
public class CompanyCertificateController {

    private final CompanyCertificateService certificateService;
    private final CompanyService companyService;
    private final SecurityService securityService;
    private final AuditService auditService;

    /**
     * Lista certyfikatów — SUPER_ADMIN widzi wszystkie firmy, COMPANY_ADMIN tylko swoje.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_COMPANY_ADMIN')")
    public String list(Model model) {
        Set<Long> allowedIds = securityService.getAllowedCompanyIds();
        List<Company> companies = (allowedIds == null)
                ? companyService.getAllCompanies()
                : companyService.getAllowedCompanies(allowedIds);

        Map<Long, CompanyCertificate> activeCerts = new LinkedHashMap<>();
        for (Company c : companies) {
            certificateService.findActive(c.getId()).ifPresent(cert -> activeCerts.put(c.getId(), cert));
        }

        model.addAttribute("companies", companies);
        model.addAttribute("activeCerts", activeCerts);
        return "certificates/list";
    }

    /**
     * Formularz wgrywania certyfikatu dla firmy.
     */
    @GetMapping("/company/{companyId}/upload")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToCompany(#companyId)")
    public String uploadForm(@PathVariable Long companyId, Model model) {
        Company company = companyService.getCompanyById(companyId);
        model.addAttribute("company", company);
        return "certificates/upload";
    }

    /**
     * Obsługa wgrywania pliku .p12/.pfx.
     */
    @PostMapping("/company/{companyId}/upload")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToCompany(#companyId)")
    public String upload(@PathVariable Long companyId,
                         @RequestParam MultipartFile keystoreFile,
                         @RequestParam String keystorePassword,
                         @AuthenticationPrincipal User currentUser,
                         RedirectAttributes ra) {

        if (keystoreFile.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Nie wybrano pliku certyfikatu.");
            return "redirect:/certificates/company/" + companyId + "/upload";
        }

        String originalName = keystoreFile.getOriginalFilename();
        if (originalName == null || (!originalName.toLowerCase().endsWith(".p12")
                && !originalName.toLowerCase().endsWith(".pfx"))) {
            ra.addFlashAttribute("errorMessage", "Plik musi mieć rozszerzenie .p12 lub .pfx.");
            return "redirect:/certificates/company/" + companyId + "/upload";
        }

        try {
            byte[] bytes = keystoreFile.getBytes();
            Long userId = currentUser != null ? currentUser.getId() : null;
            CompanyCertificate cert = certificateService.upload(companyId, bytes, keystorePassword, userId);

            auditService.logOperation("CompanyCertificate", cert.getId(), "UPLOAD",
                    null, Map.of("companyId", companyId, "subject", cert.getSubject(),
                            "serialNumber", cert.getSerialNumber()));

            ra.addFlashAttribute("successMessage",
                    "Certyfikat został wgrany pomyślnie. CN: " + cert.getSubject());
            return "redirect:/certificates";

        } catch (IllegalArgumentException e) {
            log.warn("Błąd podczas wgrywania certyfikatu dla firmy {}: {}", companyId, e.getMessage());
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/certificates/company/" + companyId + "/upload";
        } catch (Exception e) {
            log.error("Nieoczekiwany błąd podczas wgrywania certyfikatu dla firmy {}", companyId, e);
            ra.addFlashAttribute("errorMessage", "Błąd podczas wgrywania certyfikatu: " + e.getMessage());
            return "redirect:/certificates/company/" + companyId + "/upload";
        }
    }

    /**
     * Dezaktywacja certyfikatu.
     */
    @PostMapping("/{certId}/deactivate")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or @securityService.hasAccessToCompany(#companyId)")
    public String deactivate(@PathVariable Long certId,
                              @RequestParam Long companyId,
                              RedirectAttributes ra) {
        try {
            certificateService.deactivate(certId);
            auditService.logOperation("CompanyCertificate", certId, "DEACTIVATE",
                    Map.of("active", true), Map.of("active", false));
            ra.addFlashAttribute("successMessage", "Certyfikat został dezaktywowany.");
        } catch (Exception e) {
            log.error("Błąd dezaktywacji certyfikatu ID={}", certId, e);
            ra.addFlashAttribute("errorMessage", "Błąd dezaktywacji: " + e.getMessage());
        }
        return "redirect:/certificates";
    }
}
