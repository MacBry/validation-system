package com.mac.bry.validationsystem.security.repository;

import com.mac.bry.validationsystem.security.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // ========================================================================
    // Password Expiry Queries
    // ========================================================================

    /**
     * Znajdź użytkowników z hasłami wygasającymi w określonym przedziale czasowym
     */
    List<User> findByPasswordExpiresAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Znajdź użytkowników z wygasłymi hasłami
     */
    List<User> findByPasswordExpiresAtBefore(LocalDateTime dateTime);

    /**
     * Znajdź użytkowników z hasłami, które nie mają ustawionego wygaśnięcia
     */
    @Query("SELECT u FROM User u WHERE u.passwordExpiresAt IS NULL")
    List<User> findUsersWithoutPasswordExpiry();

    /**
     * Liczba użytkowników z wygasłymi hasłami
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.passwordExpiresAt < :now")
    long countUsersWithExpiredPasswords(@Param("now") LocalDateTime now);

    /**
     * Liczba użytkowników z hasłami wygasającymi w określonym czasie
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.passwordExpiresAt BETWEEN :start AND :end")
    long countUsersWithPasswordsExpiringBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
