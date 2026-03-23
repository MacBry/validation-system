# Dokumentacja: ValidationSummaryStats — Statystyki Zbiorcze Walidacji
## Moduł: `com.mac.bry.validationsystem.stats`
## Data: 2026-03-03 | Standard: GDP (EU 2013/C 68/01), GMP Annex 11, WHO TRS 953

---

## Wstęp

`ValidationSummaryStats` gromadzi **globalne statystyki temperaturowe** wyliczone ze wszystkich serii
pomiarowych przypisanych do danej walidacji. Statystyki te stanowią rdzeń końcowego protokołu GMP/GDP.

Każda seria ma już obliczone statystyki jednostkowe (przez `calculateStatistics()` w
`MeasurementSeriesServiceImpl`). Zadaniem `ValidationSummaryStatsService` jest agregacja tych wartości
do jednej spójnej reprezentacji całej sesji mapowania / próby funkcjonalnej.

---

## TABELA A — Statystyki Temperatury (Globalne)

### A.1 `globalMinTemp` — Minimalna temperatura globalna [°C]

**Definicja:** Najniższa temperatura zmierzona w całym czasie trwania walidacji, we wszystkich
rejestratorach, we wszystkich seriach pomiarowych NIEBĘDĄCYCH rejestratorami referencyjnymi.

**Metoda obliczenia:**
```
globalMinTemp = min { s.minTemperature | s ∈ Series, s.isReferenceRecorder = false }
```

**Skąd dane:** `MeasurementSeries.minTemperature` (obliczone przez `calculateStatistics()`).

**Uzasadnienie GMP:** GDP §3 wymaga dokumentowania ekstremalnych wartości temperatury w urządzeniu
podczas mapowania. `globalMinTemp` wskazuje "zimny punkt" (coldspot) — miejsce, w którym temperatura
może spaść poniżej dolnej granicy przechowywania.

**Uwaga:** Rejestratory referencyjne (external reference) są WYKLUCZONE — mierzą warunki otoczenia,
nie warunki w komorze.

---

### A.2 `globalMaxTemp` — Maksymalna temperatura globalna [°C]

**Definicja:** Najwyższa temperatura zmierzona w całym czasie trwania walidacji, we wszystkich
rejestratorach siatki (nie referencyjnych).

**Metoda obliczenia:**
```
globalMaxTemp = max { s.maxTemperature | s ∈ Series, s.isReferenceRecorder = false }
```

**Skąd dane:** `MeasurementSeries.maxTemperature`.

**Uzasadnienie GMP:** GDP §3 — dokumentacja "hotspot" — miejsca najcieplejszego w komorze, krytycznego
dla oceny zgodności z górną granicą temperatury przechowywania.

---

### A.3 `overallAvgTemp` — Średnia temperatura ważona [°C]

**Definicja:** Średnia arytmetyczna wszystkich pomiarów ze wszystkich serii, ważona liczbą pomiarów
w każdej serii (nie prosta średnia z avg poszczególnych serii).

**Metoda obliczenia:**
```
overallAvgTemp = Σ(s.avgTemperature × s.measurementCount) / Σ(s.measurementCount)
                dla s ∈ Series (wszystkie — w tym referencyjne)
```

**Dlaczego ważona?** Prosta średnia z `avgTemperature` poszczególnych serii byłaby błędna, jeśli serie
mają różną długość. Seria 1000-punktowa powinna "ważyć" 10× więcej niż 100-punktowa.

**Skąd dane:** `MeasurementSeries.avgTemperature`, `MeasurementSeries.measurementCount`.

---

### A.4 `globalStdDev` — Globalne odchylenie standardowe [°C]

**Definicja:** Odchylenie standardowe populacyjne wszystkich pomiarów połączonych ze wszystkich serii.

**Metoda obliczenia (z danych zagregowanych — bez dostępu do surowych punktów):**

Korzystamy ze wzoru na wariancję połączonej próby ("pooled variance with between-group correction"):

