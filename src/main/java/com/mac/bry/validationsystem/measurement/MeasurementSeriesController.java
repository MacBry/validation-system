package com.mac.bry.validationsystem.measurement;

import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.device.CoolingDeviceService;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.security.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/measurements")
@RequiredArgsConstructor
public class MeasurementSeriesController {

    private final MeasurementSeriesService measurementService;
    private final CoolingDeviceService deviceService;
    private final AuditService auditService;

    /**
     * Wyświetla listę serii pomiarowych
     * 
     * @param showUsed - jeśli true, pokazuje użyte serie; jeśli false (domyślnie),
     *                 pokazuje nieużyte
     */
    @GetMapping
    public String listMeasurements(
            @RequestParam(name = "showUsed", required = false, defaultValue = "false") Boolean showUsed,
            Model model) {
        log.debug("Wyświetlanie listy serii pomiarowych (showUsed={})", showUsed);

        List<MeasurementSeriesDto> series;
        if (showUsed) {
            // Pokaż tylko UŻYTE serie do których masz dostęp
            series = measurementService.getAccessibleUsedSeries();
            model.addAttribute("filterType", "used");
        } else {
            // Pokaż tylko NIEUŻYTE serie (domyślnie) do których masz dostęp
            series = measurementService.getAccessibleUnusedSeries();
            model.addAttribute("filterType", "unused");
        }

        model.addAttribute("measurementSeries", series);
        model.addAttribute("showUsed", showUsed);
        model.addAttribute("positions", RecorderPosition.values());

        return "measurement/list";
    }

    /**
     * Wyświetla formularz do przesyłania plików .vi2
     */
    @GetMapping("/upload")
    public String showUploadForm(Model model) {
        log.debug("Wyświetlanie formularza przesyłania plików .vi2");

        // Grupowanie dostępnych urządzeń po działach
        List<CoolingDevice> accessibleDevices = deviceService.getAllAccessibleDevices();
        Map<Department, List<CoolingDevice>> devicesByDept = accessibleDevices.stream()
                .collect(Collectors.groupingBy(CoolingDevice::getDepartment));

        model.addAttribute("positions", RecorderPosition.values());
        model.addAttribute("devicesByDepartment", devicesByDept);

        return "measurement/upload";
    }

