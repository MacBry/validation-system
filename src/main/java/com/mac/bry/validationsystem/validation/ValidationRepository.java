package com.mac.bry.validationsystem.validation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository dla walidacji
 */
@Repository
public interface ValidationRepository extends JpaRepository<Validation, Long> {

    /**
     * Znajduje walidacje według statusu
     */
    List<Validation> findByStatus(ValidationStatus status);

    /**
     * Znajduje wszystkie walidacje posortowane po dacie (najnowsze pierwsze)
     */
    List<Validation> findAllByOrderByCreatedDateDesc();

    /**
     * Znajduje walidacje dla konkretnego urządzenia
     */
    @Query("SELECT DISTINCT v FROM Validation v JOIN v.measurementSeries ms WHERE ms.coolingDevice.id = :deviceId ORDER BY v.createdDate DESC")
    List<Validation> findByDeviceId(@Param("deviceId") Long deviceId);

    /**
     * Znajduje najnowszą walidację dla urządzenia
     */
    @Query("SELECT v FROM Validation v JOIN v.measurementSeries ms WHERE ms.coolingDevice.id = :deviceId ORDER BY v.createdDate DESC LIMIT 1")
    Validation findLatestByDeviceId(@Param("deviceId") Long deviceId);

    // =========================================================================
    // Pessimistic lock — prevents race condition in signing flow
    // =========================================================================

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Validation v WHERE v.id = :id")
    Optional<Validation> findByIdForUpdate(@Param("id") Long id);

    // =========================================================================
    // FIX #2: Multi-Tenancy SQL Filtering (Day 9)
    // =========================================================================

    /**
     * Pobiera walidacje dostępne dla zalogowanego użytkownika na podstawie cache
     * uprawnień.
     * Filtrowanie odbywa się na poziomie SQL przez urządzenie chłodnicze
     * (coolingDevice).
     *
     * @param isSuperAdmin czy użytkownik jest super adminem
     * @param companyIds   dozwolone ID firm (via device.department.company)
     * @param deptIds      dozwolone ID działów (via device.department)
     * @param labIds       dozwolone ID laboratoriów (via device.laboratory)
     */
    @Query("""
                SELECT DISTINCT v FROM Validation v
                JOIN v.measurementSeries ms
                JOIN ms.coolingDevice cd
                JOIN cd.department dept
                WHERE :isSuperAdmin = true
                   OR dept.company.id IN :companyIds
                   OR dept.id IN :deptIds
                   OR (cd.laboratory IS NOT NULL AND cd.laboratory.id IN :labIds)
                ORDER BY v.createdDate DESC
            """)
    List<Validation> findAllAccessible(
            @Param("isSuperAdmin") boolean isSuperAdmin,
            @Param("companyIds") Collection<Long> companyIds,
            @Param("deptIds") Collection<Long> deptIds,
            @Param("labIds") Collection<Long> labIds);

    /**
     * Pobiera ostatnią walidację dostępną dla użytkownika wraz z eagerly-załadowanymi
     * seriami pomiarowymi (JOIN FETCH) — bezpieczne dla renderowania poza transakcją.
     */
    @Query("""
                SELECT DISTINCT v FROM Validation v
                LEFT JOIN FETCH v.measurementSeries ms
                LEFT JOIN ms.coolingDevice cd
                LEFT JOIN cd.department dept
                WHERE :isSuperAdmin = true
                   OR dept.company.id IN :companyIds
                   OR dept.id IN :deptIds
                   OR (cd.laboratory IS NOT NULL AND cd.laboratory.id IN :labIds)
                ORDER BY v.createdDate DESC
            """)
    List<Validation> findLastAccessibleWithSeries(
            @Param("isSuperAdmin") boolean isSuperAdmin,
            @Param("companyIds") Collection<Long> companyIds,
            @Param("deptIds") Collection<Long> deptIds,
            @Param("labIds") Collection<Long> labIds);

    /**
     * Wyszukuje walidacje po nazwie urządzenia, numerze inwentarzowym lub numerze seryjnym rejestratora.
     */
    @Query("""
                SELECT DISTINCT v FROM Validation v
                LEFT JOIN v.measurementSeries ms
                LEFT JOIN ms.coolingDevice cd
                LEFT JOIN ms.thermoRecorder tr
                LEFT JOIN cd.department dept
                WHERE (:isSuperAdmin = true
                   OR dept.company.id IN :companyIds
                   OR dept.id IN :deptIds
                   OR (cd.laboratory IS NOT NULL AND cd.laboratory.id IN :labIds))
                   AND (LOWER(cd.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(cd.inventoryNumber) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(tr.serialNumber) LIKE LOWER(CONCAT('%', :query, '%')))
                ORDER BY v.createdDate DESC
            """)
    List<Validation> searchAccessible(
            @Param("query") String query,
            @Param("isSuperAdmin") boolean isSuperAdmin,
            @Param("companyIds") Collection<Long> companyIds,
            @Param("deptIds") Collection<Long> deptIds,
            @Param("labIds") Collection<Long> labIds);
}
