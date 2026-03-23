package com.mac.bry.validationsystem.security.controller;

import com.mac.bry.validationsystem.security.Role;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.RoleRepository;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import com.mac.bry.validationsystem.security.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import com.mac.bry.validationsystem.security.service.AuditService;
import com.mac.bry.validationsystem.security.service.SecurityService;
import com.mac.bry.validationsystem.security.service.PermissionService;
import com.mac.bry.validationsystem.security.repository.UserPermissionRepository;
import com.mac.bry.validationsystem.security.PermissionType;
import com.mac.bry.validationsystem.company.CompanyRepository;
import com.mac.bry.validationsystem.company.Company;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.mac.bry.validationsystem.security.AuditLog;
import com.mac.bry.validationsystem.department.DepartmentRepository;
import com.mac.bry.validationsystem.laboratory.LaboratoryRepository;

@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'ADMIN')")
@Slf4j
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;
    private final SecurityService securityService;
    private final UserPermissionRepository userPermissionRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final PermissionService permissionService;

    @GetMapping
    public String listUsers(Model model) {
        if (securityService.isSuperAdmin()) {
            model.addAttribute("users", userRepository.findAll());
        } else {
            Set<Long> allowedCompanyIds = securityService.getAllowedCompanyIds();
            if (allowedCompanyIds != null && !allowedCompanyIds.isEmpty()) {
                Set<Long> userIds = userPermissionRepository.findUserIdsByCompanyIds(allowedCompanyIds);
                model.addAttribute("users", userRepository.findAllById(userIds));
            } else {
                model.addAttribute("users", List.of());
            }
        }
        return "security/users/list";
    }

    @GetMapping("/{id}")
    public String showUserDetails(@PathVariable Long id,
                                @RequestParam(value = "page", defaultValue = "0") int page,
                                Model model, RedirectAttributes redirectAttributes) {
        if (!securityService.hasAccessToUser(id)) {
            redirectAttributes.addFlashAttribute("error", "Brak uprawnień do podglądu tego użytkownika.");
            return "redirect:/users";
        }

        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user ID"));
        model.addAttribute("user", user);

        // Paginacja dla audytu - 20 wpisów na stronę
        Pageable pageable = PageRequest.of(page, 20);
        Page<AuditLog> auditPage = auditService.getRelatedLogsForUserWithPagination(id, pageable);

        model.addAttribute("auditLogs", auditPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", auditPage.getTotalPages());
        model.addAttribute("totalElements", auditPage.getTotalElements());
        model.addAttribute("hasNextPage", auditPage.hasNext());
        model.addAttribute("hasPreviousPage", auditPage.hasPrevious());

        return "security/users/details";
    }

    @GetMapping("/new")
    @Transactional(readOnly = true)
    public String showCreateForm(Model model) {
        log.info("Accessing /users/new endpoint");
        model.addAttribute("user", new User());

        List<Role> roles = roleRepository.findAll();
        if (securityService.isCompanyAdmin() && !securityService.isSuperAdmin()) {
            // Company Admin nie może nadawać uprawnień SUPER_ADMIN
            roles = roles.stream()
                    .filter(r -> !"ROLE_SUPER_ADMIN".equals(r.getName()))
                    .collect(Collectors.toList());
        }
        model.addAttribute("allRoles", roles);
        addPermissionDataToModel(model);
        return "security/users/form";
    }

    @Transactional(readOnly = true)
    private void addPermissionDataToModel(Model model) {
        User currentUser = securityService.getCurrentUser();
        String username = (currentUser != null) ? currentUser.getUsername() : "anonymous";
        log.info("Populating permission data to model for user: {}", username);
        
        if (securityService.isSuperAdmin()) {
            log.info("User is Super Admin, loading all companies/depts/labs");
            model.addAttribute("companies", companyRepository.findAll());
            model.addAttribute("departments", departmentRepository.findAll());
            model.addAttribute("laboratories", laboratoryRepository.findAll());
        } else {
            Set<Long> allowedCompanyIds = securityService.getAllowedCompanyIds();
            log.info("User is NOT Super Admin. Allowed company IDs: {}", allowedCompanyIds);
            
            if (allowedCompanyIds == null || allowedCompanyIds.isEmpty()) {
                log.warn("No allowed company IDs found for user!");
                model.addAttribute("companies", List.of());
                model.addAttribute("departments", List.of());
                model.addAttribute("laboratories", List.of());
                return;
            }
            
            model.addAttribute("companies", companyRepository.findAllById(allowedCompanyIds));
            
            // Filter departments by allowed companies (with null checks)
            log.info("Filtering departments...");
            List<com.mac.bry.validationsystem.department.Department> depts = departmentRepository.findAll().stream()
                .filter(d -> d.getCompany() != null && allowedCompanyIds.contains(d.getCompany().getId()))
                .collect(Collectors.toList());
            log.info("Found {} departments for allowed companies", depts.size());
            model.addAttribute("departments", depts);
            
            Set<Long> allowedDeptIds = depts.stream()
                .map(com.mac.bry.validationsystem.department.Department::getId)
                .collect(Collectors.toSet());
            
            log.info("Filtering laboratories for {} departments...", allowedDeptIds.size());
            model.addAttribute("laboratories", laboratoryRepository.findAll().stream()
                .filter(l -> l.getDepartment() != null && allowedDeptIds.contains(l.getDepartment().getId()))
                .collect(Collectors.toList()));
            log.info("Finished populating permission data");
        }
    }

    @PostMapping
    public String createUser(@ModelAttribute User user,
            @RequestParam(value = "roleIds", required = false) List<Long> roleIds,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Nazwa użytkownika już istnieje!");
            return "redirect:/users/new";
        }

        if (roleIds != null) {
            List<Role> selectedRoles = roleRepository.findAllById(roleIds);
            if (securityService.isCompanyAdmin() && !securityService.isSuperAdmin()) {
                // Walidacja czy Company Admin nie próbuje nadać ROLE_SUPER_ADMIN
                boolean hasSuperAdmin = selectedRoles.stream()
                        .anyMatch(r -> "ROLE_SUPER_ADMIN".equals(r.getName()));
                if (hasSuperAdmin) {
                    redirectAttributes.addFlashAttribute("error", "Brak uprawnień do nadawania tej roli.");
                    return "redirect:/users/new";
                }
            }
            user.setRoles(new HashSet<>(selectedRoles));
        }

        user.setEnabled(true);
        final User createdUser = userService.createUser(user);

        // PRZYPISANIE UPRAWNIEŃ (Firma / Dział / Pracownia)
        String permType = (String) allParams.get("permissionType");
        if (permType != null) {
            Long currentUserId = securityService.getCurrentUser().getId();
            if ("COMPANY".equals(permType) && allParams.containsKey("companyId")) {
                Long companyId = Long.parseLong((String) allParams.get("companyId"));
                companyRepository.findById(companyId).ifPresent(c -> 
                    permissionService.grantFullCompanyAccess(createdUser.getId(), c, currentUserId));
            } else if ("DEPARTMENT".equals(permType) && allParams.containsKey("deptId")) {
                Long deptId = Long.parseLong((String) allParams.get("deptId"));
                departmentRepository.findById(deptId).ifPresent(d -> 
                    permissionService.grantDepartmentAccess(createdUser.getId(), d, currentUserId));
            } else if ("LABORATORY".equals(permType) && allParams.containsKey("labId")) {
                Long labId = Long.parseLong((String) allParams.get("labId"));
                laboratoryRepository.findById(labId).ifPresent(l -> 
                    permissionService.grantLaboratoryAccess(createdUser.getId(), l, currentUserId));
            }
        } else if (securityService.isCompanyAdmin()) {
            // Fallback: AUTOMATYCZNE PRZYPISANIE DO FIRMY DLA COMPANY ADMINA (stara logika)
            Set<Long> companyIds = securityService.getAllowedCompanyIds();
            if (companyIds != null && !companyIds.isEmpty()) {
                Long companyId = companyIds.iterator().next();
                companyRepository.findById(companyId).ifPresent(c -> 
                    permissionService.grantFullCompanyAccess(createdUser.getId(), c, securityService.getCurrentUser().getId()));
            }
        }

        auditService.logOperation("User", createdUser.getId(), "CREATE", null,
                Map.of("username", createdUser.getUsername(), "email", createdUser.getEmail()));

        redirectAttributes.addFlashAttribute("success", "Użytkownik utworzony pomyślnie.");
        return "redirect:/users";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        if (!securityService.hasAccessToUser(id)) {
            redirectAttributes.addFlashAttribute("error", "Brak uprawnień do edycji tego użytkownika.");
            return "redirect:/users";
        }

        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user ID"));
        model.addAttribute("user", user);

        List<Role> roles = roleRepository.findAll();
        if (securityService.isCompanyAdmin() && !securityService.isSuperAdmin()) {
            roles = roles.stream()
                    .filter(r -> !"ROLE_SUPER_ADMIN".equals(r.getName()))
                    .collect(Collectors.toList());
        }
        model.addAttribute("allRoles", roles);
        addPermissionDataToModel(model);
        return "security/users/form";
    }

    @PostMapping("/{id}")
    public String updateUser(@PathVariable Long id, @ModelAttribute User userDetails,
            @RequestParam(value = "roleIds", required = false) List<Long> roleIds,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {
        if (!securityService.hasAccessToUser(id)) {
            redirectAttributes.addFlashAttribute("error", "Brak uprawnień do edycji tego użytkownika.");
            return "redirect:/users";
        }

        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user ID"));

        user.setFirstName(userDetails.getFirstName());
        user.setLastName(userDetails.getLastName());
        user.setEmail(userDetails.getEmail());

        if (roleIds != null) {
            List<Role> selectedRoles = roleRepository.findAllById(roleIds);
            if (securityService.isCompanyAdmin() && !securityService.isSuperAdmin()) {
                boolean hasSuperAdmin = selectedRoles.stream()
                        .anyMatch(r -> "ROLE_SUPER_ADMIN".equals(r.getName()));
                if (hasSuperAdmin) {
                    redirectAttributes.addFlashAttribute("error", "Brak uprawnień do nadawania roli Super Admina.");
                    return "redirect:/users/" + id + "/edit";
                }
            }
            user.setRoles(new HashSet<>(selectedRoles));
        } else {
            user.getRoles().clear();
        }

        userRepository.save(user);

        auditService.logOperation("User", id, "UPDATE",
                Map.of("email", userDetails.getEmail(), "firstName", userDetails.getFirstName()),
                Map.of("email", user.getEmail(), "firstName", user.getFirstName()));

        redirectAttributes.addFlashAttribute("success", "Dane użytkownika zaktualizowane.");
        return "redirect:/users";
    }

    @PostMapping("/{id}/toggle-enabled")
    public String toggleEnabled(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (!securityService.hasAccessToUser(id)) {
            redirectAttributes.addFlashAttribute("error", "Brak uprawnień.");
            return "redirect:/users";
        }
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user ID"));
        boolean oldState = user.isEnabled();
        userService.setUserEnabled(id, !oldState);

        auditService.logOperation("User", id, "TOGGLE_ENABLED",
                Map.of("enabled", oldState),
                Map.of("enabled", !oldState));

        redirectAttributes.addFlashAttribute("success", "Zmieniono status aktywności konta.");
        return "redirect:/users";
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, @RequestParam("newPassword") String newPassword,
            RedirectAttributes redirectAttributes) {
        if (!securityService.hasAccessToUser(id)) {
            redirectAttributes.addFlashAttribute("error", "Brak uprawnień.");
            return "redirect:/users";
        }
        
        try {
            userService.changePassword(id, newPassword);
            auditService.logOperation("User", id, "RESET_PASSWORD", null, null);
            redirectAttributes.addFlashAttribute("success", "Hasło zresetowane pomyślnie.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Błąd resetowania hasła: " + e.getMessage());
            log.warn("Nieudana próba resetu hasła dla użytkownika {}: {}", id, e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Wystąpił nieoczekiwany błąd podczas resetowania hasła.");
            log.error("Błąd systemowy przy resecie hasła dla użytkownika {}: ", id, e);
        }

        return "redirect:/users";
    }

    @PostMapping("/{id}/unlock")
    public String unlockUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (!securityService.hasAccessToUser(id)) {
            redirectAttributes.addFlashAttribute("error", "Brak uprawnień.");
            return "redirect:/users";
        }
        userService.resetFailedLoginAttempts(id);

        auditService.logOperation("User", id, "UNLOCK", null, null);

        redirectAttributes.addFlashAttribute("success", "Konto zostało pomyślnie odblokowane.");
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (!securityService.hasAccessToUser(id)) {
            redirectAttributes.addFlashAttribute("error", "Brak uprawnień do usunięcia tego użytkownika.");
            return "redirect:/users";
        }

        // GMP COMPLIANCE: Soft-delete instead of hard-delete to maintain audit trail
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user ID"));

        // Disable user account instead of physical deletion
        userService.setUserEnabled(id, false);

        auditService.logOperation("User", id, "SOFT_DELETE",
            Map.of("username", user.getUsername(), "enabled", true),
            Map.of("enabled", false));

        redirectAttributes.addFlashAttribute("success", "Użytkownik został dezaktywowany zgodnie z polityką GMP.");
        return "redirect:/users";
    }
}
