# Matryca Śladowości (Traceability Matrix): Validation Cold Control (VCC)

## 1. Cel dokumentu
Matryca Śladowości zapewnia formalne powiązanie pomiędzy wymaganiami użytkownika (URS), ich realizacją funkcjonalną (FS) oraz weryfikacją. Jest to kluczowy dokument w procesie walidacji systemów skomputeryzowanych (GAMP 5).

---

## 2. Tabela Powiązań (URS -> FS)

| Id Wymagania (URS) | Id Funkcji (FS) | Opis Powiązania | Status |
|:---|:---|:---|:---|
| **UR-FUNC-01** | FS-FUNC-01 | Ewidencja urządzeń i rejestratorów w module Resources. | ✅ Pełny |
| **UR-FUNC-02** | FS-FUNC-02 | Walidacja daty wzorcowania w CalibrationService. | ✅ Pełny |
| **UR-FUNC-03** | FS-FUNC-03 | Import i dekodowanie plików .vi2 przez Vi2Decoder. | ✅ Pełny |
| **UR-FUNC-03** | FS-FUNC-04 | Obliczenia statystyczne (MKT, Stab, Homogen). | ✅ Pełny |
| **UR-FUNC-04** | FS-FUNC-05 | Generowanie raportów PDF przez iText & JFreeChart. | ✅ Pełny |
| **UR-SEC-01**  | FS-SEC-02 | Izolacja danych na poziomie Company i Department. | ✅ Pełny |
| **UR-SEC-02**  | FS-SEC-02 | Mechanizm wygasania haseł i blokady kont. | ✅ Pełny |
| **UR-COMP-01** | FS-SEC-01 | Rejestracja Audit Trail przez Hibernate Envers. | ✅ Pełny |
| **UR-COMP-02** | FS-FUNC-06 | Proces zatwierdzania walidacji i blokada edycji. | ✅ Pełny |

---

## 3. Weryfikacja (Testy)

System VCC został poddany testom automatycznym i manualnym, które potwierdzają realizację powyższych punktów:

- **Testy jednostkowe**: Weryfikacja logiki obliczeniowej, wzorcowań i powiadomień.
- **Testy integracyjne**: Weryfikacja multi-tenancy i bezpieczeństwa API.
- **Testy manualne**: Weryfikacja interfejsu Thymeleaf oraz poprawności raportów PDF.

---

## 4. Podsumowanie
Wszystkie zdefiniowane wymagania użytkownika (URS) znalazły odzwierciedlenie w specyfikacji funkcjonalnej (FS) i zostały zaimplementowane w systemie. Matryca potwierdza 100% pokrycia wymagań.
