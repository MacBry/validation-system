package com.mac.bry.validationsystem.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Kontroler wyświetlający historię zmian encji (Hibernate Envers).
 * URL pattern: /audit/history/{entityType}/{id}
 *
 * Dostępne typy encji:
 *   validation, cooling-device, thermo-recorder, calibration,
 *   material-type, department, laboratory, company
 */
@Controller
@RequestMapping("/audit/history")
@RequiredArgsConstructor
public class EnversHistoryController {

    private final EnversAuditService auditService;

    @GetMapping("/validation/{id}")
    public String validationHistory(@PathVariable Long id, Model model) {
        model.addAttribute("entityType", "Walidacja");
        model.addAttribute("entityId", id);
        model.addAttribute("backUrl", "/validations/" + id);
        model.addAttribute("revisions", auditService.getValidationHistory(id));
        return "audit/entity-history";
    }

    @GetMapping("/cooling-device/{id}")
    public String coolingDeviceHistory(@PathVariable Long id, Model model) {
        model.addAttribute("entityType", "Urządzenie chłodnicze");
        model.addAttribute("entityId", id);
        model.addAttribute("backUrl", "/devices/" + id);
        model.addAttribute("revisions", auditService.getCoolingDeviceHistory(id));
        return "audit/entity-history";
    }

    @GetMapping("/thermo-recorder/{id}")
    public String thermoRecorderHistory(@PathVariable Long id, Model model) {
        model.addAttribute("entityType", "Rejestrator temperatury");
        model.addAttribute("entityId", id);
        model.addAttribute("backUrl", "/recorders/" + id);
        model.addAttribute("revisions", auditService.getThermoRecorderHistory(id));
        return "audit/entity-history";
    }

    @GetMapping("/calibration/{id}")
    public String calibrationHistory(@PathVariable Long id, Model model) {
        model.addAttribute("entityType", "Wzorcowanie");
        model.addAttribute("entityId", id);
        model.addAttribute("backUrl", "/recorders");
        model.addAttribute("revisions", auditService.getCalibrationHistory(id));
        return "audit/entity-history";
    }

    @GetMapping("/material-type/{id}")
    public String materialTypeHistory(@PathVariable Long id, Model model) {
        model.addAttribute("entityType", "Typ materiału");
        model.addAttribute("entityId", id);
        model.addAttribute("backUrl", "/material-types");
        model.addAttribute("revisions", auditService.getMaterialTypeHistory(id));
        return "audit/entity-history";
    }

    @GetMapping("/department/{id}")
    public String departmentHistory(@PathVariable Long id, Model model) {
        model.addAttribute("entityType", "Dział");
        model.addAttribute("entityId", id);
        model.addAttribute("backUrl", "/departments");
        model.addAttribute("revisions", auditService.getDepartmentHistory(id));
        return "audit/entity-history";
    }

    @GetMapping("/laboratory/{id}")
    public String laboratoryHistory(@PathVariable Long id, Model model) {
        model.addAttribute("entityType", "Pracownia");
        model.addAttribute("entityId", id);
        model.addAttribute("backUrl", "/laboratories");
        model.addAttribute("revisions", auditService.getLaboratoryHistory(id));
        return "audit/entity-history";
    }

    @GetMapping("/company/{id}")
    public String companyHistory(@PathVariable Long id, Model model) {
        model.addAttribute("entityType", "Firma");
        model.addAttribute("entityId", id);
        model.addAttribute("backUrl", "/companies");
        model.addAttribute("revisions", auditService.getCompanyHistory(id));
        return "audit/entity-history";
    }

    @GetMapping("/user/{id}")
    public String userHistory(@PathVariable Long id, Model model) {
        model.addAttribute("entityType", "Konto użytkownika");
        model.addAttribute("entityId", id);
        model.addAttribute("backUrl", "/admin/users");
        model.addAttribute("revisions", auditService.getUserHistory(id));
        return "audit/entity-history";
    }

    @GetMapping("/user-permission/{id}")
    public String userPermissionHistory(@PathVariable Long id, Model model) {
        model.addAttribute("entityType", "Uprawnienie użytkownika");
        model.addAttribute("entityId", id);
        model.addAttribute("backUrl", "/admin/users");
        model.addAttribute("revisions", auditService.getUserPermissionHistory(id));
        return "audit/entity-history";
    }

    @GetMapping("/validation-signature/{id}")
    public String validationSignatureHistory(@PathVariable Long id, Model model) {
        model.addAttribute("entityType", "Podpis elektroniczny");
        model.addAttribute("entityId", id);
        model.addAttribute("backUrl", "/validations");
        model.addAttribute("revisions", auditService.getValidationSignatureHistory(id));
        return "audit/entity-history";
    }

    @GetMapping("/validation-document/{id}")
    public String validationDocumentHistory(@PathVariable Long id, Model model) {
        model.addAttribute("entityType", "Dokument walidacji");
        model.addAttribute("entityId", id);
        model.addAttribute("backUrl", "/validations");
        model.addAttribute("revisions", auditService.getValidationDocumentHistory(id));
        return "audit/entity-history";
    }

    @GetMapping("/validation-plan-number/{id}")
    public String validationPlanNumberHistory(@PathVariable Long id, Model model) {
        model.addAttribute("entityType", "Numer RPW");
        model.addAttribute("entityId", id);
        model.addAttribute("backUrl", "/devices");
        model.addAttribute("revisions", auditService.getValidationPlanNumberHistory(id));
        return "audit/entity-history";
    }
}
