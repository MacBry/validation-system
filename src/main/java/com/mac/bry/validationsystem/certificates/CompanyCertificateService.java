package com.mac.bry.validationsystem.certificates;

import java.util.List;
import java.util.Optional;

public interface CompanyCertificateService {

    /**
     * Wgrywa nowy certyfikat PKCS12 dla firmy.
     * Dezaktywuje poprzedni aktywny certyfikat tej firmy.
     *
     * @param companyId     ID firmy
     * @param keystoreBytes bajty pliku .p12/.pfx
     * @param password      hasło keystore
     * @param uploadedBy    ID użytkownika wgrywającego
     * @return zapisany CompanyCertificate z wyekstrahowanymi metadanymi
     * @throws Exception jeśli plik nie jest poprawnym PKCS12 lub hasło jest błędne
     */
    CompanyCertificate upload(Long companyId, byte[] keystoreBytes, String password, Long uploadedBy) throws Exception;

    /**
     * Zwraca aktywny certyfikat firmy, jeśli istnieje.
     */
    Optional<CompanyCertificate> findActive(Long companyId);

    /**
     * Zwraca całą historię certyfikatów firmy (od najnowszego).
     */
    List<CompanyCertificate> findAll(Long companyId);

    /**
     * Dezaktywuje certyfikat o podanym ID.
     */
    void deactivate(Long certId);
}
