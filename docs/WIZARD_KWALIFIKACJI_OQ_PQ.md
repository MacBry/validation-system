# Projekt Architektury: "Wizard" Kwalifikacji Urządzeń Chłodniczych (OQ, PQ, Mapowanie)

Poniższy dokument analizuje i porządkuje koncepcję utworzenia wieloetapowego kreatora (wizarda) do przeprowadzania walidacji i kwalifikacji w branży Life-Science.

## 🎯 Wizja Główna (Poziom Biznesowy)
Celem narzędzia jest modularyzacja procesu tworzenia obszernych raportów walidacyjnych. Obecny proces jest jednolity; zastąpi go proces **dynamiczny** oparty na asystencie (wizard), dopasowujący kolejne etapy, formularze testów i bloki gotowych dokumentów (PDF) na podstawie wybranej procedury (OQ / PQ / Mapowanie).

W przyszłości architektura ma pozwolić na łatwe dołożenie modułów:
- Kwalifikacja międzyokresowa urządzeń pomiaru temperatur (Re-kalibracja / Wzorcowanie kontrolne).
- Kontrola przebiegu transportu (Zimny łańcuch / Cold Chain).

---

## 🏗️ Architektura Kreatora (Flow Użytkownika)

Proces został podzielony na tzw. "kroki" (Steps). Z racji złożoności prawnej i wymogów walidacji GMP/GDP, obostrzono politykę przechodzenia wstecz w wizardzie. Cały proces do momentu podpisu ma status **PROJEKT / DRAFT**.

### 🚥 Reguły Nawigacji
* **Możliwość cofania w każdym momencie:** Dopuszczalne na etapie Krok 1 ⟷ Krok 3.
* **Blokada edytowania założeń (Lock-in):** Po przejściu Kroku 3 (Kryteria Akceptacji), formularz tworzy ramę prawną badania. Cofnięcie do ustaleń z Kroku 2 lub 3 skutkowałoby naruszeniem spójności wygenerowanych później dokumentów. Użytkownik może nawigować dowolnie wstecz tylko do Kroku 4 (Metryka Badania).
* **Zapisywanie Sesji (Auto-Save):** Każde kliknięcie "Dalej" (lub wyjście z systemu) zapisuje aktualny stan procesu (Status: PROJEKT).

---

## 🪜 Struktura Kroków Kreatora (Wizard Steps)

### Krok 1: Wybór Procedury
Wejście z dedykowanego kafelka na stronie głównej (Dashboardze).
- **Akcja:** Użytkownik wybiera z płytki typ badania.
- **Dostępne na start:** Kwalifikacja Operacyjna (OQ), Kwalifikacja Procesowa (PQ), Mapowanie Rozkładu Temperatur.
- **Decyzja systemu:** Od tego momentu system ładuje odpowiednie "kafelki raportów" w krokach końcowych (wspólne szablony + specyficzne dodatki).

### Krok 2: Kontekst Badawczy (Urządzenie i Materiał)
- **Akcja:** Użytkownik wskazuje konkretne urządzenie/chłodnię z bazy danych organizacji.
- **Wyświetlanie (Podsumowanie):** System prezentuje wyciąg (read-only) kluczowych danych dotyczących urządzenia, na które walidator musi zwrócić uwagę (np. pojemność, rok produkcji, minimalna wymagana liczba punktów pomiarowych, status kwalifikacji). Dodatkowo wyświetlany jest typ magazynowanego leku/materiału, aby użytkownik miał pewność, w jakim reżimie pracuje.

### Krok 3: Kryteria Akceptacji (Serce Systemu GMP)
Ten ekran definiuje definicje sukcesu procedury.
- **Kryteria Obowiązkowe (Złote Standardy):** Pre-wypełnione lub zablokowane kryteria niezbędne dla DPD/GMP (np. Temperatury Min/Max, MKT globalne poniżej progu dla badanego materiału, maksymalny czas przebywania w stanie alarmowym).
- **[NOWOŚĆ] Składak Kryteriów Niestandardowych (Custom Acceptance Criteria):**
  - Walidator może rozbudować raport o dodatkowy parametr formalny.
  - Wybiera zmienną z listy pre-obliczonych statystyk na poziomie pojedynczej serii i nakłada ograniczenie matematyczne.
  - Przykład: "Średnia z Serii musi być < 6.5°C", "Odchylenie Standardowe < 0.2".

### Krok 4: Ustalenia Rutynowe i Załadunek
- **Akcja 1:** Zdefiniowanie statusu załadunku (Urządzenie puste / Częściowo załadowane / W pełni załadowane).
- **Akcja 2:** Opis rejestratora rutynowego. Użytkownik wskazuje, gdzie na co dzień zamontowany jest (lub będzie) docelowy czujnik ciągłego monitoringu (BMS/RMS) w wybranym urządzeniu chłodniczym.

