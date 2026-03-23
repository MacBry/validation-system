package com.mac.bry.validationsystem.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO dla formularza wymuszonej zmiany hasła przy pierwszym logowaniu
 */
@Getter
@Setter
public class ForcedPasswordChangeDto {

    @NotBlank(message = "Nowe hasło jest wymagane")
    private String newPassword;

    @NotBlank(message = "Potwierdzenie nowego hasła jest wymagane")
    private String confirmPassword;

    /**
     * Sprawdza czy hasła są identyczne
     */
    public boolean isPasswordsMatch() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}