package com.mac.bry.validationsystem.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Encja rewizji Envers — napisana od zera, BEZ dziedziczenia po DefaultRevisionEntity.
 *
 * DLACZEGO: DefaultRevisionEntity w Hibernate 6.4.x NIE ma @Column(name="REV") na polu 'id'
 * ani @Column(name="REVTSTMP") na polu 'timestamp'. ddl-auto=update dodawał do tabeli REVINFO
 * stray kolumny 'id' i 'timestamp', kolidując z prawidłowymi kolumnami REV i REVTSTMP.
 *
 * Jawne @Column na każdym polu = brak nieporozumień między JPA a Envers.
 *
 * GMP Annex 11 §10: każda zmiana musi zawierać kto, kiedy i skąd.
 */
@Entity
@Table(name = "REVINFO")
@RevisionEntity(EnversRevisionListener.class)
@Getter
@Setter
public class SystemRevisionEntity {

    /**
     * Numer rewizji — AUTO_INCREMENT w MySQL (GenerationType.IDENTITY).
     * @Column(name = "REV") zapewnia, że JPA i Envers używają tej samej kolumny.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    @Column(name = "REV")
    private int id;

    /**
     * Timestamp rewizji w milisekundach (Unix epoch).
     * Envers wypełnia to pole automatycznie.
     */
    @RevisionTimestamp
    @Column(name = "REVTSTMP")
    private long timestamp;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
