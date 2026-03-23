# Opis Procesów Biznesowych: Validation Cold Control (VCC)

## 1. Cel dokumentu
Niniejszy dokument opisuje logiczny przebieg procesów w aplikacji VCC, definiując przepływ danych oraz zasady rządzące systemem.

---

## 2. Cykl życia Walidacji (Validation Lifecycle)
Każda walidacja w systemie przechodzi przez określone stany, które determinują możliwe do wykonania akcje:

### 2.1 DRAFT (Szkic)
- **Opis**: Nowo utworzona walidacja.
- **Akcje**: Można zmieniać przypisane urządzenia, rejestratorzy oraz daty.
- **Przejście**: Po załadowaniu danych pomiarowych przechodzi do stanu *In Progress*.

### 2.2 IN PROGRESS (W toku)
- **Opis**: Walidacja z załadowanymi danymi, poddawana analizie.
- **Akcje**: Obliczanie parametrów statystycznych, generowanie wykresów.
- **Przejście**: Po zakończeniu analizy i weryfikacji przez użytkownika, przesyłana do zatwierdzenia.

### 2.3 COMPLETED (Zakończona)
- **Opis**: Walidacja, w której obliczenia zostały sfinalizowane.
- **Przejście**: Oczekiwanie na akcję osoby z uprawnieniami *Approver* (Zatwierdzający).

### 2.4 APPROVED / REJECTED (Zatwierdzona / Odrzucona)
- **APPROVED**: Walidacja prawomocna. Na tym etapie dane są blokowane przed edycją. Generowany jest raport końcowy.
- **REJECTED**: Walidacja odrzucona z powodu błędów. Wraca do stanu *Draft* lub *In Progress* w celu korekty.

---

## 3. Hierarchia Danych i Multi-tenancy
System został zaprojektowany w architekturze wielodostępnej (Multi-tenancy):

1. **Firma (Company)**: Najwyższy poziom organizacji. Dane między firmami są odizolowane.
2. **Dział (Department)**: Podział wewnątrz firmy. Użytkownicy mogą mieć dostęp do jednego lub wielu działów.
3. **Urządzenie (CoolingDevice)**: Przypisane do konkretnego działu.
4. **Walidacja (Validation)**: Powiązana z urządzeniem i grupą rejestratorów.

---

## 4. Proces Kontroli Wzorcowań (Calibration Control)
System automatycznie dba o spójność metrologiczną:
- Rejestrator bez ważnego świadectwa wzorcowania nie może zostać użyty w nowej walidacji.
- Data walidacji musi zawierać się w terminie ważności wzorcowania wszystkich użytych rejestratorów.
- Powiadomienia o wygasaniu wzorcowań są generowane proaktywnie (60 dni i 14 dni przed końcem).

---

## 5. Mechanizm Obliczeniowy
Statystyki w systemie VCC bazują na:
- **Średniej kinetycznej temperatury (MKT)**: Do oceny wpływu wahań temperatury na przechowywane materiały.
- **Analizie jednorodności**: Porównanie temperatur z różnych punktów komory w tym samym czasie.
- **Stabilności**: Ocena wahań temperatury w czasie w konkretnym punkcie.
