package com.mac.bry.validationsystem.security;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

/**
 * Historia hasel uzytkownikow - zgodnosc z FDA 21 CFR Part 11 Sec. 11.300(b).
 *
 * Przechowuje hashowane (BCrypt) wersje poprzednich hasel w celu zapobiegania
 * ponownemu uzyciu. System weryfikuje ostatnie 12 hasel przed akceptacja nowego.
 *
 * Regulacje:
 * - FDA 21 CFR Part 11 Sec. 11.300(b): hasla musza byc unikalne
 * - EU GMP Annex 11 Sec. 12.1: kontrola dostepu i unikalnosc hasel
 * - GAMP 5: walidacja systemow skomputeryzowanych
 */
@Entity
@Table(name = "password_history", indexes = {
        @Index(name = "idx_password_history_user_changed", columnList = "user_id, changed_at DESC"),
        @Index(name = "idx_password_history_changed_at", columnList = "changed_at")
})
@Audited
@Getter
@Setter
@NoArgsConstructor
public class PasswordHistory {

    /**
     * Maksymalna liczba hasel przechowywanych w historii.
     * GxP best practice: 12 ostatnich hasel.
     */
    public static final int MAX_HISTORY_SIZE = 12;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Hash BCrypt poprzedniego hasla.
     * NIGDY nie przechowujemy hasel w postaci jawnej (plaintext).
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * Moment ustawienia tego hasla (zgodnie z ALCOA+ - Contemporaneous).
     */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /**
     * Kto zainicjowal zmiane hasla (Attributable - ALCOA+).
     */
    @Column(name = "changed_by", length = 50)
    private String changedBy;

    /**
     * Typ zmiany hasla dla audit trail.
     */
    @Column(name = "change_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private PasswordChangeType changeType;

    /**
     * Konstruktor tworzacy wpis historii hasla.
     *
     * @param user       uzytkownik ktorego haslo sie zmienia
     * @param passwordHash hash BCrypt hasla
     * @param changedBy  kto zainicjowal zmiane
     * @param changeType typ zmiany
     */
    public PasswordHistory(User user, String passwordHash, String changedBy, PasswordChangeType changeType) {
        this.user = user;
        this.passwordHash = passwordHash;
        this.changedAt = LocalDateTime.now();
        this.changedBy = changedBy;
        this.changeType = changeType;
    }

    /**
     * Typy zmian hasla - dla pelnego audit trail.
     */
    public enum PasswordChangeType {
        /** Zmiana przez samego uzytkownika */
        SELF,
        /** Reset przez administratora */
        ADMIN_RESET,
        /** Wymuszona zmiana (pierwsze logowanie / wygasniecie) */
        FORCED,
        /** Reset przez link email */
        PASSWORD_RESET,
        /** Poczatkowe haslo przy tworzeniu konta */
        INITIAL
    }
}
