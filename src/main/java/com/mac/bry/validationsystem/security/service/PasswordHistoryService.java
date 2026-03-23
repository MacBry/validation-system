package com.mac.bry.validationsystem.security.service;

import com.mac.bry.validationsystem.security.PasswordHistory;
import com.mac.bry.validationsystem.security.PasswordHistory.PasswordChangeType;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Serwis historii hasel - FDA 21 CFR Part 11 Sec. 11.300(b).
 *
 * Odpowiedzialnosci:
 * 1. Weryfikacja czy nowe haslo nie bylo uzywane w ostatnich 12 zmianach
 * 2. Zapisywanie hasel do historii po kazdej zmianie
 * 3. Czyszczenie starych wpisow (utrzymanie max 12 na uzytkownika)
 *
 * Wszystkie hasla przechowywane sa wylacznie jako hashe BCrypt.
 * Porownanie odbywa sie przez PasswordEncoder.matches() - nigdy plaintext.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordHistoryService {

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Sprawdza czy podane haslo (plaintext) bylo juz uzywane w ostatnich N zmianach.
     *
     * FDA 21 CFR Part 11 Sec. 11.300(b): hasla musza byc unikalne.
     * GxP best practice: sprawdzanie ostatnich 12 hasel.
     *
     * @param userId      ID uzytkownika
     * @param rawPassword nowe haslo w postaci jawnej (do porownania z hashami)
     * @return true jesli haslo bylo juz uzywane (niedozwolone), false jesli jest unikalne
     */
    @Transactional(readOnly = true)
    public boolean isPasswordPreviouslyUsed(Long userId, String rawPassword) {
        List<PasswordHistory> recentPasswords = passwordHistoryRepository
                .findTopNByUserIdOrderByChangedAtDesc(userId, PasswordHistory.MAX_HISTORY_SIZE);

        for (PasswordHistory entry : recentPasswords) {
            if (passwordEncoder.matches(rawPassword, entry.getPasswordHash())) {
                log.warn("Proba uzycia hasla z historii przez uzytkownika ID: {} " +
                        "(haslo uzyte w: {})", userId, entry.getChangedAt());
                return true;
            }
        }
        return false;
    }

    /**
     * Zapisuje haslo do historii po pomyslnej zmianie.
     *
     * WAZNE: Ta metoda przyjmuje juz zahashowane haslo (BCrypt).
     * Nigdy nie przechowujemy hasel w postaci jawnej.
     *
     * @param user         uzytkownik
     * @param encodedPassword hash BCrypt nowego hasla
     * @param changedBy    kto zainicjowal zmiane (username)
     * @param changeType   typ zmiany
     */
    @Transactional
    public void recordPasswordChange(User user, String encodedPassword,
                                     String changedBy, PasswordChangeType changeType) {
        PasswordHistory entry = new PasswordHistory(user, encodedPassword, changedBy, changeType);
        passwordHistoryRepository.save(entry);

        log.info("Zapisano haslo do historii dla uzytkownika: {} (typ: {}, przez: {})",
                user.getUsername(), changeType, changedBy);

        // Cleanup: usun wpisy ponad limit
        cleanupOldEntries(user.getId());
    }

    /**
     * Usuwa najstarsze wpisy historii hasel, zachowujac tylko ostatnie MAX_HISTORY_SIZE.
     * Zapobiega nieograniczonemu wzrostowi tabeli.
     *
     * @param userId ID uzytkownika
     */
    @Transactional
    public void cleanupOldEntries(Long userId) {
        long count = passwordHistoryRepository.countByUserId(userId);
        if (count > PasswordHistory.MAX_HISTORY_SIZE) {
            passwordHistoryRepository.deleteOldEntries(userId, PasswordHistory.MAX_HISTORY_SIZE);
            log.debug("Wyczyszczono stare wpisy historii hasel dla uzytkownika ID: {} " +
                    "(bylo: {}, zostalo: {})", userId, count, PasswordHistory.MAX_HISTORY_SIZE);
        }
    }

    /**
     * Pobiera liczbe hasel w historii uzytkownika.
     * Uzyteczne do diagnostyki i raportow compliance.
     *
     * @param userId ID uzytkownika
     * @return liczba wpisow historii
     */
    @Transactional(readOnly = true)
    public long getHistoryCount(Long userId) {
        return passwordHistoryRepository.countByUserId(userId);
    }
}
