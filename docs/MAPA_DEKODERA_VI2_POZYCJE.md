# MAPA OLE2 DLA DEKODERA VI2 (Zestawienie dla Inżyniera)

Ten dokument jest szybką referencją typu "ściągawka" (Cheat Sheet) z dokładnymi lokacjami (offsetami) i typami zmiennych we wnętrzu systemu plików OLE2 formatu Testo `.vi2`. Został stworzony na podstawie pełnej inżynierii wstecznej i testów w terenie.

## Ważne zasady nawigowania OLE2 (Biblioteka POIFS)
1. Główny folder przechowujący strumienie ma zawsze w każdym pliku **inną nazwę** będącą losowym identyfikatorem liczbowym (np. `52762`, `7130`, `1578`). 
2. Parser **nie może twardo szukać tej nazwy**. Musi pobrać korzeń struktury OLE (Root) i dynamicznie przejść do **pierwszego i jedynego** podkatalogu przed odczytaniem strumieni.
3. Wszystkie dane binarne odczytywane są w standardzie **LITTLE-ENDIAN**.

---

## MAPA LOKACJI KLUCZOWYCH DANYCH

### 1. Pobieranie Numeru Seryjnego 
- **Strumień docelowy:** `[KORZEŃ]/t17b`
- **Offset (Pozycja w pliku):** Od bajtu `13`
- **Format / Typ Danych:** Czysty strumień bajtów formatowany jako tekst ASCII (np. `58980784`). Brak zakończenia nulem na ściśle określonym offsecie. Należy skonwertować bajty na String ignorując ewentualne zera przed następnymi danymi.

### 2. Status Parametrów Sesji (Ilość / Interwał)
- **Strumień docelowy:** `[KORZEŃ]/summary`

| Zmienna | Offset Start | Rozmiar w Bajtach | Typ Danych | Opis / Przykład |
| :--- | :---: | :---: | :--- | :--- |
| **Ilość Zmierzonych Punktów** | `12` | 4 bajty | 32-bit Integer (Unsigned) | Ilekroć pętla sczytywania punktów w sekcji values powinna się wykonać (np. `6` lub `40`). Eliminuję rzeźbienie i "padding". |
| **Interwał Odczytu** | `28` | 4 bajty | 32-bit Integer (Unsigned) | Podawany w milisekundach (ms). Trzeba go podzielić przez 1000, by uzyskać skok czasowy w pętli dla sekund (np. `10800000` = `3 godziny`). |

### 3. Zegary Lokalnego Czasu (Strefa Czasowa)
- **Strumień docelowy:** `[KORZEŃ]/data/timezone`
- **Rozmiar Strumienia:** 188 Bajtów
- **Offset Start:** `16`
- **Format / Typ Danych:** 32-bit Integer (Signed)
- **Opis:** Offset biasu w minutach dla lokalnego UTC. W Polsce powinieneś napotkać tu liczby `-60` (Zima: UTC+1) lub `-120` (Lato: UTC+2). Wycięty INT32 pozwala automatycznie narzucać `ZoneOffset.ofTotalSeconds(bias * -60)`. Niewykorzystanie tego punktu zakłamuje pliki importowane zza granicy.

### 4. Pętla Danych: Temperatury i Tick Czasu
- **Strumień docelowy:** `[KORZEŃ]/data/values`
- **Typ Struktury:** Nagłówek i macierz (Pętla powtarzalna)

**Nagłówek sekcji:**
- Od Offsetu `0` do `3`: Informacje o bloku (Należy Pominąć / Skoczyć o 4 bajty).

**Analiza bloku pętli (Długość bloku = 8 Bajtów):**
Odczytywanie należy umieścić w pętli powtórzonej tyle razy, ile odczytano ze zmiennej `Ilość Zmierzonych Punktów` (strumień summary). Indeks pętli oznaczmy jako `i` (zaczynając od `i=0`).  

Zatem dany blok zaczyna się od offsetu w pętli równego `B_START = 4 + (i * 8)`.

| Wartość Cząstkowa | Offset Wewnętrzny Bloku | Typ Danych | Opis Algorytmiczny |
| :--- | :---: | :--- | :--- |
| **Temperatura (°C)** | od `B_START` (zajmuje 4B)| IEEE 754 Float32 (Little Endian) | Prawidłowo sklasyfikowana temperatura. Może zostać pocięta przez DecimalFormat do formatu "X.XX". |
| **Tick (Czasowy Metadanych)** | od `B_START + 4` (zajmuje 4B)| 32-bit Integer (Unsigned) | Reprezentuje liczbę uderzeń kwarcu. Przenieś przez formułę "EPOKA 1961" omówioną niżej. |

---

## 📅 Ustawianie Czasu: "EPOKA 1961 TESTO"
Aby określić absolutną datę Startu na podstawie pierwszej zmiennej **Tick** z pierwszego bloku:

1. Base Epoch Zegara: `1961-07-09T01:30:00` (UTC)
2. Podział Ticku dla uderzeń dobowych: Zdobytą liczbę całkowitą trzeba podzielić przez stałą programową `DAY_TICK = 131072.0` na obiekcie Float/Double.
3. Obliczenie dni skoku: Część całkowita obcięta z wyniku to pełna liczba dni od Epoki '61 do dodania.
4. Obliczenie mikroskoków (Sekund): Ułamkową resztę z wyniku należy przykryć mnożnikiem sekund w dniu `(reszta * 86400)`.  Sekundy dodajesz do Epoki.
5. Zamróź ten czas jako `T0` (Start).

*Uwaga Inżynierska: Testo potrafi uszkadzać ticki od drugiego bloku. Należy bezwzględnie przeczytać Tick Czasowy **tylko** w pierwszej iteracji pętli `(i=0)`. Czasy dla iteracji `i=1` i wzwyż należy wyliczać ręcznie, opierając się na dodaniu wyciągniętego z sekcji summary INTERWAŁU do zamrożonego T0.*

--- 

Powodzenia w refaktoryzacji, trzymaj się tych stałych wartości a otrzymasz najbardziej kuloodporny VI2 Parser na rynku!
