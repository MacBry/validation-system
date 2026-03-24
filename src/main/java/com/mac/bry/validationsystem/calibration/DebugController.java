package com.mac.bry.validationsystem.calibration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Debug controller - available ONLY in the 'dev' profile and restricted to SUPER_ADMIN role.
 * This controller is completely disabled in production builds.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
@PreAuthorize("hasRole('SUPER_ADMIN')")
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
            // Do not expose file system paths in debug output
            return "ID: " + cal.getId() +
                   ", Numer certyfikatu: " + cal.getCertificateNumber() +
                   ", Recorder: " + (cal.getThermoRecorder() != null ? cal.getThermoRecorder().getSerialNumber() : "null");
        } catch (Exception e) {
            log.error("Debug endpoint error for calibration id: {}", id, e);
            return "Error processing request";
        }
    }
}
