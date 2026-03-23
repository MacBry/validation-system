package com.mac.bry.validationsystem.security.controller;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.department.DepartmentRepository;
import com.mac.bry.validationsystem.laboratory.Laboratory;
import com.mac.bry.validationsystem.laboratory.LaboratoryRepository;
import com.mac.bry.validationsystem.security.PermissionType;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import com.mac.bry.validationsystem.security.repository.UserPermissionRepository;
import com.mac.bry.validationsystem.security.service.PermissionService;
import com.mac.bry.validationsystem.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'ADMIN')")
public class PermissionController {

    private final PermissionService permissionService;
    private final SecurityService securityService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final UserPermissionRepository userPermissionRepository;

    @GetMapping("/grant")
    public String showGrantForm(Model model, @RequestParam(required = false) Long userId,
            RedirectAttributes redirectAttributes) {
        if (userId != null && !securityService.hasAccessToUser(userId)) {
            redirectAttributes.addFlashAttribute("error", "Brak uprawnień do nadawania uprawnień temu użytkownikowi.");
            return "redirect:/users";
        }

        if (securityService.isSuperAdmin()) {
            model.addAttribute("users", userRepository.findAll());
            model.addAttribute("companies", companyRepository.findAll());
        } else {
            Set<Long> allowedCompanyIds = securityService.getAllowedCompanyIds();
            Set<Long> userIds = userPermissionRepository.findUserIdsByCompanyIds(allowedCompanyIds);
            model.addAttribute("users", userRepository.findAllById(userIds));
            model.addAttribute("companies", companyRepository.findAllById(allowedCompanyIds));
        }
        model.addAttribute("permissionTypes", PermissionType.values());

        if (userId != null) {
            model.addAttribute("userId", userId);
        }

        return "security/permissions/grant";
    }

    @PostMapping("/grant")
    public String grantPermission(
            @RequestParam Long userId,
            @RequestParam PermissionType permissionType,
            @RequestParam Long companyId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long laboratoryId,
            RedirectAttributes redirectAttributes) {

        if (!securityService.hasAccessToUser(userId) || !securityService.hasAccessToCompany(companyId)) {
            redirectAttributes.addFlashAttribute("error", "Próba nadania uprawnień poza zakresem kompetencji.");
            return "redirect:/users";
        }

        try {
            User currentUser = securityService.getCurrentUser();
            Long grantedById = currentUser != null ? currentUser.getId() : 1L;

            if (permissionType == PermissionType.FULL_COMPANY) {
                Company company = companyRepository.findById(companyId).orElseThrow();
                permissionService.grantFullCompanyAccess(userId, company, grantedById);
            } else if (permissionType == PermissionType.FULL_DEPARTMENT) {
                Department dept = departmentRepository.findById(departmentId).orElseThrow();
                permissionService.grantDepartmentAccess(userId, dept, grantedById);
            } else if (permissionType == PermissionType.SPECIFIC_LABORATORY) {
                Laboratory lab = laboratoryRepository.findById(laboratoryId).orElseThrow();
                permissionService.grantLaboratoryAccess(userId, lab, grantedById);
            }

            redirectAttributes.addFlashAttribute("success", "Uprawnienia zostały pomyślnie nadane.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Błąd podczas nadawania uprawnień: " + e.getMessage());
        }

        return "redirect:/users/" + userId;
    }

    @PostMapping("/{id}/revoke")
    public String revokePermission(@PathVariable Long id, @RequestParam Long userId,
            RedirectAttributes redirectAttributes) {
        if (!securityService.hasAccessToUser(userId)) {
            redirectAttributes.addFlashAttribute("error", "Brak uprawnień.");
            return "redirect:/users";
        }
        permissionService.revokePermission(id);
        redirectAttributes.addFlashAttribute("success", "Uprawnienie zostało pomyślnie cofnięte.");
        return "redirect:/users/" + userId;
    }

    // --- AJAX API ENDPOINTS ---

    @GetMapping("/api/departments")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getDepartmentsByCompany(@RequestParam Long companyId) {
        if (!securityService.hasAccessToCompany(companyId)) {
            return ResponseEntity.status(403).build();
        }
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null)
            return ResponseEntity.notFound().build();

        List<Map<String, Object>> depts = departmentRepository.findByCompanyOrderByNameAsc(company)
                .stream()
                .map(d -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", d.getId());
                    map.put("name", d.getName());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(depts);
    }

    @GetMapping("/api/laboratories")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getLaboratoriesByDepartment(@RequestParam Long departmentId) {
        Department department = departmentRepository.findById(departmentId).orElse(null);
        if (department == null)
            return ResponseEntity.notFound().build();

        List<Map<String, Object>> labs = laboratoryRepository.findByDepartmentOrderByFullNameAsc(department)
                .stream()
                .map(l -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", l.getId());
                    map.put("name", l.getFullName());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(labs);
    }
}