### Krok 5: Specyficzne Testy Procedury (OQ/PQ)
- Panel generuje dedykowane (dodatkowe) szablony formularzy badawczych zależnie od dokonanego w Kroku 1 wyboru.
- **Jeśli OQ (Weryfikacja Operacyjna):** Pojawiają się pola do opisania testów sprzętowych (niewymagających zapisu loggerów TESTO). 
  - Przykład: Próba na zanik napięcia zasilającego (z czasem T1 i T2), Weryfikacja działania alarmów akustyczno-optycznych z potwierdzeniem aktywacji dialera (SMS/Mail), Test otwarcia drzwi.
- **Jeśli PQ (Weryfikacja Procesowa):** Inne specyficzne pytania i checklisty operacyjne ze strony personelu obsługującego ładunek farmaceutyczny.

### Krok 6: Dodawanie Serii Pomiarowych (V2)
Zaktualizowany układ znany z klasycznej wersji:
- Mapowanie układu w przestrzeni rejestratora (Siatka trójwymiarowa, np. T1: Dół-Przód-Lewo, M1: Środek-Środek).
- Wrzucenie binarnego pliku `.vi2`. (Korzystające z ulepszonego, odpornego na błędy dekodera formatu OLE2).

### Krok 7: Przegląd Serii i Detekcja Krawędziowa
Mając zebrany sprzęt i wymagania, system zestawia to z rzeczywistymi zrzutami `.vi2`.
- Wyliczenie odchyleń (Deviations/Excursions) dla *każdej serii rejestratora osobno*, względem zdefiniowanych w Kroku 3 parametrów i Kryteriów Akceptacji.
- Trójpozycyjny panel komentarzy pod każdym odstępstwem (na wypadek usprawiedliwiania otwarcia drzwi podczas inwentaryzacji itp).
- Wizualne kodowanie (kolorami) serii: Zielone (Pass), Czerwone (Oblała/Do Usprawiedliwienia).

### Krok 8: Statystyki Globalne i Odchylenia Procesowe
- Wyświetlenie matematycznych danych w ujęciu całkowitym (Global MKT aplikacji statystycznej, Coldspot, Hotspot).
- Identyfikacja punktów krytycznych układu.
- Decydujące podsumowanie wytypowanych odchyleń rzutujących bezpośrednio na ostateczny osąd w DPD. Główne pole tłumaczenia analityka z odchyleń.

### Krok 9: Kompletacja, Podgląd PDF i Finalizacja
- "Zestaw klocków LEGO": Kreator dynamicznie łączy moduły PDF (Tabela A dla OQ nie jest taka sama jak dla PQ). Domyślny, generyczny nagłówek dokumentu, z doklejanym formularzem oceny sprzętowej (zanik zasilania) plus tabele MKT i MKT Delta.
- Użytkownik weryfikuje wizualny podgląd wygenerowanej paczki dokumentów.
- Zgłasza poprawek (wtedy wraca do Kroku 4-8), albo przystępuje do formalnego **podpisu elektronicznego (Certyfikat PKCS12)**.
- Zamknięcie wizarda z ostatecznym statusem (np. ZATWIERDZONE, DO ODRZUCENIA).

---

## 🛠️ Wnioski Architektoniczne dla Asystentów Code Claude
Aby zrealizować to przedsięwzięcie będziemy musieli pomyśleć nad:
1. **Stanowa Baza Danych:** Zamienić jedno-strzałowe tworzenie rekordu Validacji na tzw. StateMachine (Maszynkę stanową bazy), zapisującą tymczasowe progresy jako encję `ValidationDraft`.
2. **Strategy Pattern na Backendzie:** Logika generowania PDF i widoków powinna korzystać z wzorca "Strategii", aby elastycznie wybierać i wypełniać fragmenty protokołu na podstawie enumeratora wybranego w Kroku 1 (`ValidationProcedureType.OQ`, `PQ`, `MAPPING`). Zabezpieczy to kod pliku Service przez "ifozą" (setkami zagnieżdżonych `if...else`).
3. **Nowa Encja `CustomAcceptanceCriterion`:** Umożliwiająca stworzenie asocjacji Many-to-One dla konkretnego draftu Badania, przechowująca wyrażenie matematyczne do ewaluacji (Target Statistic -> Operator (`>` / `<` / `=`) -> Wartość Graniczna).
4. **Envers Auditing (Ostrzeżenie przed Modułem Security):** Upewnienie się, że stan projektu na bieżąco loguje każdego analityka który "dotknął" tego draftu, a po zamknięciu blokuje modyfikacje.
