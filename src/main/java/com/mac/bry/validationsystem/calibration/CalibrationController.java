package com.mac.bry.validationsystem.calibration;

import com.mac.bry.validationsystem.security.service.AuditService;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/recorders/{recorderId}/calibrations")
@RequiredArgsConstructor
@Slf4j
public class CalibrationController {

    private final CalibrationService calibrationService;
    private final ThermoRecorderService thermoRecorderService;
    private final AuditService auditService;

    @PostMapping
    public String addCalibration(@PathVariable Long recorderId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate calibrationDate,
            @RequestParam String certificateNumber,
            @RequestParam(required = false) MultipartFile certificateFile,
            @RequestParam(required = false) List<BigDecimal> temperatures,
            @RequestParam(required = false) List<BigDecimal> errors,
            @RequestParam(required = false) List<BigDecimal> uncertainties,
            RedirectAttributes redirectAttributes) {
        log.debug("Dodawanie wzorcowania do rejestratora o id: {}", recorderId);

        try {
            List<CalibrationPointDto> points = mapPoints(temperatures, errors, uncertainties);
            Calibration saved = calibrationService.addCalibration(recorderId, calibrationDate, certificateNumber,
                    certificateFile, points);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Wzorcowanie zostało pomyślnie dodane");
        } catch (IOException e) {
            log.error("Błąd podczas zapisywania pliku certyfikatu", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Błąd podczas zapisywania pliku certyfikatu");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Błąd podczas dodawania wzorcowania", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Nie można dodać wzorcowania");
        }

        return "redirect:/recorders/" + recorderId;
    }

    @PostMapping("/{calibrationId}/delete")
    public String removeCalibration(@PathVariable Long recorderId,
            @PathVariable Long calibrationId,
            RedirectAttributes redirectAttributes) {
        log.debug("Usuwanie wzorcowania o id: {} z rejestratora o id: {}", calibrationId, recorderId);

        try {
            calibrationService.removeCalibration(recorderId, calibrationId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Wzorcowanie zostało pomyślnie usunięte");
        } catch (Exception e) {
            log.error("Błąd podczas usuwania wzorcowania", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Nie można usunąć wzorcowania");
        }

        return "redirect:/recorders/" + recorderId;
    }

    @PostMapping("/{calibrationId}/update")
    public String updateCalibration(@PathVariable Long recorderId,
            @PathVariable Long calibrationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate calibrationDate,
            @RequestParam String certificateNumber,
            @RequestParam(required = false) MultipartFile certificateFile,
            @RequestParam(required = false) List<BigDecimal> temperatures,
            @RequestParam(required = false) List<BigDecimal> errors,
            @RequestParam(required = false) List<BigDecimal> uncertainties,
            RedirectAttributes redirectAttributes) {
        log.debug("Aktualizacja wzorcowania o id: {}", calibrationId);

        try {
            List<CalibrationPointDto> points = mapPoints(temperatures, errors, uncertainties);
            calibrationService.updateCalibration(calibrationId, calibrationDate, certificateNumber, certificateFile,
                    points);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Wzorcowanie zostało pomyślnie zaktualizowane");
        } catch (IOException e) {
            log.error("Błąd podczas zapisywania pliku certyfikatu", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Błąd podczas zapisywania pliku certyfikatu");
        } catch (Exception e) {
            log.error("Błąd podczas aktualizacji wzorcowania", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Nie można zaktualizować wzorcowania");
        }

        return "redirect:/recorders/" + recorderId;
    }

    private List<CalibrationPointDto> mapPoints(List<BigDecimal> temperatures, List<BigDecimal> errors,
            List<BigDecimal> uncertainties) {
        List<CalibrationPointDto> points = new ArrayList<>();
        if (temperatures != null) {
            for (int i = 0; i < temperatures.size(); i++) {
                if (temperatures.get(i) == null)
                    continue;

                points.add(CalibrationPointDto.builder()
                        .temperatureValue(temperatures.get(i))
                        .systematicError(errors != null && errors.size() > i ? errors.get(i) : BigDecimal.ZERO)
                        .uncertainty(uncertainties != null && uncertainties.size() > i ? uncertainties.get(i)
                                : BigDecimal.ZERO)
                        .build());
            }
        }
        return points;
    }
}
