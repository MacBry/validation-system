package com.mac.bry.validationsystem.security.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Kontroler API do zarządzania sesją użytkownika.
 * Udostępnia endpointy do przedłużania ważności sesji bez przeładowania strony.
 */
@RestController
@RequestMapping("/api/session")
public class SessionApiController {

    /**
     * Endpoint typu ping, którego jedynym zadaniem jest odświeżenie 
     * czasu ostatniego dostępu do sesji na serwerze.
     * 
     * @return Status OK z informacją o powodzeniu.
     */
    @PostMapping("/ping")
    public ResponseEntity<?> ping() {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Session extended",
            "timestamp", System.currentTimeMillis()
        ));
    }
}
