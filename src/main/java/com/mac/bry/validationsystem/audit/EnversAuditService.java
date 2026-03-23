package com.mac.bry.validationsystem.audit;

import com.mac.bry.validationsystem.calibration.Calibration;
import com.mac.bry.validationsystem.calibration.CalibrationPoint;
import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.laboratory.Laboratory;
import com.mac.bry.validationsystem.materialtype.MaterialType;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.UserPermission;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorder;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationDocument;
import com.mac.bry.validationsystem.validation.ValidationSignature;
import com.mac.bry.validationsystem.validationplan.ValidationPlanNumber;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Serwis odczytujący historię zmian encji przez Hibernate Envers AuditReader.
 * Wszystkie metody zwracają listę rewizji od najnowszej do najstarszej.
 */
@Service
@RequiredArgsConstructor
public class EnversAuditService {

    private final EntityManager entityManager;

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntityRevisionEntry<Validation>> getValidationHistory(Long id) {
        return fetchHistory(Validation.class, id);
    }

    // -------------------------------------------------------------------------
    // CoolingDevice
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntityRevisionEntry<CoolingDevice>> getCoolingDeviceHistory(Long id) {
        return fetchHistory(CoolingDevice.class, id);
    }

    // -------------------------------------------------------------------------
    // ThermoRecorder
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntityRevisionEntry<ThermoRecorder>> getThermoRecorderHistory(Long id) {
        return fetchHistory(ThermoRecorder.class, id);
    }

    // -------------------------------------------------------------------------
    // Calibration
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntityRevisionEntry<Calibration>> getCalibrationHistory(Long id) {
        return fetchHistory(Calibration.class, id);
    }

    // -------------------------------------------------------------------------
    // CalibrationPoint
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntityRevisionEntry<CalibrationPoint>> getCalibrationPointHistory(Long id) {
        return fetchHistory(CalibrationPoint.class, id);
    }

    // -------------------------------------------------------------------------
    // MaterialType
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntityRevisionEntry<MaterialType>> getMaterialTypeHistory(Long id) {
        return fetchHistory(MaterialType.class, id);
    }

    // -------------------------------------------------------------------------
    // Department
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntityRevisionEntry<Department>> getDepartmentHistory(Long id) {
        return fetchHistory(Department.class, id);
    }

    // -------------------------------------------------------------------------
    // Laboratory
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntityRevisionEntry<Laboratory>> getLaboratoryHistory(Long id) {
        return fetchHistory(Laboratory.class, id);
    }

    // -------------------------------------------------------------------------
    // Company
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntityRevisionEntry<Company>> getCompanyHistory(Long id) {
        return fetchHistory(Company.class, id);
    }

    // -------------------------------------------------------------------------
    // User
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntityRevisionEntry<User>> getUserHistory(Long id) {
        return fetchHistory(User.class, id);
    }

    // -------------------------------------------------------------------------
    // UserPermission
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntityRevisionEntry<UserPermission>> getUserPermissionHistory(Long id) {
        return fetchHistory(UserPermission.class, id);
    }

    // -------------------------------------------------------------------------
    // ValidationSignature
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntityRevisionEntry<ValidationSignature>> getValidationSignatureHistory(Long id) {
        return fetchHistory(ValidationSignature.class, id);
    }

    // -------------------------------------------------------------------------
    // ValidationDocument
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntityRevisionEntry<ValidationDocument>> getValidationDocumentHistory(Long id) {
        return fetchHistory(ValidationDocument.class, id);
    }

    // -------------------------------------------------------------------------
    // ValidationPlanNumber
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EntityRevisionEntry<ValidationPlanNumber>> getValidationPlanNumberHistory(Long id) {
        return fetchHistory(ValidationPlanNumber.class, id);
    }

    // -------------------------------------------------------------------------
    // Generic helper
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private <T> List<EntityRevisionEntry<T>> fetchHistory(Class<T> entityClass, Long id) {
        AuditReader reader = AuditReaderFactory.get(entityManager);

        List<Object[]> results = reader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().desc())
                .getResultList();

        return results.stream()
                .map(row -> {
                    T entity = (T) row[0];
                    SystemRevisionEntity rev = (SystemRevisionEntity) row[1];
                    RevisionType revType = (RevisionType) row[2];
                    return new EntityRevisionEntry<>(entity, RevisionInfoDto.from(rev, revType));
                })
                .toList();
    }
}
