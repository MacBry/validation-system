# Specyfikacja Funkcjonalna (FS): Validation Cold Control (VCC)

## 1. Wstęp
Niniejszy dokument (FS) opisuje szczegółowo funkcjonalności systemu VCC i sposób ich realizacji, stanowiąc odpowiedź na wymagania zawarte w Specyfikacji Wymagań Użytkownika (URS).

---

## 2. Funkcje Zarządzania Urządzeniami i Rejestratorami

### 2.1 Ewidencja Urządzeń (FS-FUNC-01)
- **Realizacja (UR-FUNC-01)**: Interfejs użytkownika w `/devices` pozwala na dodawanie, edycję i usuwanie urządzeń.
- **Dane**: Przechowywanie numeru inwentarzowego, nazwy, typu komory oraz zakresu temperatur roboczych.
- **Powiązania**: Każde urządzenie jest przypisane do konkretnego Działu (`Department`).

### 2.2 Weryfikacja Wzorcowań (FS-FUNC-02)
- **Realizacja (UR-FUNC-02)**: Podczas przypisywania rejestratorów do walidacji, system wywołuje usługę `CalibrationService`, która sprawdza pole `validUntil` dla każdego rejestratora.
- **Blokada**: Jeśli `validUntil` jest mniejsze niż data planowanej walidacji, system wyświetla komunikat o błędzie i uniemożliwia zapis.

---

## 3. Proces Procesowania Danych i Obliczeń

### 3.1 Import i Dekodowanie Plików (FS-FUNC-03)
- **Realizacja (UR-FUNC-03)**: System wykorzystuje dedykowany parser `Vi2Decoder` (wspierający standard OLE2), który ekstrahuje surowe dane pomiarowe z plików bocznych.
- **Filtrowanie**: Dane są czyszczone z błędnych odczytów (np. błędy czujnika) przed rozpoczęciem obliczeń.

### 3.2 Silnik Statystyczny (FS-FUNC-04)
- **MKT (Mean Kinetic Temperature)**: Obliczane zgodnie z równaniem Arrheniusa (energia aktywacji domyślnie 83.144 kJ/mol).
- **Stabilność/Jednorodność**: Wyznaczane jako różnice między kanałami oraz odchylenia od temperatury zadanej w funkcji czasu.

---

## 4. Raportowanie i Zatwierdzanie

### 4.1 Generowanie Raportów PDF (FS-FUNC-05)
- **Realizacja (UR-FUNC-04)**: Biblioteka `iText 7` składa raport końcowy, pobierając dane z encji `Validation` oraz powiązanych `MeasurementSeries`.
- **Wykresy**: `JFreeChart` generuje przebiegi temperatur, które są wstrzykiwane do dokumentu PDF jako obrazy SVG/PNG.

### 4.2 Przepływ Zatwierdzania (FS-FUNC-06)
- **Realizacja (UR-COMP-02)**: Status `APPROVED` może zostać nadany tylko przez użytkownika z uprawnieniem `ROLE_APPROVER` poprzez dedykowany formularz zatwierdzenia. Po tej akcji pole `sealed` zostaje ustawione na `true`, co uniemożliwia dalszą edycję.

---

## 5. Bezpieczeństwo i Integrity

### 5.1 Audit Trail (FS-SEC-01)
- **Realizacja (UR-COMP-01)**: Wykorzystanie Hibernate Envers. System tworzy wpis w tabeli `REVINFO` przy każdej transakcji, a stare wersje danych są kopiowane do tabel `*_AUD`.
- **Interfejs**: Logi audytowe są dostępne pod adresem `/admin/audit-log`.

### 5.2 Multi-tenancy (FS-SEC-02)
- **Realizacja (UR-SEC-01)**: Wykorzystanie `Filter` w Spring Security oraz mechanizmów JPA do automatycznego wstrzykiwania warunku `WHERE company_id = ?` do zapytań SQL.
