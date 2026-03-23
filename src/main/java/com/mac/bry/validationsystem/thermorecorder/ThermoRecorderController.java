package com.mac.bry.validationsystem.thermorecorder;

import com.mac.bry.validationsystem.audit.EnversRevisionService;
import com.mac.bry.validationsystem.audit.FieldDiffDto;
import com.mac.bry.validationsystem.audit.RevisionInfoDto;
import com.mac.bry.validationsystem.department.DepartmentService;
import com.mac.bry.validationsystem.laboratory.LaboratoryService;
import com.mac.bry.validationsystem.security.service.AuditService;
import com.mac.bry.validationsystem.security.service.SecurityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/recorders")
@RequiredArgsConstructor
@Slf4j
public class ThermoRecorderController {

    private final ThermoRecorderService thermoRecorderService;
    private final DepartmentService departmentService;
    private final LaboratoryService laboratoryService;
    private final SecurityService securityService;
    private final AuditService auditService;
    private final EnversRevisionService enversRevisionService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        log.debug("Wyświetlanie listy rejestratorów TESTO (strona: {})", page);
        Page<ThermoRecorder> recorderPage = thermoRecorderService.getAllAccessibleRecorders(PageRequest.of(page, 20));

        model.addAttribute("recorderPage", recorderPage);
        model.addAttribute("recorders", recorderPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", recorderPage.getTotalPages());

        return "recorder/list";
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Wyświetlanie szczegółów rejestratora o id: {}", id);

        if (!securityService.hasAccessToThermoRecorder(id)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Brak dostępu do rejestratora");
            return "redirect:/recorders";
        }

        return thermoRecorderService.findByIdWithCalibrations(id)
                .map(recorder -> {
                    model.addAttribute("recorder", recorder);
                    model.addAttribute("calibrations", recorder.getCalibrations());
                    model.addAttribute("auditLogs", auditService.getLogsForEntity("ThermoRecorder", id));
                    return "recorder/details";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Nie znaleziono rejestratora");
                    return "redirect:/recorders";
                });
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        log.debug("Wyświetlanie formularza tworzenia rejestratora");
        model.addAttribute("recorderDto", new ThermoRecorderDto());

        // Filtrowanie działów do których użytkownik ma dostęp
        model.addAttribute("departments", departmentService.getAllowedDepartments(
                securityService.getAllowedDepartmentIds(),
                securityService.getAllowedCompanyIds()));

        model.addAttribute("statuses", RecorderStatus.values());
        return "recorder/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("recorderDto") ThermoRecorderDto recorderDto,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {
        log.debug("Tworzenie nowego rejestratora: {}", recorderDto);

        if (result.hasErrors()) {
            model.addAttribute("departments", departmentService.getAllowedDepartments(
                    securityService.getAllowedDepartmentIds(),
                    securityService.getAllowedCompanyIds()));
            model.addAttribute("statuses", RecorderStatus.values());
            return "recorder/form";
        }

        if (thermoRecorderService.existsBySerialNumber(recorderDto.getSerialNumber())) {
            result.rejectValue("serialNumber", "error.recorder",
                    "Rejestrator o tym numerze seryjnym już istnieje");
            model.addAttribute("departments", departmentService.getAllowedDepartments(
                    securityService.getAllowedDepartmentIds(),
                    securityService.getAllowedCompanyIds()));
            model.addAttribute("statuses", RecorderStatus.values());
            return "recorder/form";
        }

        // Pobierz dział
        var department = departmentService.getDepartmentById(recorderDto.getDepartmentId());

        // Pobierz pracownię (jeśli podano)
        var laboratory = recorderDto.getLaboratoryId() != null
                ? laboratoryService.findById(recorderDto.getLaboratoryId()).orElse(null)
                : null;

        ThermoRecorder recorder = ThermoRecorder.builder()
                .serialNumber(recorderDto.getSerialNumber())
                .model(recorderDto.getModel())
                .department(department)
                .laboratory(laboratory)
                .status(RecorderStatus.INACTIVE) // Zawsze INACTIVE przy tworzeniu
                .build();

        ThermoRecorder savedRecorder = thermoRecorderService.save(recorder);
        redirectAttributes.addFlashAttribute("successMessage",
                "Rejestrator został pomyślnie utworzony");
        return "redirect:/recorders/" + savedRecorder.getId();
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Wyświetlanie formularza edycji rejestratora o id: {}", id);

        if (!securityService.hasAccessToThermoRecorder(id)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Brak dostępu do edycji rejestratora");
            return "redirect:/recorders";
        }

        return thermoRecorderService.findById(id)
                .map(recorder -> {
                    ThermoRecorderDto dto = ThermoRecorderDto.builder()
                            .id(recorder.getId())
                            .serialNumber(recorder.getSerialNumber())
                            .model(recorder.getModel())
                            .departmentId(recorder.getDepartment().getId())
                            .laboratoryId(recorder.getLaboratory() != null ? recorder.getLaboratory().getId() : null)
                            .status(recorder.getStatus())
                            .build();

                    model.addAttribute("recorderDto", dto);
                    model.addAttribute("departments", departmentService.getAllowedDepartments(
                            securityService.getAllowedDepartmentIds(),
                            securityService.getAllowedCompanyIds()));
                    model.addAttribute("statuses", RecorderStatus.values());

                    // Jeśli rejestrator ma dział z pracowniami, załaduj je
                    if (recorder.getDepartment().getHasLaboratories()) {
                        model.addAttribute("laboratories",
                                laboratoryService.getLaboratoriesByDepartment(recorder.getDepartment().getId()));
                    }

                    return "recorder/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Nie znaleziono rejestratora");
                    return "redirect:/recorders";
                });
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
            @Valid @ModelAttribute("recorderDto") ThermoRecorderDto recorderDto,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {
        log.debug("Aktualizacja rejestratora o id: {}", id);

        if (!securityService.hasAccessToThermoRecorder(id)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Brak dostępu do aktualizacji rejestratora");
            return "redirect:/recorders";
        }

        if (result.hasErrors()) {
            model.addAttribute("departments", departmentService.getAllowedDepartments(
                    securityService.getAllowedDepartmentIds(),
                    securityService.getAllowedCompanyIds()));
            model.addAttribute("statuses", RecorderStatus.values());
            return "recorder/form";
        }

        return thermoRecorderService.findById(id)
                .map(existingRecorder -> {
                    Map<String, Object> oldSnapshot = Map.of("serialNumber", existingRecorder.getSerialNumber(),
                            "model", existingRecorder.getModel() != null ? existingRecorder.getModel() : "",
                            "status", existingRecorder.getStatus() != null ? existingRecorder.getStatus().name() : "");
                    // Pobierz dział
                    var department = departmentService.getDepartmentById(recorderDto.getDepartmentId());

                    // Pobierz pracownię (jeśli podano)
                    var laboratory = recorderDto.getLaboratoryId() != null
                            ? laboratoryService.findById(recorderDto.getLaboratoryId()).orElse(null)
                            : null;

                    existingRecorder.setSerialNumber(recorderDto.getSerialNumber());
                    existingRecorder.setModel(recorderDto.getModel());
                    existingRecorder.setDepartment(department);
                    existingRecorder.setLaboratory(laboratory);
                    existingRecorder.setStatus(recorderDto.getStatus());

                    thermoRecorderService.save(existingRecorder);

                    redirectAttributes.addFlashAttribute("successMessage",
                            "Rejestrator został pomyślnie zaktualizowany");
                    return "redirect:/recorders/" + id;
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Nie znaleziono rejestratora");
                    return "redirect:/recorders";
                });
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.debug("Usuwanie rejestratora o id: {}", id);

        if (!securityService.hasAccessToThermoRecorder(id)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Brak uprawnień do usunięcia rejestratora");
            return "redirect:/recorders";
        }

        try {
            thermoRecorderService.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Rejestrator został pomyślnie usunięty");
        } catch (Exception e) {
            log.error("Błąd podczas usuwania rejestratora", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Nie można usunąć rejestratora");
        }

        return "redirect:/recorders";
    }

    @PostMapping("/update-statuses")
    public String updateAllStatuses(RedirectAttributes redirectAttributes) {
        log.debug("Aktualizacja statusów wszystkich rejestratorów");

        try {
            thermoRecorderService.updateAllRecorderStatuses();
            redirectAttributes.addFlashAttribute("successMessage",
                    "Statusy rejestratorów zostały zaktualizowane");
        } catch (Exception e) {
            log.error("Błąd podczas aktualizacji statusów", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Błąd podczas aktualizacji statusów");
        }

        return "redirect:/recorders";
    }

    /**
     * API endpoint - pobiera pracownie dla działu (AJAX)
     */
    @GetMapping("/api/departments/{departmentId}/laboratories")
    @ResponseBody
    public java.util.List<LaboratoryDto> getLaboratoriesForDepartment(@PathVariable Long departmentId) {
        log.debug("Pobieranie pracowni dla działu ID: {}", departmentId);

        return laboratoryService.getLaboratoriesByDepartment(departmentId).stream()
                .map(lab -> new LaboratoryDto(lab.getId(), lab.getFullName(), lab.getAbbreviation()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Envers: lista rewizji dla rejestratora (AJAX)
     */
    @GetMapping("/{id}/history")
    @ResponseBody
    public List<RevisionInfoDto> getRecorderRevisions(@PathVariable Long id) {
        log.debug("Pobieranie historii Envers dla rejestratora ID: {}", id);
        return enversRevisionService.getRevisionHistory(ThermoRecorder.class, id);
    }

    /**
     * Envers: diff pól między rewizją N a N-1 (AJAX)
     */
    @GetMapping("/{id}/history/{revNum}")
    @ResponseBody
    public List<FieldDiffDto> getRecorderRevisionDiff(@PathVariable Long id, @PathVariable int revNum) {
        log.debug("Pobieranie diff rewizji {} dla rejestratora ID: {}", revNum, id);
        Map<String, String> labels = Map.ofEntries(
                Map.entry("serialNumber", "Numer seryjny"),
                Map.entry("model", "Model"),
                Map.entry("status", "Status"),
                Map.entry("department.name", "Dział"),
                Map.entry("laboratory.fullName", "Pracownia"));
        return enversRevisionService.getDetailedDiff(ThermoRecorder.class, id, revNum, labels);
    }

    /**
     * DTO dla AJAX response (pracownie)
     */
    private record LaboratoryDto(Long id, String name, String abbreviation) {
    }
}
