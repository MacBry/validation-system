package com.mac.bry.validationsystem.measurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Repository dla serii pomiarowych
 */
@Repository
public interface MeasurementSeriesRepository extends JpaRepository<MeasurementSeries, Long> {

    /**
     * Znajduje wszystkie serie posortowane według daty przesłania (najnowsze
     * pierwsze)
     */
    List<MeasurementSeries> findAllByOrderByUploadDateDesc();

    /**
     * Znajduje tylko NIEużyte serie pomiarowe (wolne do wykorzystania)
     */
    List<MeasurementSeries> findByUsedInValidationFalseOrderByUploadDateDesc();

    /**
     * Znajduje tylko użyte serie pomiarowe
     */
    List<MeasurementSeries> findByUsedInValidationTrueOrderByUploadDateDesc();

    /**
     * Znajduje serie według numeru seryjnego rejestratora
     */
    List<MeasurementSeries> findByRecorderSerialNumberOrderByFirstMeasurementTimeDesc(String serialNumber);

    /**
     * Znajduje serie w określonym zakresie dat
     */
    @Query("SELECT ms FROM MeasurementSeries ms WHERE ms.firstMeasurementTime >= :startDate AND ms.lastMeasurementTime <= :endDate")
    List<MeasurementSeries> findByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Znajduje serie według nazwy pliku
     */
    List<MeasurementSeries> findByOriginalFilename(String filename);

    /**
     * Znajduje najnowszą serię pomiarową dla danego urządzenia
     */
    java.util.Optional<MeasurementSeries> findTopByCoolingDeviceIdOrderByLastMeasurementTimeDesc(Long deviceId);

    /**
     * Znajduje wszystkie NIEużyte serie dla danego urządzenia
     */
    @Query("SELECT ms FROM MeasurementSeries ms WHERE ms.coolingDevice.id = :deviceId AND ms.usedInValidation = false " +
           "AND ms.thermoRecorder.status = com.mac.bry.validationsystem.thermorecorder.RecorderStatus.ACTIVE " +
           "ORDER BY ms.uploadDate DESC")
    List<MeasurementSeries> findByCoolingDeviceIdAndUsedInValidationFalseOrderByUploadDateDesc(Long deviceId);

    // =========================================================================
    // FIX #2: Multi-Tenancy SQL Filtering (Day 9)
    // =========================================================================

    /**
     * Pobiera serie pomiarowe dostępne dla zalogowanego użytkownika.
     * Filtrowanie odbywa się przez powiązane urządzenie chłodnicze.
     *
     * @param isSuperAdmin czy użytkownik jest super adminem
     * @param companyIds   dozwolone ID firm
     * @param deptIds      dozwolone ID działów
     * @param labIds       dozwolone ID laboratoriów
     */
    @Query("""
                SELECT DISTINCT ms FROM MeasurementSeries ms
                JOIN ms.coolingDevice cd
                JOIN cd.department dept
                WHERE (:isSuperAdmin = true
                   OR dept.company.id IN :companyIds
                   OR dept.id IN :deptIds
                   OR (cd.laboratory IS NOT NULL AND cd.laboratory.id IN :labIds))
                   AND ms.thermoRecorder.status = com.mac.bry.validationsystem.thermorecorder.RecorderStatus.ACTIVE
                ORDER BY ms.uploadDate DESC
            """)
    List<MeasurementSeries> findAllAccessible(
            @Param("isSuperAdmin") boolean isSuperAdmin,
            @Param("companyIds") Collection<Long> companyIds,
            @Param("deptIds") Collection<Long> deptIds,
            @Param("labIds") Collection<Long> labIds);

    /**
     * Pobiera NIEUŻYTE serie pomiarowe dostępne dla zalogowanego użytkownika.
     */
    @Query("""
                SELECT DISTINCT ms FROM MeasurementSeries ms
                JOIN ms.coolingDevice cd
                JOIN cd.department dept
                WHERE ms.usedInValidation = false
                  AND ms.thermoRecorder.status = com.mac.bry.validationsystem.thermorecorder.RecorderStatus.ACTIVE
                  AND (:isSuperAdmin = true
                       OR dept.company.id IN :companyIds
                       OR dept.id IN :deptIds
                       OR (cd.laboratory IS NOT NULL AND cd.laboratory.id IN :labIds))
                ORDER BY ms.uploadDate DESC
            """)
    List<MeasurementSeries> findAccessibleAndUnused(
            @Param("isSuperAdmin") boolean isSuperAdmin,
            @Param("companyIds") Collection<Long> companyIds,
            @Param("deptIds") Collection<Long> deptIds,
            @Param("labIds") Collection<Long> labIds);

    /**
     * Zlicza serie pomiarowe dostępne dla zalogowanego użytkownika (z filtrowaniem).
     */
    @Query("""
                SELECT COUNT(DISTINCT ms) FROM MeasurementSeries ms
                JOIN ms.coolingDevice cd
                JOIN cd.department dept
                WHERE :isSuperAdmin = true
                   OR dept.company.id IN :companyIds
                   OR dept.id IN :deptIds
                   OR (cd.laboratory IS NOT NULL AND cd.laboratory.id IN :labIds)
            """)
    long countAllAccessible(
            @Param("isSuperAdmin") boolean isSuperAdmin,
            @Param("companyIds") Collection<Long> companyIds,
            @Param("deptIds") Collection<Long> deptIds,
            @Param("labIds") Collection<Long> labIds);

    /**
     * Sumuje liczbę punktów pomiarowych ze wszystkich dostępnych serii (measurementCount).
     * Punkty pomiarowe to pojedyncze odczyty temperatury zapisane w pliku .vi2.
     */
    @Query("""
                SELECT COALESCE(SUM(ms.measurementCount), 0) FROM MeasurementSeries ms
                JOIN ms.coolingDevice cd
                JOIN cd.department dept
                WHERE :isSuperAdmin = true
                   OR dept.company.id IN :companyIds
                   OR dept.id IN :deptIds
                   OR (cd.laboratory IS NOT NULL AND cd.laboratory.id IN :labIds)
            """)
    long sumMeasurementPointsAccessible(
            @Param("isSuperAdmin") boolean isSuperAdmin,
            @Param("companyIds") Collection<Long> companyIds,
            @Param("deptIds") Collection<Long> deptIds,
            @Param("labIds") Collection<Long> labIds);
}
