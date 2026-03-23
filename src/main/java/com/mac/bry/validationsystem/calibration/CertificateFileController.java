package com.mac.bry.validationsystem.calibration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequestMapping("/certificates")
@RequiredArgsConstructor
@Slf4j
public class CertificateFileController {

    private final CalibrationService calibrationService;

    @PreAuthorize("hasAnyRole('ADMIN', 'QUALITY_MANAGER', 'AUDITOR', 'OPERATIONAL_MANAGER')")
    @GetMapping("/{calibrationId}")
    public ResponseEntity<Resource> viewCertificate(@PathVariable Long calibrationId) {
        log.info("=== Wyświetlanie certyfikatu dla wzorcowania o id: {} ===", calibrationId);
        
        try {
            Calibration calibration = calibrationService.findById(calibrationId)
                    .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono wzorcowania"));
            
            log.info("Znaleziono wzorcowanie: {}", calibration);
            
            if (calibration.getCertificateFilePath() == null) {
                log.warn("Brak ścieżki do certyfikatu dla wzorcowania id: {}", calibrationId);
                return ResponseEntity.notFound().build();
            }
            
            log.info("Ścieżka certyfikatu z bazy: {}", calibration.getCertificateFilePath());
            
            
            Path filePath = Paths.get(calibration.getCertificateFilePath()).normalize();
            
            // Jeśli ścieżka jest względna, zamień na bezwzględną
            if (!filePath.isAbsolute()) {
                filePath = Paths.get(System.getProperty("user.dir"), calibration.getCertificateFilePath()).normalize();
            }

            // Path Traversal Mitigation: Ensure the resolved path is within the application directory
            Path baseDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            if (!filePath.toAbsolutePath().normalize().startsWith(baseDir)) {
                log.error("Zablokowano próbę Path Traversal dla ID {}: {}", calibrationId, filePath);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Dostęp do pliku zabroniony");
            }
            
            log.info("Próba odczytu certyfikatu z: {}", filePath.toAbsolutePath());
            
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                log.info("Certyfikat znaleziony i czytelny: {}", filePath);
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, 
                               "inline; filename=\"" + filePath.getFileName() + "\"")
                        .body(resource);
            } else {
                log.error("Plik certyfikatu nie istnieje lub nie można go odczytać: {}", filePath.toAbsolutePath());
                log.error("Exists: {}, Readable: {}", resource.exists(), resource.isReadable());
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Błąd podczas wyświetlania certyfikatu dla calibration id: {}", calibrationId, e);
            return ResponseEntity.notFound().build();
        }
    }
}
