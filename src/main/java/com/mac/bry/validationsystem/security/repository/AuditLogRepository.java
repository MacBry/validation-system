package com.mac.bry.validationsystem.security.repository;

import com.mac.bry.validationsystem.security.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

        List<AuditLog> findByUserIdOrderByTimestampDesc(Long userId);

        List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, Long entityId);

        Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

        @Query("SELECT a FROM AuditLog a WHERE " +
                        "(:username IS NULL OR a.username LIKE %:username%) AND " +
                        "(:ipAddress IS NULL OR a.ipAddress LIKE %:ipAddress%) " +
                        "ORDER BY a.timestamp DESC")
        Page<AuditLog> findWithFilters(
                        @Param("username") String username,
                        @Param("ipAddress") String ipAddress,
                        Pageable pageable);

        @Query("SELECT a FROM AuditLog a WHERE (a.entityType = 'User' AND a.entityId = :userId) OR a.userId = :userId ORDER BY a.timestamp DESC")
        List<AuditLog> findAllRelatedToUser(@Param("userId") Long userId);

        @Query("SELECT a FROM AuditLog a WHERE (a.entityType = 'User' AND a.entityId = :userId) OR a.userId = :userId ORDER BY a.timestamp DESC")
        Page<AuditLog> findAllRelatedToUserWithPagination(@Param("userId") Long userId, Pageable pageable);

        @Query("SELECT a FROM AuditLog a WHERE " +
                        "(:userIds IS NULL OR a.userId IN :userIds) AND " +
                        "(:username IS NULL OR a.username LIKE %:username%) AND " +
                        "(:ipAddress IS NULL OR a.ipAddress LIKE %:ipAddress%) " +
                        "ORDER BY a.timestamp DESC")
        Page<AuditLog> findWithFiltersAndUserIds(
                        @Param("userIds") Collection<Long> userIds,
                        @Param("username") String username,
                        @Param("ipAddress") String ipAddress,
                        Pageable pageable);

        // FIX #4: Realne wykorzystanie MySQL JSON_EXTRACT dla wyszukiwania konkretnych
        // zmian
        @Query(value = "SELECT * FROM audit_log WHERE entity_type = :entityType AND JSON_EXTRACT(new_value_json, '$.status') = :status", nativeQuery = true)
        List<AuditLog> findByEntityTypeAndNewStatusNative(@Param("entityType") String entityType,
                        @Param("status") String status);

        // GMP AUDIT INTEGRITY: Get hash of last record for hash chain
        @Query("SELECT a.recordHash FROM AuditLog a ORDER BY a.id DESC LIMIT 1")
        Optional<String> findLastRecordHash();
}
