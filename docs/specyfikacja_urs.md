# Specyfikacja Wymagań Użytkownika (URS): Validation Cold Control (VCC)

## 1. Wstęp i Cel
Celem niniejszego dokumentu jest zdefiniowanie wymagań użytkownika dla systemu **Validation Cold Control (VCC)**, który służy do zarządzania procesami walidacji urządzeń chłodniczych w środowiskach regulowanych (GAMP 5 / GMP).

---

## 2. Wymagania Funkcjonalne

### 2.1 Zarządzanie Zasobami (UR-FUNC-01)
- System musi umożliwiać ewidencję urządzeń chłodniczych (lodówki, zamrażarki).
- System musi umożliwiać ewidencję rejestratorów temperatury wraz z ich numerami seryjnymi.

### 2.2 Kontrola Wzorcowań (UR-FUNC-02)
- System musi blokować użycie rejestratora w procesie walidacji, jeśli nie posiada on ważnego świadectwa wzorcowania.
- System musi automatycznie obliczać datę wygaśnięcia wzorcowania na podstawie daty ostatniego certyfikatu.

### 2.3 Proces Walidacji (UR-FUNC-03)
- System musi umożliwiać import danych pomiarowych z plików o rozszerzeniu `.vi2`.
- System musi automatycznie wyliczać kluczowe parametry statystyczne (MKT, średnia, Min/Max, odchylenie standardowe).

### 2.4 Raportowanie (UR-FUNC-04)
- System musi umożliwiać generowanie raportów z walidacji w formacie PDF.
- Raporty muszą zawierać informacje o urządzeniu, użytych rejestratorach, wynikach pomiarów oraz statusie zatwierdzenia.

---

## 3. Wymagania Bezpieczeństwa (Security Requirements)

### 3.1 Kontrola Dostępu (UR-SEC-01)
- Dostęp do systemu musi być zabezpieczony unikalnym loginem i hasłem.
- System musi obsługiwać strukturę multi-tenant, izolując dane między różnymi firmami.

### 3.2 Zarządzanie Hasłami (UR-SEC-02)
- Hasła użytkowników muszą wygasać po określonym czasie (domyślnie 90 dni).
- Użytkownik musi zostać powiadomiony o zbliżającym się terminie zmiany hasła.

---

## 4. Wymagania Dotyczące Zgodności (Compliance)

### 4.1 Ścieżka Audytowa (UR-COMP-01)
- System musi rejestrować każdą istotną zmianę w danych (kto, co, kiedy zmienił).
- Historia zmian musi być dostępna dla administratora w celach kontrolnych (zgodność z Annex 11 / 21 CFR Part 11).

### 4.2 Podpis i Zatwierdzanie (UR-COMP-02)
- Walidacja musi przejść proces formalnego zatwierdzenia przez osobę z rolą `Approver` przed oznaczeniem jej jako prawomocnej.