    /**
     * Obsługuje przesyłanie plików .vi2
     */
    @PreAuthorize("@securityService.canManageDevice(#deviceId)")
    @PostMapping("/upload")
    public String uploadFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "recorderPosition", required = false) RecorderPosition recorderPosition,
            @RequestParam(value = "deviceId", required = false) Long deviceId,
            @RequestParam(value = "isReferenceRecorder", required = false, defaultValue = "false") boolean isReferenceRecorder,
            RedirectAttributes redirectAttributes) {

        log.info("Otrzymano żądanie przesłania {} plików, pozycja: {}, urządzenie: {}, referencyjny: {}",
                files.length, recorderPosition, deviceId, isReferenceRecorder);

        if (files.length == 0 || (files.length == 1 && files[0].isEmpty())) {
            redirectAttributes.addFlashAttribute("error", "Nie wybrano żadnych plików");
            return "redirect:/measurements/upload";
        }

        if (!isReferenceRecorder && recorderPosition == null) {
            redirectAttributes.addFlashAttribute("error",
                    "Należy wybrać umiejscowienie rejestratora w urządzeniu chłodniczym");
            return "redirect:/measurements/upload";
        }

        if (deviceId == null) {
            redirectAttributes.addFlashAttribute("error",
                    "Należy wybrać urządzenie chłodnicze");
            return "redirect:/measurements/upload";
        }

        try {
            UploadResult result = measurementService.uploadVi2Files(
                    files, recorderPosition, deviceId, isReferenceRecorder);

            // Dodaj wpis do audit trail dla każdej przesłanej serii
            for (MeasurementSeries series : result.getUploadedSeries()) {
                String positionName = isReferenceRecorder ? "REFERENCE" :
                        (recorderPosition != null ? recorderPosition.name() : "NONE");
                auditService.logOperation("MeasurementSeries", series.getId(), "CREATE", null,
                        Map.of("fileName", series.getOriginalFilename(), "deviceId", deviceId,
                                "position", positionName));
            }

            String successMsg = String.format("Pomyślnie przesłano %d plików .vi2",
                    result.getUploadedSeries().size());
            redirectAttributes.addFlashAttribute("success", successMsg);

            // Dodaj ostrzeżenia jeśli są
            if (result.hasWarnings()) {
                redirectAttributes.addFlashAttribute("warnings", result.getWarnings());
            }

            // Dodaj błędy jeśli są (ale pliki zostały przesłane)
            if (result.hasErrors()) {
                redirectAttributes.addFlashAttribute("errors", result.getErrors());
            }

            return "redirect:/measurements";

        } catch (Exception e) {
            log.error("Błąd podczas przesyłania plików: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Błąd podczas przesyłania plików: " + e.getMessage());
            return "redirect:/measurements/upload";
        }
    }

    /**
     * Wyświetla szczegóły serii pomiarowej
     */
    @PreAuthorize("@securityService.canManageMeasurementSeries(#id)")
    @GetMapping("/{id}")
    public String viewSeriesDetails(@PathVariable Long id, Model model) {
        log.debug("Wyświetlanie szczegółów serii o ID: {}", id);

        try {
            MeasurementSeriesDto series = measurementService.getSeriesById(id);
            model.addAttribute("series", series);

            // Pobieramy też punkty pomiarowe (dla przyszłego wykresu)
            MeasurementSeries fullSeries = measurementService.getSeriesWithPoints(id);
            model.addAttribute("pointsCount", fullSeries.getMeasurementPoints().size());
            model.addAttribute("auditLogs", auditService.getLogsForEntity("MeasurementSeries", id));

            // Dodaj pełną encję z budżetem niepewności
            model.addAttribute("seriesEntity", fullSeries);

            return "measurement/details";

        } catch (IllegalArgumentException e) {
            log.error("Nie znaleziono serii o ID: {}", id);
            model.addAttribute("error", "Nie znaleziono serii pomiarowej");
            return "redirect:/measurements";
        }
    }

    /**
     * Wyświetla wykres temperatury dla serii pomiarowej
     */
    @PreAuthorize("@securityService.canManageMeasurementSeries(#id)")
    @GetMapping("/{id}/chart")
    public String viewChart(@PathVariable Long id, Model model) {
        log.debug("Wyświetlanie wykresu dla serii o ID: {}", id);

        try {
            // DTO z danymi urządzenia i rejestratora
            MeasurementSeriesDto seriesDto = measurementService.getSeriesById(id);
            model.addAttribute("series", seriesDto);

            // Encja z punktami pomiarowymi dla wykresu
            MeasurementSeries fullSeries = measurementService.getSeriesWithPoints(id);
            model.addAttribute("measurementPoints", fullSeries.getMeasurementPoints());

            return "measurement/chart";

        } catch (IllegalArgumentException e) {
            log.error("Nie znaleziono serii o ID: {}", id);
            model.addAttribute("error", "Nie znaleziono serii pomiarowej");
            return "redirect:/measurements";
        }
    }

    /**
     * Usuwa serię pomiarową
     */
    @PreAuthorize("@securityService.canManageMeasurementSeries(#id)")
    @PostMapping("/{id}/delete")
    public String deleteSeries(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Usuwanie serii pomiarowej o ID: {}", id);

        try {
            try {
                MeasurementSeries series = measurementService.getSeriesWithPoints(id);
                auditService.logOperation("MeasurementSeries", id, "DELETE",
                        Map.of("fileName", series.getOriginalFilename()), null);
            } catch (Exception ignores) {
            }

            measurementService.deleteSeries(id);
            redirectAttributes.addFlashAttribute("success", "Pomyślnie usunięto serię pomiarową");
        } catch (Exception e) {
            log.error("Błąd podczas usuwania serii: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error",
                    "Błąd podczas usuwania serii: " + e.getMessage());
        }

        return "redirect:/measurements";
    }
}
