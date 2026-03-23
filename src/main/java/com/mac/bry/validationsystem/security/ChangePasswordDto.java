package com.mac.bry.validationsystem.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO dla formularza zmiany hasła przez użytkownika
 */
@Getter
@Setter
public class ChangePasswordDto {

    @NotBlank(message = "Aktualne hasło jest wymagane")
    private String currentPassword;

    @NotBlank(message = "Nowe hasło jest wymagane")
    private String newPassword;

    @NotBlank(message = "Potwierdzenie nowego hasła jest wymagane")
    private String confirmPassword;
}
