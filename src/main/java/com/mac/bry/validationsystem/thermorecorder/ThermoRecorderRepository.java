package com.mac.bry.validationsystem.thermorecorder;

import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.laboratory.Laboratory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThermoRecorderRepository extends JpaRepository<ThermoRecorder, Long>, JpaSpecificationExecutor<ThermoRecorder> {

        Optional<ThermoRecorder> findBySerialNumber(String serialNumber);

        List<ThermoRecorder> findByStatus(RecorderStatus status);

        /**
         * Znajduje wszystkie rejestratory działu
         * NOWA METODA!
         */
        List<ThermoRecorder> findByDepartment(Department department);

        /**
         * Znajduje wszystkie rejestratory pracowni
         * NOWA METODA!
         */
        List<ThermoRecorder> findByLaboratory(Laboratory laboratory);

        boolean existsBySerialNumber(String serialNumber);

        @Query("SELECT tr FROM ThermoRecorder tr LEFT JOIN FETCH tr.calibrations WHERE tr.id = :id")
        Optional<ThermoRecorder> findByIdWithCalibrations(Long id);

        @Query("SELECT tr FROM ThermoRecorder tr WHERE " +
                        "(:companyIds IS NULL OR tr.department.company.id IN :companyIds) AND " +
                        "(:deptIds IS NULL OR tr.department.id IN :deptIds)")
        Page<ThermoRecorder> findAllAccessible(
                        @Param("companyIds") java.util.Collection<Long> companyIds,
                        @Param("deptIds") java.util.Collection<Long> deptIds,
                        Pageable pageable);

        /**
         * Wyszukuje rejestratory po numerze seryjnym, respektując uprawnienia.
         */
        @Query("SELECT tr FROM ThermoRecorder tr WHERE " +
                        "((:isSuperAdmin = true) OR " +
                        "(:companyIds IS NULL OR tr.department.company.id IN :companyIds) AND " +
                        "(:deptIds IS NULL OR tr.department.id IN :deptIds)) AND " +
                        "LOWER(tr.serialNumber) LIKE LOWER(CONCAT('%', :query, '%'))")
        List<ThermoRecorder> searchAccessible(
                        @Param("query") String query,
                        @Param("isSuperAdmin") boolean isSuperAdmin,
                        @Param("companyIds") java.util.Collection<Long> companyIds,
                        @Param("deptIds") java.util.Collection<Long> deptIds);
}
