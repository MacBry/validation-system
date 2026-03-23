# Specyfikacja Techniczna i Architektura: Validation Cold Control (VCC)

## 1. Stos Technologiczny (Tech Stack)
Aplikacja VCC jest nowoczesną aplikacją webową zbudowaną w oparciu o architekturę monolityczną z wyraźnym podziałem na warstwy.

- **Język programowania**: Java 17
- **Framework**: Spring Boot 3.2.2
- **Warstwa danych**: Spring Data JPA / Hibernate 6.4
- **Baza danych**: MySQL 8.0 (Produkcja) / H2 (Testy/Dev)
- **Bezpieczeństwo**: Spring Security 6, BCrypt, Envers (Audit Log)
- **Frontend**: Thymeleaf 3.1, HTML5, Vanilla CSS, JavaScript (AJAX)
- **Raportowanie**: iText 7, JFreeChart, Apache POI
- **Cache & Rate Limiting**: Redis, Lettuce, Bucket4j

---

## 2. Architektura Systemu

### 2.1 Warstwy aplikacji
System stosuje klasyczny podział na warstwy:
1. **Controller**: Obsługa żądań HTTP, walidacja wejścia, mapowanie na DTO.
2. **Service**: Logika biznesowa, transakcyjność, obliczenia statystyczne.
3. **Repository**: Abstrakcja dostępu do danych (JPA/QueryDSL).
4. **Entity**: Odzwierciedlenie modelu relacyjnego bazy danych.

### 2.2 Architektura Wielodostępna (Multi-tenancy)
System implementuje izolację danych na poziomie logicznym (Discriminated Multi-tenancy). Każda encja kluczowa (Urządzenie, Walidacja) jest powiązana z `Company` i `Department`. Filtrowanie danych odbywa się na poziomie zapytań Repository lub automatycznych filtrów Hibernate.

---

## 3. Kluczowe Moduły

- **Moduł Walidacji (`validation`)**: Serce systemu odpowiedzialne za import plików `.vi2`, parowanie danych z rejestratorów i obliczanie wyników.
- **Moduł Bezpieczeństwa (`security`)**: Zarządzanie sesjami, autoryzacją ról oraz mechanizmem odzyskiwania haseł.
- **Moduł Audytu (`audit`)**: Wykorzystuje Hibernate Envers do śledzenia każdej zmiany w encjach krytycznych (ścieżka audytowa zgodna z Annex 11 / 21 CFR Part 11).
- **Moduł Powiadomień (`notification`)**: Silnik analizujący daty wygaśnięcia walidacji i wzorcowań w czasie rzeczywistym.

---

## 4. Bezpieczeństwo Danych
- **Szyfrowanie**: Wszystkie hasła są hashowane algorytmem BCrypt z losową solą.
- **Rate Limiting**: Ochrona przed atakami Brute Force zaimplementowana za pomocą Bucket4j i Redis.
- **CSRF**: Ochrona przed atakami Cross-Site Request Forgery wbudowana w Spring Security.
- **Sanitacja**: Dane wejściowe są walidowane za pomocą Bean Validation (JSR 303).

---

## 5. Integracje i Formaty Plików
- **Import**: Obsługa formatu `.vi2` (binarny format OLE2) przez dedykowany dekoder.
- **Eksport**:
    - **PDF**: Raporty generowane dynamicznie za pomocą iText.
    - **Excel**: Zestawienia generowane przez Apache POI.
