package com.mac.bry.validationsystem;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyService;
import com.mac.bry.validationsystem.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dodaje kontekst firmy do każdego modelu — wyświetlany w górnym pasku nawigacji.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class NavContextAdvice {

    private final SecurityService securityService;
    private final CompanyService companyService;

    @org.springframework.beans.factory.annotation.Value("${app.security.session.timeout-minutes:30}")
    private int sessionTimeoutMinutes;

    @ModelAttribute
    public void addNavCompanyContext(Model model) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()
                    || "anonymousUser".equals(auth.getPrincipal())) {
                return;
            }

            if (securityService.isSuperAdmin()) {
                model.addAttribute("navCompanyContext", "Wszystkie organizacje");
                model.addAttribute("navIsSuperAdmin", true);
                return;
            }

            Set<Long> companyIds = securityService.getAllCompanyIdsWithAnyAccess();
            if (companyIds == null || companyIds.isEmpty()) {
                return;
            }

            List<Company> companies = companyService.getAllowedCompanies(companyIds);
            if (!companies.isEmpty()) {
                String names = companies.stream()
                        .map(Company::getName)
                        .collect(Collectors.joining(", "));
                model.addAttribute("navCompanyContext", names);
            }

            // Dodaj czas wygaśnięcia sesji w sekundach dla session-timer.js
            model.addAttribute("sessionTimeoutSeconds", sessionTimeoutMinutes * 60);
        } catch (Exception ignored) {
            // Brak kontekstu bezpieczeństwa (np. statyczne zasoby)
        }
    }
}
