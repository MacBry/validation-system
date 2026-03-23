package com.mac.bry.validationsystem;

import com.mac.bry.validationsystem.device.CoolingDeviceService;
import com.mac.bry.validationsystem.measurement.MeasurementSeriesService;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorderService;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final CoolingDeviceService coolingDeviceService;
    private final ThermoRecorderService thermoRecorderService;
    private final MeasurementSeriesService measurementSeriesService;
    private final ValidationService validationService;

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        try {
            long deviceCount = coolingDeviceService.getAllAccessibleDevices().size();
            model.addAttribute("deviceCount", deviceCount);
        } catch (Exception e) {
            log.warn("Could not load device count: {}", e.getMessage());
            model.addAttribute("deviceCount", null);
        }

        try {
            long recorderCount = thermoRecorderService.getAllAccessibleRecorders().size();
            model.addAttribute("recorderCount", recorderCount);
        } catch (Exception e) {
            log.warn("Could not load recorder count: {}", e.getMessage());
            model.addAttribute("recorderCount", null);
        }

        try {
            long seriesCount = measurementSeriesService.countAccessibleSeries();
            long pointsCount = measurementSeriesService.countAccessibleMeasurementPoints();
            model.addAttribute("seriesCount", seriesCount);
            model.addAttribute("measurementPointsCount", pointsCount);
        } catch (Exception e) {
            log.warn("Could not load measurement series stats: {}", e.getMessage());
            model.addAttribute("seriesCount", null);
            model.addAttribute("measurementPointsCount", null);
        }

        try {
            List<Validation> allValidations = validationService.getAllAccessibleValidations();
            long validationCount = allValidations.size();
            model.addAttribute("validationCount", validationCount);

            long completedCount = allValidations.stream()
                    .filter(v -> v.getStatus() != null
                            && "COMPLETED".equals(v.getStatus().name()))
                    .count();
            int compliance = validationCount > 0
                    ? (int) Math.round((completedCount * 100.0) / validationCount)
                    : 0;
            model.addAttribute("validationCompliance", compliance);

            // ✅ Mapujemy encje do płaskich Map, żeby uniknąć LazyInitializationException
            // podczas renderowania szablonu Thymeleaf (sesja JPA jest już zamknięta)
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            List<Map<String, Object>> recent = new ArrayList<>();
            allValidations.stream().limit(5).forEach(v -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", v.getId());
                row.put("createdAt", v.getCreatedDate() != null ? v.getCreatedDate().format(fmt) : "-");
                row.put("deviceName", v.getCoolingDevice() != null ? v.getCoolingDevice().getName() : "-");
                // Validation entity does not have validationType/performedBy — use RPW number as type
                row.put("validationType", v.getValidationPlanNumber() != null ? v.getValidationPlanNumber() : "-");
                row.put("statusName", v.getStatus() != null ? v.getStatus().name() : "DRAFT");
                row.put("statusDisplay", v.getStatus() != null ? v.getStatus().toString() : "DRAFT");
                row.put("performedBy", "-");
                recent.add(row);
            });
            model.addAttribute("recentValidations", recent);

        } catch (Exception e) {
            log.warn("Could not load validation stats: {}", e.getMessage());
        }

        try {
            var lastValidationSummary = validationService.findLastAccessibleValidationSummary();
            model.addAttribute("lastValidationSummary", lastValidationSummary);
        } catch (Exception e) {
            log.warn("Could not load last validation summary: {}", e.getMessage());
            model.addAttribute("lastValidationSummary", null);
        }

        return "home";
    }
}
