# Specyfikacja Techniczna Dekodera Pojemników Danych TESTO (Format VI2)
**Dokumentacja Systemowa do procesu Computerized System Validation (CSV)**

## 1. Informacje Ogólne
Niniejszy dokument stanowi specyfikację oprogramowania dla modułu dekodera autorskich plików binarnych `.vi2` generowanych przez urządzenia pomiarowe firmy TESTO (w tym seria T174, T175). Dekoder (klasa `Vi2FileDecoder.java`) stanowi kluczowy element obróbki i importu danych surowych w Systemie Walidacyjnym do pomiarów jakościowych zgodnych z wytycznymi GMP / GDP. Oprogramowanie to zostało zweryfikowane poprzez inżynierię wsteczną w procesie czarnej skrzynki (Black-Box Testing).

## 2. Architektura Kontenera Pliku (Standard OLE2)
Każdy plik `.vi2` jest obiektem OLE2 (Object Linking and Embedding, Compound File Binary Format), dzielącym swoją bazową strukturę hermetyzacji m.in. ze starszymi plikami pakietu pakietu Microsoft Office.
Wewnątrz OLE2 znajduje się główny folder bazowy tzw. `Root Entry`.

*Wymaganie projektowe CSV-1:* Dekoder **nie opiera nawigacji o statyczne nazewnictwo katalogu głównego**, który jest unikalnym ciągiem znaków lub liczb (np. `52762` lub `7130`). Zamiast tego dekoder buduje drzewo systemowe poprzez dynamiczne wskazanie pierwszego odnalezionego węzła jako folderu parametrów pracy.

### Struktura znanych strumieni
Po odnalezieniu głównego i jedynego folderu urządzenia w kontenerze, system operuje na 4 kluczowych, niemodyfikowalnych strumieniach w standardzie odczytu **Little-Endian**.

```text
Root Entry/
 └── [unikalne_id]/
     ├── summary                    -> Przechowuje wielkość sesji
     ├── t17b                       -> Metryka techniczna
     ├── SummaryInformation          
     └── data/
         ├── values                 -> Tablica pomiarów T1
         ├── timezone               -> Konfiguracja DST
         └── schema                 
```

## 3. Przetwarzanie i Ekstrakcja Danych Biznesowych 

### 3.1 Identyfikowalność Rejestratora (Numer Seryjny)
- **Cel Biznesowy:** Nienaruszalne przypisanie danych do certyfikowanego punktu pomiarowego.
- **Odczyt Dekodera:** Przeszukuje strumień `t17b` począwszy od offsetu `13`. Zabezpiecza proces przed fałszowaniem nazw plików systematycznym sprawdzaniem zgodności znaków formatu znaków w kodowaniu układu ASCII.
- **Wytyczna GxP/ALCOA+:** Identyfikowalność metryki pliku zawsze uzyskiwana jest ze strumienia binarnego, a wpis pochodzący od użytkownika (np. nazwa pliku w systemie) służy wyłącznie w charakterze "zapasowym" (Fallback Method).

### 3.2 Dynamiczne Wymiary Sesji 
Przekraczając barierę szkodliwego twardego progowania danych (np. *hard limits* i założenia długości interwałów), poprawiona implementacja wyciąga zmienne bezpośrednio przygotowane przez przetwornik przed uśpieniem się obwodu (strumień `summary`).
1. **Zmienna: Ilość Punktów Pomiarowych:** Czwarty bajt układu Int32 z offsetu `12` strumienia (*32-bit Integer Unsigned*). Określa definitywną, całkowitą liczbę odczytów zebranych w sesji przez cykl kwarcu wyznaczając granicę Pętli głównej `T(n)`.
2. **Zmienna: Interwał Obwodu:**  Zapisany na offsecie `28` układ zdefiniowany w milisekundach (np. `10800000 ms`), pozwalający obiektywnie ustalić takt (krok pętli), uniezależniając skrypt od sztywnych wartości.


### 3.3 Obliczanie Liniowości Czasu i Temperatura 
Najważniejszy mechanizm odczytu wartości referencyjnych dla OQ/PQ/Mapowań prowadzony jest na strumieniu `data/values`.

- **Offset Startowy:** Pierwsze `4 bajty` danych pliku to sygnatura organizacyjna (Nagłówek sekcji) – pomijany w procesie. 
- **Bloki Kwarantanny (Skoki co 8 bajtów):** Algorytm rozpoczyna czytanie na wylistowanej w punkcie 3.2 ilości pętli po sekwencji (4 Bajty *Float32* Temperatura + 4 Bajty *Int32* Ticki Metadanych). 
- Ze względu na brak możliwości zaufania poprawności Ticków czasowych urządzenia produkującego zrzut od punktu pomiaru >= 2, System stosuje tzw. "Testo Epoch Time (Base 1961)" **wyłącznie** do najstarszego pomiaru, generującego `Czas Zerowy (T0)`.

**Algorytm generowania EPOKI (T0):**
1. System wykorzystuje Zegar Bazowy: `1961-07-09T01:30:00`.
2. Odkodowany Int32 Ticku T(0) procesowany jest w formacie: `Tick / 131072.0` (Stała Dnia Kwarcu).
3. Część całkowita ułamka traktowana jest jako wartość w formacie Dodaj Dni (`Days`).
4. Ułamek pomnożony dodatnio o bazę `86400` dodawany jest jako zmienna `Seconds`.

Następnie wyliczone T0 staje się trzonem obróbki `T(1..n)`. Obliczanie każdej kolejnej daty odczytu bazuje nie na ododaniu Ticków kwarcu, lecz precyzyjnym wymnożeniu Interwału z Kroku 3.2. 

### 3.4 Moduł Bias'u Strefowego (Został Odizolowany)
Badania i audyt inżynieryjny na pliku `srodek.vi2` na offsecie `16` w strumieniu `timezone` udowodnił zagnieżdżenie Windowsowego bias-u UTC. Badanie wykazało jednocześnie, że sygnatura tick'u sprzętowego (punkt 3.3) zawiera już ten stempel i operowanie w trybie Native-Local Time podczas importu skutkowało fałszywym narastaniem o +1 godzinę dla T(0).
W kodzie zablokowano funkcję dodawania bias'u sprzętowego (`startTime.minusMinutes(biasMinutes)`), zachowując jednak strumień dla dalszych celów ew. spójności w systemie między-strefowym.

## 4. Wytyczne Systemowe i Środowisko
Kod opracowany na użytek w frameworku Spring Boot. Kompilacja i parsowanie zachodzi wyłącznie w locie podczas wgrywania poświadczonego dokumentu binarnego i zapisywana jest przez model ORM do struktur powiązanych tabel (`MeasurementSeries` oraz `MeasurementPoint`). Cały odczyt wykonywany jest w ułamku sekund poprzez bufor pamięci roboczej bez włączania wektora I/O po sieci, wspierając zasady Integralności Danych.
