package com.mac.bry.validationsystem.security.controller;

import com.mac.bry.validationsystem.security.ChangePasswordDto;
import com.mac.bry.validationsystem.security.ForcedPasswordChangeDto;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.service.AuditService;
import com.mac.bry.validationsystem.security.service.SecurityService;
import com.mac.bry.validationsystem.security.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.web.csrf.CsrfToken;

@Slf4j
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProfileController {

    private final UserService userService;
    private final SecurityService securityService;
    private final AuditService auditService;


    @GetMapping("/change-password")
    public String showChangePasswordForm(Model model) {
        log.debug("Wyświetlanie formularza zmiany hasła dla zalogowanego użytkownika");
        model.addAttribute("changePasswordDto", new ChangePasswordDto());
        return "security/profile/change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@Valid @ModelAttribute("changePasswordDto") ChangePasswordDto dto,
            BindingResult result,
            RedirectAttributes redirectAttributes) {
        log.debug("Próba zmiany hasła przez zalogowanego użytkownika");

        if (result.hasErrors()) {
            return "security/profile/change-password";
        }

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "error.changePasswordDto", "Hasła nie są identyczne");
            return "security/profile/change-password";
        }

        try {
            User currentUser = securityService.getCurrentUser();
            userService.changePasswordSelf(currentUser.getId(), dto.getCurrentPassword(), dto.getNewPassword());

            auditService.logOperation("User", currentUser.getId(), "CHANGE_PASSWORD_SELF", null, null);

            redirectAttributes.addFlashAttribute("success", "Hasło zostało pomyślnie zmienione.");
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            result.rejectValue("currentPassword", "error.changePasswordDto", e.getMessage());
            return "security/profile/change-password";
        } catch (Exception e) {
            log.error("Błąd podczas zmiany hasła", e);
            redirectAttributes.addFlashAttribute("error", "Wystąpił nieoczekiwany błąd podczas zmiany hasła.");
            return "redirect:/profile/change-password";
        }
    }

    // ========================================================================
    // FORCED PASSWORD CHANGE (First Login)
    // ========================================================================

    @GetMapping("/forced-change-password")
    public String showForcedPasswordChangeForm(Model model, HttpServletRequest request) {
        // Force CSRF token materialization (Spring Security 6 deferred loading)
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Calling getToken() forces the token to be generated and cookie to be set
            csrfToken.getToken();
            log.debug("CSRF token materialized for forced-change-password page");
        }

        User currentUser = securityService.getCurrentUser();

        // Sprawdź czy użytkownik rzeczywiście musi zmienić hasło (wymuszenie lub wygaśnięcie)
        if (!currentUser.mustChangePasswordNow()) {
            log.debug("Użytkownik {} nie musi zmieniać hasła - przekierowanie na stronę główną", currentUser.getUsername());
            return "redirect:/";
        }

        String reason = currentUser.isMustChangePassword() ? "wymuszenie administratora" : "wygaśnięcie hasła";
        log.debug("Wyświetlanie formularza wymuszonej zmiany hasła dla użytkownika: {} (powód: {})",
                currentUser.getUsername(), reason);

        model.addAttribute("forcedPasswordChangeDto", new ForcedPasswordChangeDto());
        model.addAttribute("user", currentUser);
        model.addAttribute("passwordExpired", currentUser.isPasswordExpired());
        model.addAttribute("daysUntilExpiry", currentUser.getDaysUntilPasswordExpiry());
        return "security/profile/forced-change-password";
    }

    @PostMapping("/forced-change-password")
    public String processForcedPasswordChange(
            @Valid @ModelAttribute("forcedPasswordChangeDto") ForcedPasswordChangeDto dto,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {

        User currentUser = securityService.getCurrentUser();

        // Sprawdź czy użytkownik rzeczywiście musi zmienić hasło (wymuszenie lub wygaśnięcie)
        if (!currentUser.mustChangePasswordNow()) {
            log.debug("Użytkownik {} nie musi zmieniać hasła - przekierowanie na stronę główną", currentUser.getUsername());
            return "redirect:/";
        }

        String reason = currentUser.isMustChangePassword() ? "wymuszenie" : "wygaśnięcie";
        log.debug("Przetwarzanie wymuszonej zmiany hasła dla użytkownika: {} (powód: {})",
                currentUser.getUsername(), reason);

        if (result.hasErrors()) {
            model.addAttribute("user", currentUser);
            model.addAttribute("passwordExpired", currentUser.isPasswordExpired());
            model.addAttribute("daysUntilExpiry", currentUser.getDaysUntilPasswordExpiry());
            return "security/profile/forced-change-password";
        }

        if (!dto.isPasswordsMatch()) {
            result.rejectValue("confirmPassword", "error.forcedPasswordChangeDto", "Hasła nie są identyczne");
            model.addAttribute("user", currentUser);
            model.addAttribute("passwordExpired", currentUser.isPasswordExpired());
            model.addAttribute("daysUntilExpiry", currentUser.getDaysUntilPasswordExpiry());
            return "security/profile/forced-change-password";
        }

        try {
            userService.changePasswordForced(currentUser.getId(), dto.getNewPassword());

            auditService.logOperation("User", currentUser.getId(), "FORCED_PASSWORD_CHANGE_COMPLETED", null, null);

            // Refresh the User principal in SecurityContext so ForcedPasswordChangeFilter
            // sees the updated mustChangePassword=false on subsequent requests
            User freshUser = userService.findById(currentUser.getId());
            if (freshUser != null) {
                org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null) {
                    org.springframework.security.authentication.UsernamePasswordAuthenticationToken newAuth =
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            freshUser, auth.getCredentials(), auth.getAuthorities());
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(newAuth);
                    log.debug("SecurityContext updated with fresh user principal (mustChangePassword={})", 
                              freshUser.isMustChangePassword());
                }
            }

            redirectAttributes.addFlashAttribute("success",
                "Hasło zostało pomyślnie zmienione. Możesz teraz korzystać z systemu.");

            log.info("Użytkownik {} pomyślnie zmienił hasło przy pierwszym logowaniu", currentUser.getUsername());
            return "redirect:/";


        } catch (IllegalArgumentException e) {
            result.rejectValue("newPassword", "error.forcedPasswordChangeDto", e.getMessage());
            model.addAttribute("user", currentUser);
            model.addAttribute("passwordExpired", currentUser.isPasswordExpired());
            model.addAttribute("daysUntilExpiry", currentUser.getDaysUntilPasswordExpiry());
            return "security/profile/forced-change-password";
        } catch (Exception e) {
            log.error("Błąd podczas wymuszonej zmiany hasła dla użytkownika: {}", currentUser.getUsername(), e);
            redirectAttributes.addFlashAttribute("error",
                "Wystąpił nieoczekiwany błąd podczas zmiany hasła. Spróbuj ponownie.");
            return "redirect:/profile/forced-change-password";
        }
    }
}
