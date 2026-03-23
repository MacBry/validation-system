package com.mac.bry.validationsystem.validationplan;

import com.mac.bry.validationsystem.device.CoolingDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ValidationPlanNumberRepository extends JpaRepository<ValidationPlanNumber, Long> {
    
    List<ValidationPlanNumber> findByCoolingDeviceOrderByYearDesc(CoolingDevice coolingDevice);
    
    Optional<ValidationPlanNumber> findByCoolingDeviceAndYear(CoolingDevice coolingDevice, Integer year);
    
    boolean existsByCoolingDeviceAndYear(CoolingDevice coolingDevice, Integer year);
}
