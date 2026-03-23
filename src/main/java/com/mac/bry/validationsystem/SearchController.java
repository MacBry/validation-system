package com.mac.bry.validationsystem;

import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.device.CoolingDeviceService;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorder;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorderService;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final CoolingDeviceService coolingDeviceService;
    private final ThermoRecorderService thermoRecorderService;
    private final ValidationService validationService;

    @GetMapping
    public String search(@RequestParam("q") String query, Model model) {
        log.info("Wyszukiwanie frazy: {}", query);

        if (query == null || query.trim().length() < 2) {
            return "fragments/search-results :: results";
        }

        // 1. Szukamy urządzeń
        List<CoolingDevice> devices = coolingDeviceService.searchAccessibleDevices(query);
        
        // 2. Szukamy rejestratorów
        List<ThermoRecorder> recorders = thermoRecorderService.searchAccessibleRecorders(query);
        
        // 3. Szukamy walidacji
        List<Validation> validations = validationService.searchAccessibleValidations(query);

        model.addAttribute("foundDevices", devices);
        model.addAttribute("foundRecorders", recorders);
        model.addAttribute("foundValidations", validations);
        model.addAttribute("searchQuery", query);

        return "fragments/search-results :: results";
    }
}
