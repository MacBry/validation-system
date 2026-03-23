# Instrukcja Administratora: Validation Cold Control (VCC)

## 1. Wstęp
Niniejszy dokument jest przeznaczony dla administratorów systemu (Super Admin, Company Admin) i opisuje funkcje związane z konfiguracją i nadzorem nad aplikacją VCC.

---

## 2. Zarządzanie Strukturą Organizacyjną
Aplikacja VCC wspiera strukturę wielofirmową. Jako administrator możesz:

### 2.1 Zarządzanie Firmami (Companies)
- **Tworzenie**: Dodawanie nowych podmiotów gospodarczych do systemu. Każda firma ma oddzielną bazę danych/zasobów.
- **Edycja**: Zmiana danych teleadresowych, NIP, oraz logotypów firmowych widocznych na raportach.

### 2.2 Zarządzanie Działami (Departments)
- Działy pozwalają na logiczny podział urządzeń wewnątrz jednej firmy (np. Dział Mikrobiologii, Magazyn Wysokiego Składowania).

---

## 3. Zarządzanie Użytkownikami i Uprawnieniami

### 3.1 Role w systemie
System posiada zdefiniowane role:
- **ROLE_USER**: Podstawowy dostęp do przeglądania i wprowadzania danych.
- **ROLE_APPROVER**: Uprawnienie do zatwierdzania raportów walidacyjnych.
- **ROLE_COMPANY_ADMIN**: Pełne zarządzanie w obrębie jednej firmy.
- **ROLE_SUPER_ADMIN**: Pełny dostęp techniczny do całego systemu.

### 3.2 Nadawanie dostępów (Permissions Cache)
System VCC wykorzystuje mechanizm cache'owania uprawnień. Przy dodawaniu użytkownika należy:
1. Zdefiniować listę **Działów**, do których użytkownik ma mieć dostęp.
2. Wybrać odpowiednią **Rolę**.

---

## 4. Bezpieczeństwo i Hasła

### 4.1 Polityka haseł
- Hasła w systemie są przechowywane w formie zaszyfrowanej (BCrypt).
- **Wygaśnięcie hasła**: Standardowo hasło wygasa po 90 dniach (konfigurowalne w ustawieniach firmy).
- **Monitoring**: Administrator widzi na swoim pulpicie listę użytkowników, których hasła wygasną w ciągu najbliższych 30 dni.

### 4.2 Resetowanie haseł
Administrator może wygenerować link do resetowania hasła dla użytkownika lub wymusić zmianę hasła przy następnym logowaniu.

---

## 5. Konfiguracja Powiadomień
Aplikacja posiada globalne progi powiadomień:
- **Walidacja**: Ostrzeżenie pojawia się na 30 dni przed upływem roku od ostatniej walidacji.
- **Wzorcowanie**: Ostrzeżenie na 60 dni przed końcem ważności certyfikatu.

---

## 6. Logi Systemowe i Audyt
Każda istotna zmiana (zmiana statusu walidacji, usunięcie urządzenia, zmiana uprawnień) jest rejestrowana w logach audytowych dostępnych dla administratora.