```
Dla każdej serii s:
  n_s    = s.measurementCount
  μ_s    = s.avgTemperature
  σ²_s   = s.variance

N_total = Σ n_s
μ_total = overallAvgTemp (z A.3)

Wariancja połączona:
  σ²_global = (1/N_total) × Σ [ n_s × (σ²_s + (μ_s - μ_total)²) ]

Odchylenie standardowe:
  globalStdDev = √σ²_global
```

**Wzór matematyczny (tożsamość Steiner'a dla połączonych grup):**
```
σ²_pooled = [Σ(n_s × σ²_s) + Σ(n_s × (μ_s - μ_global)²)] / N_total
```

**Skąd dane:** `MeasurementSeries.variance`, `MeasurementSeries.measurementCount`,
`MeasurementSeries.avgTemperature`.

**Uzasadnienie:** Błędem byłoby obliczenie `mean(s.stdDeviation)` — odchylenie nie jest addytywne
i takie podejście zaniżałoby rozrzut uwzględniając tylko wariancję wewnątrz-serii, bez wariancji
między-serii.

---

### A.5 `globalCvPercentage` — Globalny współczynnik zmienności [%]

**Definicja:** Miara względnego rozrzutu temperatur wyrażona procentowo.

**Metoda obliczenia:**
```
globalCvPercentage = (globalStdDev / |overallAvgTemp|) × 100
```

**Warunek:** Jeśli `overallAvgTemp == 0`, zwracamy `0.0` (unikamy dzielenia przez zero).

**Interpretacja:** CV < 5% → bardzo jednorodny rozkład temperatur; CV > 15% → duże niejednorodności
wymagające uzasadnienia w protokole.

---

### A.6 `hotspotTemp` — Temperatura hotspot [°C]

**Definicja:** Maksymalna temperatura zmierzona przez rejestrator SIATKI (nie referencyjny),
wskazująca "najgorętszy punkt" w przestrzeni komory.

**Metoda obliczenia:**
```
hotspotTemp = max { s.maxTemperature | s ∈ Series, s.isReferenceRecorder = false }
hotspotSeriesId = id serii z tą wartością (do identyfikacji pozycji w siatce)
```

**Różnica od `globalMaxTemp`:** W bieżącej implementacji Tabeli A dla serii nicht-referencyjnych
są tożsame. Rozróżnienie stanie się istotne gdy w przyszłości dodamy filtrowanie per rejestrator,
a nie po `isReferenceRecorder`.

**Uzasadnienie GDP:** Wytyczne GDP wymagają identyfikacji hotspot i coldspot jako kluczowych punktów
mapowania — warunku sine qua non oceny zgodności przestrzennej komory.

---

### A.7 `coldspotTemp` — Temperatura coldspot [°C]

**Definicja:** Minimalna temperatura zmierzona przez rejestrator siatki.

**Metoda obliczenia:**
```
coldspotTemp = min { s.minTemperature | s ∈ Series, s.isReferenceRecorder = false }
coldspotSeriesId = id serii z tą wartością
```

---

### A.8 `globalExpandedUncertainty` — Niepewność rozszerzona globalna [°C]

**Definicja:** Największa niepewność rozszerzona spośród wszystkich serii (podejście konserwatywne —
"najgorszy przypadek").

**Metoda obliczenia:**
```
globalExpandedUncertainty = max { s.expandedUncertainty | s ∈ Series }
```

Gdzie w `calculateStatistics()`: `expandedUncertainty = 2.0 × stdDeviation` (k=2, poziom ufności ~95%).

**Uzasadnienie:** W protokole podajemy jednąliczbę niepewności dla całej walidacji. Zgodnie z zasadą
ostrożności (`prudent approach`) stosujemy wartość maksymalną, a nie średnią.

---

### A.9 `globalPercentile5` i `globalPercentile95` — Percentyle P5/P95 [°C]

**Definicja:** Wartości temperatury, poniżej (P5) lub powyżej (P95) których leży 5% lub 95%
wszystkich pomiarów.

**Metoda obliczenia (aproksymacja z danych zagregowanych):**
Nie możemy obliczyć dokładnych percentyli bez dostępu do surowych punktów. Stosujemy
**ważoną interpolację percentyli per seria**:

```
globalPercentile5  = min { s.percentile5  | s ∈ Series } (przybliżenie dolne)
globalPercentile95 = max { s.percentile95 | s ∈ Series } (przybliżenie górne)
```

**Uwaga metodyczna:** To jest przybliżenie konserwatywne — daje szerszy "ogon" rozkładu niż
dokładna kalkulacja. Dla potrzeb protokołu GMP jest wystarczające i bezpieczne.

---

## Metadane walidacji (Tabela E przedpremierowo)

| Pole | Obliczenie |
|------|-----------|
| `totalSeriesCount` | `count(Series)` |
| `gridSeriesCount` | `count(s | !s.isReferenceRecorder)` |
| `referenceSeriesCount` | `count(s | s.isReferenceRecorder)` |
| `totalMeasurementCount` | `Σ s.measurementCount` |
| `validationStartTime` | `min(s.firstMeasurementTime)` |
| `validationEndTime` | `max(s.lastMeasurementTime)` |
| `totalDurationMinutes` | `validationEndTime - validationStartTime` [min] |
| `dominantIntervalMinutes` | najczęstszy `s.measurementIntervalMinutes` (moda) |

---

## Reguły wydajnościowe implementacji

1. **Nie ładuj punktów pomiarowych** — wszystkie kalkulacje bazują na zagregowanych polach
   `MeasurementSeries`, nie na surowych `MeasurementPoint`. To gwarantuje wydajność nawet przy
   sesjach z 100 000+ pomiarów.

2. **Obliczaj raz przy tworzeniu walidacji** — `calculateAndSave(validationId)` jest wywołane
   jednorazowo przez `ValidationService`. Wynik zapisany w `ValidationSummaryStats`.

3. **Encja 1-to-1 z Validation** — `validation_summary_stats` jest osobną tabelą z FK do `validations`.
   Nie modyfikuje istniejącej tabeli `validations` (zero ryzyka migracyjnego dla istniejących rekordów).

---

## TABELA B  MKT (Mean Kinetic Temperature)

### Czym jest MKT?

**MKT** to jednotemperaturowy ekwiwalent rzeczywistego profilu temperatur, wywołujący taki sam
degradacyjny efekt termochemiczny na produkt jak rzeczywisty przebieg (WHO TRS 953, ICH Q1A).
MKT uwzględnia **nieliniową** zależność degradacji od temperatury (model Arrheniusa).

### B.1 `globalMkt`  Globalny MKT [C]

**Kluczowa własność:** MKT **nie jest addytywny**  `mean(s.mktTemperature)` daje błąd do 2C.

**Poprawny wzór  tożsamość Arrheniusa dla połączonych serii:**

``````
  A_k = n_k  e^(-ΔH/R / (MKT_k + 273.15))
  A_global = Σ_k A_k,  N_total = Σ_k n_k
  globalMkt = ΔH/R / (-ln(A_global / N_total)) - 273.15
``````

**Stałe (WHO TRS 953):** ΔH = 83 144.72 J/mol, R = 8.314472 J/(molK), ΔH/R  9998 K.

Wynik jest **matematycznie dokładny** bez dostępu do surowych punktów pomiarowych.

---

### B.2 `mktDeltaHR`  ΔH/R użyta w obliczeniu [K]

Zapisywana dla pełnej odtwarzalności obliczeń w audycie GMP (Annex 11 9).

---

### B.3 `mktWorstValue` / `mktWorstSeriesId`  Najwyższy MKT (worst case)

``````
mktWorstValue = max { s.mktTemperature | s  allSeries }
``````

Wymagany element analizy worst-case  seria najbardziej obciążająca termicznie produkt.

---

### B.4 `mktBestValue` / `mktBestSeriesId`  Najniższy MKT siatki (best case)

``````
mktBestValue = min { s.mktTemperature | s  gridSeries }
``````

Najkorzystniejsze miejsce przechowywania. Spread MKT = worst  best.

---

### B.5 `mktReferenceValue`  MKT rejestratora referencyjnego (otoczenie)

MKT serii z `isReferenceRecorder = true`  pomiar poza komorą (korytarz/otoczenie).

---

### B.6 `mktDeltaInternalVsReference`  Różnica MKT komora vs. otoczenie [C]

``````
mktDeltaInternalVsReference = globalMkt  mktReferenceValue
``````

| Wartość | Interpretacja |
|---------|--------------|
| Ujemna  | Komora chłodzi skutecznie |
| Bliska 0 | Komora nie utrzymuje temperatury |
| Dodatnia | Komora grzeje  problem krytyczny |


---

## TABELA C  Czas w Zakresie / Zgodność Temperaturowa (Compliance)

### Kontekst GMP

Tabela C dostarcza **ilościowych dowodów zgodności temperaturowej**  niezbędnych do oceny,
czy warunki przechowywania były utrzymane w każdym z rejestratorów przez cały czas walidacji.
Jest wymaganym elementem protokołu GDP (EU 2013/C 68/01 5) i GMP Annex 11.

---

### C.1 `totalTimeInRangeMinutes`  Łączny czas w zakresie [min]

```
totalTimeInRangeMinutes = Σ { s.totalTimeInRangeMinutes | s  allSeries }
```

**Uwaga metodyczna:** Jest to SUMA, nie czas kalendarzowy. Ponieważ serie rejestrują równolegle
(np. 27 rejestratorów przez 72 godziny), suma może wielokrotnie przekraczać czas sesji.
Wartość służy wyłącznie do obliczenia compliance% (C.3).

---

### C.2 `totalTimeOutOfRangeMinutes`  Łączny czas poza zakresem [min]

```
totalTimeOutOfRangeMinutes = Σ { s.totalTimeOutOfRangeMinutes | s  allSeries }
```

**Wymaganie GMP:** Każda minuta poza zakresem musi być opisana w sekcji "Analiza odchyleń"
protokołu. Wartość 0 oznacza pełną zgodność temperaturową.

---

### C.3 `globalCompliancePercentage`  Globalny wskaźnik zgodności [%]

```
compliance = totalTimeInRangeMinutes / (totalTimeInRangeMinutes + totalTimeOutOfRangeMinutes)  100
```

**Interpretacja:**

| Wartość | Ocena |
|---------|-------|
|  99%   | Doskonała zgodność |
|  95%   | Zgodna  akceptowalna według GDP |
|  85%   | Wymaga szczegółowej analizy odchyleń |
| < 85%   | Niezgodna  walidacja wymaga powtórzenia |

**Uwaga:** Progi akceptacji są zdefiniowane w protokole walidacyjnym (IQ/OQ/PQ acceptance criteria)
i mogą różnić się w zależności od klasy produktu i wymagań klienta.

---

### C.4 `totalViolations`  Łączna liczba przekroczeń

```
totalViolations = Σ { s.violationCount | s  allSeries }
```

Każde "przekroczenie" = jedno ciągłe wyjście temperature poza granice alarmowe.
Protokół GMP wymaga opisu każdego incydentu z analizą przyczyn i oceną wpływu na produkt.

---

### C.5 `maxViolationDurationMinutes` / `maxViolationSeriesId`  Najdłuższa eksursja termiczna

```
maxViolationDurationMinutes = max { s.maxViolationDurationMinutes | s  allSeries }
```

**Wymaganie GDP:** Eksursja trwająca > 15 min (lub inna wartość zdefiniowana w protokole)
wymaga obowiązkowej oceny wpływu na trwałość produktu przez osobę wykwalifikowaną.

**Klasyfikacja eksursji:**
- < 5 min: typowy artefakt przejściowy  zazwyczaj pomijany
- 515 min: dokumentacja wymagana
- 1560 min: ocena wpływu na produkt
- > 60 min: potencjalna dyskwalifikacja partii

---

### C.6 `seriesWithViolationsCount`  Liczba serii z przekroczeniami

```
seriesWithViolationsCount = count { s | s.violationCount > 0 }
```

Wskazuje **geograficzny zasięg** problemów termicznych  ile lokalizacji siatki pomiarowej
było dotkniętych przekroczeniami. Wysoka wartość (np. > 50% serii) sugeruje systemowy problem
z urządzeniem (awaria chłodzenia, nieszczelność), a nie lokalne zdarzenie.

---

### C.7 `seriesFullyCompliantCount`  Liczba serii w pełni zgodnych

```
seriesFullyCompliantCount = count { s | s.violationCount == 0 }
```

Uzupełnienie C.6. Suma C.6 + C.7 = totalSeriesCount.
Ideał: C.7 = totalSeriesCount (wszystkie serie bez przekroczeń).


---

## TABELA D - Stabilnosc Termiczna / Drift / Spike

### Kontekst

Tabela D opisuje **dynamiczne zachowanie temperatury** w czasie sesji mapowania.
Uzupelnia Tabele A-C (statystyki statyczne) o wymiar czasowy  czy temperatura w komorze
byla stabilna, czy wykazywala systematyczny dryftu lub anomalie impulsowe (spike'i).

Dane pochodza z per-seryjnej analizy trendu obliczonej przez `calculateStatistics()`
w `MeasurementSeriesServiceImpl` (regresja liniowa MKS + detekcja spike (3-sigma)).

---

### D.1 maxAbsTrendCoefficient / maxTrendSeriesId - Maks. |dryftu| [C/h]

```
maxAbsTrendCoefficient = max { |s.trendCoefficient| | s in allSeries, s.trendCoefficient != null }
```

Slope regresji liniowej y = a*t + b (MKS dla surowych pomiarow per seria).
Wartosc bezwzgledna - liczy sie wielkosc driftu, nie kierunek.

**Interpretacja:**

| Wartosc   | Ocena                                |
|-----------|--------------------------------------|
| < 0.1 C/h | Bardzo stabilny - brak driftu       |
| 0.1-0.5   | Akceptowalny                        |
| 0.5-1.0   | Wymaga analizy przyczyn             |
| > 1.0 C/h | Istotny dryftu - obowiazkowe uzasad. |

---

### D.2 avgTrendCoefficient - Sredniowazony trend [C/h]

```
avgTrendCoefficient = Sum(s.trendCoefficient * s.measurementCount) / N_total
```

Wartosc bliska 0 = brak globalnego trendu.
Wartosc dodatnia = komora sie nagrzewala. Ujemna = schladzala.

---

### D.3 totalSpikeCount - Laczna liczba spike'ow

```
totalSpikeCount = Sum { s.spikeCount }
```

Spike = |T_i - mean_s| > 3 * sigma_s (kryterium 3-sigma).
0 = brak zaklocen, > 5 = problem z czujnikami lub interferencja EMC.

---

### D.4 seriesWithDriftCount - Serie z driftem

```
seriesWithDriftCount = count { s | s.driftClassification IN ('DRIFT', 'MIXED') }
```

Zasieg geograficzny problemu driftu w siatce pomiarowej.

---

### D.5 seriesStableCount - Serie stabilne (STABLE)

```
seriesStableCount = count { s | s.driftClassification = 'STABLE' }
```

Ideal: seriesStableCount = totalSeriesCount. Suma D.4 + D.5 + spike-only = totalSeriesCount.

---

### D.6 dominantDriftClassification - Dominujaca klasyfikacja (moda)

```
dominantDriftClassification = argmax { count(s.driftClassification) }
```

Syntetyczna ocena jakosci termicznej walidacji:
- **STABLE** - w pelni stabilna, brak zaburzen
- **DRIFT** - systematyczny dryftu w wiekszosci serii
- **SPIKE** - anomalie impulsowe (zaklocenia pomiarowe)
- **MIXED** - kombinacja driftu i spike'ow
