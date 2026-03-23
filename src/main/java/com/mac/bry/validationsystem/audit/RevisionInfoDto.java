package com.mac.bry.validationsystem.audit;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * DTO z metadanymi rewizji Envers (kto, kiedy, skąd).
 */
@Value
@Builder
public class RevisionInfoDto {

    int revisionNumber;
    LocalDateTime timestamp;
    String username;
    String fullName;
    String ipAddress;
    /** ADD / MOD / DEL */
    String revisionType;

    public static RevisionInfoDto from(SystemRevisionEntity rev, org.hibernate.envers.RevisionType revType) {
        return RevisionInfoDto.builder()
                .revisionNumber(rev.getId())
                .timestamp(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(rev.getTimestamp()), ZoneId.systemDefault()))
                .username(rev.getUsername())
                .fullName(rev.getFullName())
                .ipAddress(rev.getIpAddress())
                .revisionType(switch (revType) {
                    case ADD -> "ADD";
                    case MOD -> "MOD";
                    case DEL -> "DEL";
                })
                .build();
    }
}
