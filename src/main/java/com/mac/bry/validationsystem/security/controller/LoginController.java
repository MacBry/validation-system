package com.mac.bry.validationsystem.security.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "expired", required = false) String expired,
            Model model,
            HttpServletRequest request) {

        if (error != null) {
            // Read the specific error message set by CustomAuthenticationFailureHandler
            HttpSession session = request.getSession(false);
            String errorMessage = null;
            if (session != null) {
                errorMessage = (String) session.getAttribute("errorMessage");
                session.removeAttribute("errorMessage"); // Clean up after reading
            }
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = "Nieprawidłowa nazwa użytkownika lub hasło.";
            }
            model.addAttribute("error", errorMessage);
        }

        if (logout != null) {
            model.addAttribute("msg", "Zostałeś pomyślnie wylogowany.");
        }

        if (expired != null) {
            model.addAttribute("error", "Twoja sesja wygasła. Zaloguj się ponownie.");
        }

        return "auth/login";
    }
}
