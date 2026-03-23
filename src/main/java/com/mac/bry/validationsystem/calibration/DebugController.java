package com.mac.bry.validationsystem.calibration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DebugController {

    private final CalibrationService calibrationService;

    @GetMapping("/debug/calibration/{id}")
    @ResponseBody
    public String debugCalibration(@PathVariable Long id) {
        try {
            Calibration cal = calibrationService.findById(id).orElse(null);
            if (cal == null) {
                return "Wzorcowanie nie znalezione";
            }
            return "ID: " + cal.getId() + "<br>" +
                   "Numer certyfikatu: " + cal.getCertificateNumber() + "<br>" +
                   "Ścieżka: " + cal.getCertificateFilePath() + "<br>" +
                   "Recorder: " + (cal.getThermoRecorder() != null ? cal.getThermoRecorder().getSerialNumber() : "null");
        } catch (Exception e) {
            return "Błąd: " + e.getMessage();
        }
    }
}
