package com.mac.bry.validationsystem.device;

import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.laboratory.Laboratory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoolingDeviceRepository extends JpaRepository<CoolingDevice, Long>, JpaSpecificationExecutor<CoolingDevice> {

        Optional<CoolingDevice> findByInventoryNumber(String inventoryNumber);

        /**
         * Pobiera urządzenie z załadowanymi relacjami (department, laboratory,
         * materialType)
         */
        @Query("SELECT cd FROM CoolingDevice cd " +
                        "JOIN FETCH cd.department d " +
                        "LEFT JOIN FETCH cd.laboratory l " +
                        "LEFT JOIN FETCH cd.materialType mt " +
                        "WHERE cd.id = :id")
        Optional<CoolingDevice> findByIdWithRelations(@Param("id") Long id);

        List<CoolingDevice> findByLaboratory(Laboratory laboratory);

        /**
         * Znajduje wszystkie urządzenia działu
         * NOWA METODA!
         */
        List<CoolingDevice> findByDepartment(Department department);

        List<CoolingDevice> findByChamberType(ChamberType chamberType);

        boolean existsByInventoryNumber(String inventoryNumber);

        // =========================================================================
        // FIX #2: Multi-Tenancy SQL Filtering (Day 9)
        // =========================================================================

        /**
         * Pobiera urządzenia dostępne dla zalogowanego użytkownika na podstawie cache
         * uprawnień.
         *
         * Logika:
         * - isSuperAdmin = true → zwróć WSZYSTKIE urządzenia
         * - Inaczej → filtruj po companyIds (przez department.company),
         * deptIds (przez department), labIds (przez laboratory, nullable)
         *
         * @param isSuperAdmin czy użytkownik jest super adminem
         * @param companyIds   dozwolone ID firm (via department.company)
         * @param deptIds      dozwolone ID działów
         * @param labIds       dozwolone ID laboratoriów (może być null)
         */
        @Query("""
                            SELECT DISTINCT cd FROM CoolingDevice cd
                            JOIN cd.department dept
                            WHERE :isSuperAdmin = true
                               OR dept.company.id IN :companyIds
                               OR dept.id IN :deptIds
                               OR (cd.laboratory IS NOT NULL AND cd.laboratory.id IN :labIds)
                        """)
        Page<CoolingDevice> findAllAccessible(
                        @Param("isSuperAdmin") boolean isSuperAdmin,
                        @Param("companyIds") Collection<Long> companyIds,
                        @Param("deptIds") Collection<Long> deptIds,
                        @Param("labIds") Collection<Long> labIds,
                        Pageable pageable);

        /**
         * Liczy urządzenia dostępne dla zalogowanego użytkownika (do paginacji).
         */
        @Query("""
                            SELECT COUNT(DISTINCT cd) FROM CoolingDevice cd
                            JOIN cd.department dept
                            WHERE :isSuperAdmin = true
                               OR dept.company.id IN :companyIds
                               OR dept.id IN :deptIds
                               OR (cd.laboratory IS NOT NULL AND cd.laboratory.id IN :labIds)
                        """)
        long countAccessible(
                        @Param("isSuperAdmin") boolean isSuperAdmin,
                        @Param("companyIds") Collection<Long> companyIds,
                        @Param("deptIds") Collection<Long> deptIds,
                        @Param("labIds") Collection<Long> labIds);

        /**
         * Wyszukuje urządzenia po nazwie lub numerze inwentarzowym, respektując uprawnienia.
         */
        @Query("""
                            SELECT DISTINCT cd FROM CoolingDevice cd
                            JOIN cd.department dept
                            WHERE (:isSuperAdmin = true
                               OR dept.company.id IN :companyIds
                               OR dept.id IN :deptIds
                               OR (cd.laboratory IS NOT NULL AND cd.laboratory.id IN :labIds))
                               AND (LOWER(cd.name) LIKE LOWER(CONCAT('%', :query, '%'))
                                    OR LOWER(cd.inventoryNumber) LIKE LOWER(CONCAT('%', :query, '%')))
                        """)
        List<CoolingDevice> searchAccessible(
                        @Param("query") String query,
                        @Param("isSuperAdmin") boolean isSuperAdmin,
                        @Param("companyIds") Collection<Long> companyIds,
                        @Param("deptIds") Collection<Long> deptIds,
                        @Param("labIds") Collection<Long> labIds);
}
