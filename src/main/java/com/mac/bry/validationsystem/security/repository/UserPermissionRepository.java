package com.mac.bry.validationsystem.security.repository;

import com.mac.bry.validationsystem.security.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    List<UserPermission> findByUserId(Long userId);

    @Query("SELECT DISTINCT p.user.id FROM UserPermission p WHERE p.company.id IN :companyIds")
    Set<Long> findUserIdsByCompanyIds(@Param("companyIds") Collection<Long> companyIds);

    @Query("SELECT COUNT(p) > 0 FROM UserPermission p WHERE p.user.id = :userId AND p.company.id IN :companyIds")
    boolean existsByUserIdAndCompanyIds(@Param("userId") Long userId, @Param("companyIds") Collection<Long> companyIds);

    void deleteByUserId(Long userId);
}
