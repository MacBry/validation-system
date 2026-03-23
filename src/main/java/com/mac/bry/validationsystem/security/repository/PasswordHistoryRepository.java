package com.mac.bry.validationsystem.security.repository;

import com.mac.bry.validationsystem.security.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repozytorium historii hasel.
 *
 * Zapewnia wydajne zapytania do weryfikacji unikalnosci hasel
 * zgodnie z FDA 21 CFR Part 11 Sec. 11.300(b).
 */
@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    /**
     * Pobiera ostatnie N hasel uzytkownika (od najnowszego).
     * Uzywane do weryfikacji czy nowe haslo nie powtarza sie.
     *
     * @param userId ID uzytkownika
     * @param limit  maksymalna liczba wynikow (domyslnie 12)
     * @return lista hashowanych hasel posortowana od najnowszego
     */
    @Query("SELECT ph FROM PasswordHistory ph WHERE ph.user.id = :userId ORDER BY ph.changedAt DESC LIMIT :limit")
    List<PasswordHistory> findTopNByUserIdOrderByChangedAtDesc(
            @Param("userId") Long userId,
            @Param("limit") int limit);

    /**
     * Pobiera wszystkie wpisy historii hasel uzytkownika.
     */
    @Query("SELECT ph FROM PasswordHistory ph WHERE ph.user.id = :userId ORDER BY ph.changedAt DESC")
    List<PasswordHistory> findAllByUserIdOrderByChangedAtDesc(@Param("userId") Long userId);

    /**
     * Liczy wpisy historii hasel dla uzytkownika.
     */
    @Query("SELECT COUNT(ph) FROM PasswordHistory ph WHERE ph.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    /**
     * Usuwa najstarsze wpisy ponad limit dla uzytkownika.
     * Utrzymuje tylko ostatnie N hasel w historii (cleanup).
     *
     * @param userId ID uzytkownika
     * @param keepCount ile najnowszych wpisow zachowac
     */
    @Modifying
    @Query(value = "DELETE FROM password_history WHERE user_id = :userId AND id NOT IN " +
            "(SELECT id FROM (SELECT id FROM password_history WHERE user_id = :userId " +
            "ORDER BY changed_at DESC LIMIT :keepCount) AS keep_ids)", nativeQuery = true)
    void deleteOldEntries(@Param("userId") Long userId, @Param("keepCount") int keepCount);
}
