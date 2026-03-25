# 🚀 Validation System V2 (Enterprise Edition)

[![Java Version](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Internal-blue.svg)](#)

Zaawansowany system klasy Enterprise przeznaczony dla sektora farmaceutycznego i laboratoryjnego, służący do zarządzania, analizy i walidacji urządzeń chłodniczych (lodówki, zamrażarki, komory klimatyczne). System zapewnia pełną zgodność z normami **GMP Annex 11** oraz **FDA 21 CFR Part 11**.

---

## ✨ Kluczowe Funkcje

### 📂 Obsługa Danych Pomiarowych
System wspiera automatyczny import i dekodowanie danych z rejestratorów TESTO:
- **Dekoder .vi2**: Autorska implementacja parsująca binarne strumienie OLE2/CFB z 100% precyzją.
- **Dekoder HTML**: Obsługa raportów generowanych bezpośrednio przez oprogramowanie Testo w formacie HTML (tabela pomiarów).

### 📊 Zaawansowana Analityka i Statystyki
Automatyczne wyliczanie kluczowych parametrów walidacyjnych dla serii pomiarowych oraz statystyk zbiorczych (Global):
- **Statystyki Podstawowe**: Minimum, Maksimum, Średnia, Odchylenie Standardowe (StdDev).
- **MKT (Mean Kinetic Temperature)**: Nieliniowy ekwiwalent profilu temperatur (wg WHO TRS 953).
- **Compliance %**: Czas przebywania w zdefiniowanym zakresie temperatur.
- **Analiza Trendów**: Wykrywanie dryftu (Drift) oraz anomalii impulsowych (Spikes - kryterium 3-sigma).
- **Punkty Krytyczne**: Automatyczna identyfikacja **Hotspot** (najcieplejszy punkt) i **Coldspot** (najzimniejszy punkt).

### 🎬 Wizualizacja i Animacja Przestrzenna
Nowoczesne podejście do prezentacji danych:
- **Wykresy Serii**: Interaktywne wykresy czasowe dla każdego rejestratora (Highcharts).
- **Animacja Przestrzenna 3D**: Dynamiczna wizualizacja zmian temperatury w czasie rzeczywistym wewnątrz komory urządzenia. Mapowanie pozycji czujników w układzie współrzędnych X, Y, Z pozwala na animowaną analizę rozkładu mas powietrza.

### 🛡️ Zgodność GMP i Workflow Walidacyjny
- **Wizard OQ/PQ**: Kompletny, wieloetapowy kreator kwalifikacji operacyjnej i procesowej.
- **Audit Trail**: Pełna historia zmian (Envers) oraz podpisy elektroniczne (TSA).
- **Raporty PDF**: Automatycznie generowane protokoły walidacyjne z wykresami i tabelami statystycznymi.

---

## 🛠️ Stack Technologiczny

- **Backend**: Java 17, Spring Boot 3.x, Spring Security, Hibernate.
- **Data Engine**: Autorskie dekodery binarne, Integracja z Python (dla animacji 3D).
- **Database**: MySQL 8.0, Redis.
- **Frontend**: Thymeleaf, Bootstrap 5, Highcharts.
- **Infrastructure**: Docker & Docker Compose.

---

## 🚀 Szybki Start

### Wymagania
- JDK 17+
- Maven 3.6+
- Docker & Docker Compose
- Środowisko Python (dla modułu wizualizacji 3D)

### Konfiguracja
1. Skopiuj `.env.example` do `.env` i uzupełnij hasła.
2. Uruchom infrastrukturę: `docker-compose up -d`.
3. Zbuduj i uruchom: `mvn clean spring-boot:run`.

Aplikacja będzie dostępna pod adresem: `https://localhost:8443`.

---

## 📂 Dokumentacja Techniczna

Szczegółowe opisy algorytmów i specyfikacje znajdują się w folderze `docs/`:
- [Metodologia Statystyk](docs/VALIDATION_SUMMARY_STATS_METODOLOGIA.md)
- [Specyfikacja Dekodera .vi2](docs/DOKLADNY_DEKODER_VI2_V2.md)
- [Algorytmy Dryftu i Spike](docs/ALGORYTM_STABILNOSC_DRIFT_VS_SPIKE.md)

---

**Wersja**: 2.12.0-ENTERPRISE  
**Status**: ✅ Gotowy do wdrożenia produkcyjnego
