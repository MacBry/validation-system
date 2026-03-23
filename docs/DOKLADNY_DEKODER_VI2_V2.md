# Precyzyjny Dekoder Plików .vi2 (Wersja 2)

Ten dokument opisuje elastyczny algorytm dekodowania binarnych plików z rejestratorów TESTO (format OLE2/CFB), uniezależniony od twardo zakodowanych (hardcoded) limitów ilości punktów oraz stałych interwałów.

## Podstawy Formatowania
Plik `.vi2` jest kontenerem typu OLE2 (podobnie jak archiwalne pliki MS Office). Składa się on z ukrytych strumieni (plików wewnątrz pliku). Główne dane urządzenia gromadzone są w folderze o nazwie zależnej od typu (np. `19788/` lub `52762/`).

Aby stworzyć bezbłędny, uniwersalny parser, musimy odczytać dane z **dwóch sprzężonych ze sobą strumieni**:

---

## 1. Strumień `summary`
**Zastosowanie:** Zastępuje "zgadywanie" długości sesji i ręczne wpisywanie interwału.

**Struktura (Analiza offsetowa):**
Strumień zazwyczaj ma długość `36 bajtów`. Wszystkie zmienne są odczytywane jako **4-bajtowe liczby całkowite bez znaku (Little-Endian Unit32)**.

- **Offset `12-15`**: *Liczba Pomiarów (Count)*. 
  - Wartość jednoznacznie definiuje, ile odczytów znajduje się w pliku. Eliminuje to potrzebę szukania tzw. "paddingu" (zapychania zerami) na końcu strumienia `values`. Oprogramowanie wie dokładnie gdzie skończyć pętlę i nie "ucina" danych.
- **Offset `28-31`**: *Interwał Pomiarowy*.
  - Zapisany w milisekundach (ms). 
  - Przykład: Wartość `10800000` = `3 godziny` / `300000` = `5 minut`. Aplikacja zamiast na sztywno narzucać `+3h`, czyta to pole, konwertuje na sekundy i iteracyjnie dodaje do czasu pierwotnego.

---

## 2. Strumień konfiguracyjny `t17b`
**Zastosowanie:** Niezawodne identyfikowanie sprzętu (Numeru Seryjnego), bez polegania na nazwie wgranego pliku.

Domyślnie obecna logika wyciągała numer seryjny z maski pliku (np. `_58980778_...`). Jest to krytyczna podatność – zmiana nazwy pliku na np. `data.vi2` powodowała błąd zapisu i uniemożliwiała powiązanie danych z odpowiednią komorą w bazie danych.

**Struktura (Analiza offsetowa):**
- **Offset `13`**: *Numer Seryjny Rejestratora (ASCII String)*
  - Od tego bajtu zapisany jest jawnym tekstem numer seryjny przypisany do urządzenia w trakcie jego produkcji/konfiguracji (np. `58980778`). Wystarczy wczytać uciętą tablicę bajtów i zrzutować bezpośrednio na tekst (UTF-8 / ASCII).

---

## 3. Strumień `data/values`
**Zastosowanie:** Odzyskanie realnych temperatur oraz absolutnego czasu włączenia rejestratora.

**Struktura (Analiza offsetowa):**
- System przechodzi obok **pierwszych 4 bajtów** (Nagłówek sekcji).
- Następnie w pętli odczytuje paczki 8-bajtowe (`BYTES_PER_MEASUREMENT = 8`).
  - **Bajty `0-3`:** Temperatura (Float32 / IEEE 754).
  - **Bajty `4-7`:** Metadane czasu (Zegar Ticków Rejestratora).

> **Kluczowy detal:** Rejestratory TESTO potrafią produkować "przesunięte" lub błędne interwały dla punktów `2` do `N` w serii. Jednak metadane **dla pomiaru nr 1 są zawsze poprawne i zsynchronizowane z czasem UTC w momencie naciśnięcia przycisku START**.

**Algorytm odzyskiwania czasu startowego (Epoch 1961):**
Wykorzystuje "Epokę TESTO", czyli początek mierzenia czasu dla tego układu: `1961-07-09 01:30:00`.
1. Odczytaj 4 bajty metadanych T z pierwszego pomiaru.
2. Dni od epoki: `T / 131072` (Gdzie 131072 to kwarcowa dzienna liczba Ticków).
3. Do uzyskanych dni kalendarzowych dodaj sekundy będące "resztą" z podzielenia ułamkowego części dnia.
4. Zamroź ten wynik jako bazowy **Czas Startowy (T0)** dla całej serii.

---

## Ostateczna (Nowa) Logika Dekodowania 

Zamiast obecnej, twardej ramy w kodzie, uniwersalna metoda wylicza finalne dane dla tabel na tej zasadzie:

```text
KROK 1: (Odczyt Summary)
- Przypisz ODCZYTY = bajty[12..15]
- Przypisz INTERWAL_SEKUNDY = bajty[28..31] / 1000

KROK 2: (Odczyt T0 z data/values)
- Przejdź do bajtów [8..11] w strumieniu values i zdekoduj bazowy Tick
- Oblicz DATĘ_START (T0) metodą "Epoki 1961"

KROK 3: (Pętla pomiarowa)
- Uruchom pętlę od i=0 do ODCZYTY
- Temperatura dla pętli [i] to 4 bajty wprost ze strumienia (na przesuniętym offsecie)
- Czas dla pętli [i] to: T0 + (i * INTERWAL_SEKUNDY)
```

## Co jeszcze może uelastycznić kod?
Aby parser był przemyślany w 100%, można poddać badaniom 3 ostatni ważny strumień w formacie OLE2:

1. **`data/timezone` (188 bajtów):**
  - Częstym problemem rejestracji dla serwerów zagranicznych jest czas strefowy (Timezone / DST). Analiza tego strumienia udowodniła, że przechowuje on pełną hardware'ową konfigurację czasu lokalnego w standardzie Windowsowym.
  - **Offset 16 (Int32)**: Wartość `-60` (Bias w minutach. -60 min = UTC+1).
  - **Okolice Offsetu 20+**: Jawny tekst w formacie `UTF-16LE` (np. `"Środkowoeuropejski czas stand."` oraz `"Środkowoeuropejski czas letni"`).
  - Pozwala to systemowi VI2 bezbłędnie przeliczać surowe ticki sprzętowe z czasu UTC na dokładny czas lokalny użytkownika niezależnie na jakim kontynencie rejestrator był włączony. Powinno się to pobierać i dynamicznie dodawać ten `Bias` do czasu wyliczonego z pierwszego punktu na siatce czasu.
