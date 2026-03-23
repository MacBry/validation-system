# Instrukcja Użytkownika: Validation Cold Control (VCC)

## 1. Wstęp
System **Validation Cold Control (VCC)** służy do monitorowania, zarządzania i walidacji urządzeń chłodniczych oraz rejestratorów temperatury. Niniejszy dokument opisuje podstawowe operacje, jakie użytkownik może wykonywać w systemie.

---

## 2. Rozpoczęcie pracy

### 2.1 Logowanie
1. Przejdź do strony logowania.
2. Wprowadź swoją **nazwę użytkownika** (lub e-mail) oraz **hasło**.
3. Kliknij przycisk **Zaloguj się**.

> [!NOTE]
> Jeśli zapomniałeś hasła, skorzystaj z opcji "Zapomniałem hasła" na ekranie logowania.

### 2.2 Pulpit (Dashboard)
Po zalogowaniu zobaczysz Pulpit, który zawiera:
- **Kafelki podsumowujące**: Liczba urządzeń, rejestratorów oraz aktywnych walidacji.
- **Powiadomienia (🔔)**: Ikona dzwonka w prawym górnym rogu informuje o zbliżających się terminach walidacji, wzorcowań lub wygaśnięciu hasła.
- **Wyszukiwarka**: Pozwala szybko odnaleźć urządzenie po nazwie lub numerze inwentarzowym.

---

## 3. Zarządzanie Zasobami

### 3.1 Urządzenia Chłodnicze
Sekcja ta pozwala na ewidencję lodówek, zamrażarek i innych komór chłodniczych.
- **Dodawanie**: Kliknij "Dodaj urządzenie", wypełnij nazwę, model, numer inwentarzowy oraz wybierz typ komory.
- **Szczegóły**: Klikając na nazwę urządzenia, zobaczysz jego pełną historię, w tym przypisane walidacje.

### 3.2 Rejestratory Temperatury
- **Dodawanie rejestratora**: Wprowadź numer seryjny oraz model.
- **Wzorcowanie**: Każdy rejestrator musi posiadać aktualne świadectwo wzorcowania. System przypomina o zbliżającym się terminie wygaśnięcia wzorcowania na 60 dni przed datą graniczną.

---

## 4. Proces Walidacji
To najważniejszy proces w aplikacji, podzielony na etapy:

### Krok 1: Utworzenie walidacji
Wybierz urządzenie, które chcesz poddać walidacji, i wybierz "Nowa walidacja".

### Krok 2: Konfiguracja pomiaru
Wskaż rejestratory użyte podczas pomiaru (np. w rogach komory i na środku). System automatycznie zweryfikuje, czy rejestratory mają ważne wzorcowanie.

### Krok 3: Import danych
Wgraj pliki z danymi pomiarowymi (format `.vi2`). System automatycznie przemieli dane i przygotuje statystyki.

### Krok 4: Analiza statystyczna
System wyliczy:
- Temperaturę średnią, minimalną i maksymalną.
- Odchylenie standardowe.
- Stabilność i jednorodność rozkładu temperatur.

### Krok 5: Zatwierdzenie
Po sprawdzeniu poprawności danych, walidacja zostaje przesłana do zatwierdzenia. Po zatwierdzeniu generowany jest raport PDF.

---

## 5. System Powiadomień
W górnym pasku znajduje się system alertów:
- 🔴 **Kolor czerwony (Danger)**: Termin minął lub zostało mniej niż 7 dni.
- 🟡 **Kolor żółty (Warning)**: Termin zbliża się (np. 30 dni do walidacji).

Kliknij w ikonę dzwonka, aby zobaczyć szczegóły i przejść bezpośrednio do elementu wymagającego uwagi.

---

## 6. Ustawienia Profilu
Użytkownik może w każdej chwili:
- Zmienić swoje hasło.
- Zaktualizować dane kontaktowe.
- Sprawdzić historię swoich logowań.
