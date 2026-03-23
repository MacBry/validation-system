package com.mac.bry.validationsystem.security.controller;

import com.mac.bry.validationsystem.security.AuditLog;
import com.mac.bry.validationsystem.security.repository.AuditLogRepository;
import com.mac.bry.validationsystem.security.repository.UserPermissionRepository;
import com.mac.bry.validationsystem.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import java.util.Set;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'ADMIN')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;
    private final UserPermissionRepository userPermissionRepository;

    @GetMapping
    public String viewAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String ipAddress,
            Model model) {

        // Konwersja pustych stringów na null dla poprawnego działania Query
        String userFilter = (username != null && !username.trim().isEmpty()) ? username.trim() : null;
        String ipFilter = (ipAddress != null && !ipAddress.trim().isEmpty()) ? ipAddress.trim() : null;

        Page<AuditLog> auditPage;

        if (securityService.isSuperAdmin()) {
            auditPage = auditLogRepository.findWithFilters(
                    userFilter, ipFilter, PageRequest.of(page, size));
        } else {
            Set<Long> companyIds = securityService.getAllowedCompanyIds();
            if (companyIds != null && !companyIds.isEmpty()) {
                Set<Long> userIds = userPermissionRepository.findUserIdsByCompanyIds(companyIds);
                auditPage = auditLogRepository.findWithFiltersAndUserIds(
                        userIds, userFilter, ipFilter, PageRequest.of(page, size));
            } else {
                auditPage = Page.empty();
            }
        }

        model.addAttribute("auditPage", auditPage);
        model.addAttribute("usernameFilter", userFilter);
        model.addAttribute("ipAddressFilter", ipFilter);
        model.addAttribute("currentPage", auditPage.getNumber());
        model.addAttribute("totalPages", auditPage.getTotalPages());
        model.addAttribute("pageSize", size);

        return "security/audit/list";
    }
}
