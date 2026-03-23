package com.mac.bry.validationsystem.security.controller;

import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.service.EmailService;
import com.mac.bry.validationsystem.security.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private final UserService userService;
    private final EmailService emailService;

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam("email") String email,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        String token = userService.generatePasswordResetToken(email);

        if (token != null) {
            String appUrl = getAppUrl(request);
            String resetLink = appUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(email, resetLink);
        }

        // Zawsze wyswietlaj ten sam komunikat, aby chronić prywatność uzytkowników
        redirectAttributes.addFlashAttribute("msg",
                "Jeśli konto z podanym adresem e-mail istnieje, instrukcje resetowania hasła zostały wysłane.");
        return "redirect:/login";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam("token") String token, Model model,
            RedirectAttributes redirectAttributes) {
        Optional<User> userOptional = userService.getByPasswordResetToken(token);

        if (userOptional.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Link do resetowania hasła jest nieprawidłowy lub wygasł.");
            return "redirect:/login";
        }

        model.addAttribute("token", token);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam("token") String token,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            RedirectAttributes redirectAttributes) {

        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Hasła nie są identyczne.");
            return "redirect:/reset-password?token=" + token;
        }

        try {
            userService.resetPassword(token, password);
            redirectAttributes.addFlashAttribute("msg",
                    "Twoje hasło zostało pomyślnie zresetowane. Możesz się teraz zalogować.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/reset-password?token=" + token;
        }
    }

    private String getAppUrl(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                + request.getContextPath();
    }
}
