# 🚀 System Walidacji z Dekoderem Vi2

System do zarządzania walidacją urządzeń chłodniczych z obsługą plików .vi2 z rejestratorów TESTO.

## ✅ Cechy

- ✅ **Dekoder Vi2** - Parsowanie plików z rejestratorów TESTO
- ✅ **Zarządzanie urządzeniami** - Lodówki, zamrażarki, chłodnie
- ✅ **Serie pomiarowe** - Import i analiza danych temperaturowych
- ✅ **Statystyki** - Min, max, średnia, granice alarmowe
- ✅ **Interfejs WWW** - Bootstrap 5, responsywny design

## 📋 Wymagania

- **Java 17** lub nowsza
- **Maven 3.6+**
- **MySQL 8.0+**
- **IntelliJ IDEA** lub Eclipse (opcjonalnie)

## 🔧 Konfiguracja MySQL

### Opcja 1: Automatyczne utworzenie bazy (ZALECANE)

Aplikacja automatycznie utworzy bazę danych `validation_system` przy pierwszym uruchomieniu.

**Upewnij się że MySQL działa i hasło root to `admin`:**

```sql
-- Sprawdź czy możesz się zalogować:
mysql -u root -p
# Hasło: admin
```

Jeśli hasło jest inne, zmień w `src/main/resources/application.properties`:

```properties
spring.datasource.password=TWOJE_HASLO
```

### Opcja 2: Ręczne utworzenie bazy

```sql
mysql -u root -p

CREATE DATABASE validation_system;
USE validation_system;

-- Tabele zostaną utworzone automatycznie przez Hibernate
```

## 🚀 Uruchomienie

### Metoda 1: Maven (zalecane)

```bash
cd validation-system-fresh
mvn clean spring-boot:run
```

### Metoda 2: IntelliJ IDEA

1. Otwórz projekt w IntelliJ
2. Poczekaj aż Maven pobierze zależności
3. Znajdź klasę `ValidationSystemApplication`
4. Kliknij prawym → Run 'ValidationSystemApplication'

### Metoda 3: JAR

```bash
mvn clean package
java -jar target/validation-system-1.0.0.jar
```

## 🌐 Dostęp do aplikacji

Po uruchomieniu otwórz przeglądarkę:

```
http://localhost:8080
```

## 📁 Upload plików .vi2

1. Przejdź do: **Pomiary → Prześlij pliki .vi2**
2. Wybierz pliki z rejestratora TESTO
3. Kliknij "Prześlij"
4. System automatycznie:
   - Wydobędzie dane temperaturowe
   - Obliczy statystyki
   - Wygeneruje wykresy
   - Zapisze w bazie danych

## 📊 Format plików .vi2

Obsługiwane pliki z rejestratorów TESTO:
- Format: OLE2/CFB
- Temperatury: -50°C do +50°C
- Interwał: dowolny (domyślnie 3 godziny)
- Liczba pomiarów: do 10000 punktów

### Przykładowa nazwa pliku:
```
_58980778_2026_01_28_07_26_01.vi2
  └─┬──┘  └────────┬────────────┘
    │              └─ Data końca pomiarów
    └─ Numer seryjny rejestratora
```

## 🔍 Struktura projektu

```
validation-system-fresh/
├── src/main/java/com/mac/bry/validationsystem/
│   ├── ValidationSystemApplication.java    # Główna klasa
│   ├── measurement/
│   │   ├── Vi2FileDecoder.java            # ⭐ DEKODER VI2
│   │   ├── MeasurementSeries.java         # Encja serii
│   │   ├── MeasurementPoint.java          # Encja punktu
│   │   ├── MeasurementSeriesService.java  # Logika biznesowa
│   │   └── MeasurementSeriesController.java # Kontroler REST
│   ├── device/                             # Urządzenia chłodnicze
│   ├── laboratory/                         # Pracownie
│   └── calibration/                        # Świadectwa kalibracji
├── src/main/resources/
│   ├── application.properties              # Konfiguracja
│   ├── templates/                          # Widoki Thymeleaf
│   └── static/                             # CSS, JS, obrazy
└── pom.xml                                 # Zależności Maven
```

## 🛠️ Rozwiązywanie problemów

### Problem: `Access denied for user 'root'@'localhost'`

**Rozwiązanie:**
```sql
mysql -u root -p
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'admin';
FLUSH PRIVILEGES;
```

### Problem: `Table doesn't exist`

**Rozwiązanie:**
Usuń bazę i uruchom ponownie (Hibernate utworzy tabele):
```sql
DROP DATABASE validation_system;
CREATE DATABASE validation_system;
```

### Problem: Port 8080 zajęty

**Rozwiązanie:**
Zmień port w `application.properties`:
```properties
server.port=8081
```

### Problem: Błąd parsowania pliku .vi2

**Rozwiązanie:**
1. Sprawdź czy plik to rzeczywiście format .vi2 z TESTO
2. Zobacz logi w konsoli - dekoder podaje szczegóły błędu
3. Plik może być uszkodzony - spróbuj ponownie wyeksportować z rejestratora

## 📝 Logi

Aplikacja loguje szczegółowe informacje o parsowaniu plików:

```
2026-02-06 14:00:00 INFO  Vi2FileDecoder : Rozpoczęcie parsowania pliku: _58980778...
2026-02-06 14:00:00 DEBUG Vi2FileDecoder : Numer seryjny: 58980778
2026-02-06 14:00:00 DEBUG Vi2FileDecoder : Strumień OLE2 zawiera 0 wartości, używam surowego pliku
2026-02-06 14:00:00 DEBUG Vi2FileDecoder : Wykryto duży plik (5120 bajtów), szukanie początku danych
2026-02-06 14:00:00 DEBUG Vi2FileDecoder : Znaleziono początek danych na offset: 2180
2026-02-06 14:00:00 INFO  Vi2FileDecoder : Wyodrębniono 40 wartości temperatur
2026-02-06 14:00:00 INFO  Vi2FileDecoder : Zakres temperatur: 4.0°C - 6.1°C, średnia: 4.948°C
2026-02-06 14:00:00 INFO  Vi2FileDecoder : Utworzono 40 punktów pomiarowych z interwałem 10800 sekund (3.0 godzin)
2026-02-06 14:00:00 INFO  Vi2FileDecoder : Pomyślnie sparsowano plik - 40 pomiarów od 2026-01-22 12:00:00 do 2026-01-27 09:00:00
```

## 🎯 Zgodność z TESTO

Dekoder Vi2 został przetestowany z aplikacją TESTO i daje **identyczne wyniki**:

| Parametr | TESTO | Nasz System | Status |
|----------|-------|-------------|--------|
| Liczba pomiarów | 40 | 40 | ✅ |
| Min temperatura | 4.0°C | 4.0°C | ✅ |
| Max temperatura | 6.1°C | 6.1°C | ✅ |
| Średnia | 4.948°C | 4.948°C | ✅ |
| Interwał | 3h | 3h | ✅ |
| Okres | 22.01-27.01 | 22.01-27.01 | ✅ |

## 📞 Wsparcie

Jeśli masz problemy:
1. Sprawdź logi w konsoli
2. Sprawdź czy MySQL działa
3. Sprawdź czy hasło w `application.properties` jest poprawne
4. Sprawdź czy port 8080 jest wolny

## 📄 Licencja

Projekt wewnętrzny - wszelkie prawa zastrzeżone.

---

**Wersja:** 1.0.0 PRODUCTION READY  
**Data:** 2026-02-06  
**Status:** ✅ Gotowe do użycia
