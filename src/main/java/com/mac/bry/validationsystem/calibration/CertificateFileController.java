package com.mac.bry.validationsystem.calibration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequestMapping("/certificates")
@RequiredArgsConstructor
@Slf4j
public class CertificateFileController {

    private final CalibrationService calibrationService;

    @Value("${calibration.certificates.path:uploads/certificates}")
    private String allowedBasePath;

    @GetMapping("/{calibrationId}")
    public ResponseEntity<Resource> viewCertificate(@PathVariable Long calibrationId) {
        log.info("=== Wyswietlanie certyfikatu dla wzorcowania o id: {} ===", calibrationId);

        try {
            Calibration calibration = calibrationService.findById(calibrationId)
                    .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono wzorcowania"));

            if (calibration.getCertificateFilePath() == null) {
                log.warn("Brak sciezki do certyfikatu dla wzorcowania id: {}", calibrationId);
                return ResponseEntity.notFound().build();
            }

            // Security: Canonicalize and validate the file path to prevent path traversal
            Path baseDir = Paths.get(System.getProperty("user.dir"), allowedBasePath)
                    .toAbsolutePath().normalize();

            Path filePath = Paths.get(calibration.getCertificateFilePath());
            if (!filePath.isAbsolute()) {
                filePath = Paths.get(System.getProperty("user.dir"), calibration.getCertificateFilePath());
            }
            filePath = filePath.toAbsolutePath().normalize();

            // Verify the resolved path is within the allowed base directory
            if (!filePath.startsWith(baseDir)) {
                log.error("Path traversal attempt detected for calibration id: {}. " +
                          "Resolved path '{}' is outside allowed directory '{}'",
                          calibrationId, filePath, baseDir);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                log.info("Certyfikat znaleziony i czytelny: {}", filePath);
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                               "inline; filename=\"" + filePath.getFileName() + "\"")
                        .body(resource);
            } else {
                log.error("Plik certyfikatu nie istnieje lub nie mozna go odczytac: {}", filePath);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Blad podczas wyswietlania certyfikatu dla calibration id: {}", calibrationId, e);
            return ResponseEntity.notFound().build();
        }
    }
}
