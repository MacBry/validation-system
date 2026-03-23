package com.mac.bry.validationsystem.measurement;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Dekoder plików .vi2 z rejestratora Testo
 * 
 * RZECZYWISTA STRUKTURA PLIKU .vi2:
 * 
 * Root Entry/
 * ├── 19788/ (folder z danymi urządzenia)
 * │ ├── data/
 * │ │ ├── values ← DANE POMIARÓW (temp + metadata)
 * │ │ ├── timezone (informacje o strefie czasowej)
 * │ │ └── schema (wersja schematu)
 * │ └── channels/
 * │ └── 1/ (kanał 1)
 * └── org (organizacja)
 * 
 * ALGORYTM DEKODOWANIA DAT:
 * 1. Odczyt "data/values" - pierwsze 4 bajty to wartość bazowa metadata
 * 2. Obliczenie daty bazowej: BASE_DATE + (base_meta / DAY_TICK) dni
 * 3. Dla każdego pomiaru: data = file_base + (meta - base_meta) * TICK_RATE
 * sekund
 * 
 * Stałe:
 * - BASE_DATE = 1961-07-09 01:30:00 (początek epoki metadata TESTO)
 * - DAY_TICK = 131072 (2^17) - liczba ticków na dzień
 * - TICK_RATE = 225/256 ≈ 0.878906 - sekundy na tick
 */
@Slf4j
@Component
public class Vi2FileDecoder {

    // === STAŁE KONWERSJI METADATA NA CZAS ===
    private static final LocalDateTime METADATA_BASE_DATE = LocalDateTime.of(1961, 7, 9, 1, 30, 0);
    private static final int DAY_TICK = 131072; // 2^17 ticków = 1 dzień
    private static final double TICK_RATE = 225.0 / 256.0; // Współczynnik: tick → sekunda

    // === STAŁE STRUKTURY PLIKU ===
    private static final int BYTES_PER_MEASUREMENT = 8; // 4B temperatura + 4B metadata
    private static final int MAX_MEASUREMENTS = 100; // Maksymalna liczba pomiarów (DEPRECATED - do usunięcia)

    /**
     * FAZA 1: Klasa przechowująca informacje z strumienia Summary
     */
    public static class Vi2SummaryInfo {
        private final Integer measurementCount;
        private final Integer intervalMilliseconds;

        public Vi2SummaryInfo(Integer measurementCount, Integer intervalMilliseconds) {
            this.measurementCount = measurementCount;
            this.intervalMilliseconds = intervalMilliseconds;
        }

        public Integer getMeasurementCount() {
            return measurementCount;
        }

        public Integer getIntervalMilliseconds() {
            return intervalMilliseconds;
        }

        @Override
        public String toString() {
            return String.format("Vi2SummaryInfo{count=%d, interval=%dms}",
                    measurementCount, intervalMilliseconds);
        }
    }

    /**
     * FAZA 1: Klasa przechowująca informacje z strumienia Timezone (opcjonalne)
     */
    public static class Vi2TimezoneInfo {
        private final Integer utcBiasMinutes;
        private final String standardName;
        private final String daylightName;

        public Vi2TimezoneInfo(Integer utcBiasMinutes, String standardName, String daylightName) {
            this.utcBiasMinutes = utcBiasMinutes;
            this.standardName = standardName;
            this.daylightName = daylightName;
        }

        public Integer getUtcBiasMinutes() {
            return utcBiasMinutes;
        }

        public String getStandardName() {
            return standardName;
        }

        public String getDaylightName() {
            return daylightName;
        }

        @Override
        public String toString() {
            return String.format("Vi2TimezoneInfo{bias=%d min, standard='%s', daylight='%s'}",
                    utcBiasMinutes, standardName, daylightName);
        }
    }

    /**
     * FAZA 1.1: Odczytuje strumień 'summary' zawierający rzeczywistą liczbę
     * pomiarów i interwał
     *
     * STRUKTURA (36 bajtów):
     * - Offset 12-15: Liczba pomiarów (Int32 Little-Endian)
     * - Offset 28-31: Interwał w milisekundach (Int32 Little-Endian)
     *
     * @param root główny folder OLE2
     * @return informacje z summary lub null jeśli nie znaleziono
     */
    private Vi2SummaryInfo findSummaryStream(DirectoryEntry root) {
        try {
            // Przeszukaj foldery urządzeń (np. "19788", "52762")
            for (org.apache.poi.poifs.filesystem.Entry entry : root) {
                if (entry.isDirectoryEntry()) {
                    DirectoryEntry deviceDir = (DirectoryEntry) entry;

                    // Szukaj dokumentu "summary"
                    if (deviceDir.hasEntry("summary")) {
                        DocumentEntry summaryDoc = (DocumentEntry) deviceDir.getEntry("summary");

                        try (DocumentInputStream dis = new DocumentInputStream(summaryDoc)) {
                            byte[] data = new byte[dis.available()];
                            dis.read(data);

                            if (data.length < 32) {
                                log.warn("Strumień summary za mały: {} bajtów (wymagane: ≥32)", data.length);
                                continue;
                            }

                            ByteBuffer buffer = ByteBuffer.wrap(data);
                            buffer.order(ByteOrder.LITTLE_ENDIAN);

                            // Offset 12-15: Liczba pomiarów
                            buffer.position(12);
                            int measurementCount = buffer.getInt();

                            // Offset 28-31: Interwał w milisekundach
                            buffer.position(28);
                            int intervalMs = buffer.getInt();

                            // Walidacja rozumności danych
                            if (measurementCount > 0 && measurementCount <= 10000 &&
                                    intervalMs >= 30000 && intervalMs <= 86400000) { // 30s - 24h

                                Vi2SummaryInfo info = new Vi2SummaryInfo(measurementCount, intervalMs);
                                log.info("✅ Znaleziono summary: {}", info);
                                return info;
                            } else {
                                log.warn("Nieprawidłowe dane summary: count={}, interval={}ms",
                                        measurementCount, intervalMs);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Błąd podczas odczytu summary stream: {}", e.getMessage());
        }

        log.warn("Nie znaleziono prawidłowego strumienia summary - fallback na stare wartości");
        return null;
    }

    /**
     * FAZA 1.2: Odczytuje strumień 't17b' zawierający numer seryjny rejestratora
     *
     * STRUKTURA:
     * - Offset 13+: Numer seryjny (ASCII string)
     *
     * @param root             główny folder OLE2
     * @param fallbackFilename nazwa pliku dla fallback
     * @return numer seryjny lub fallback z nazwy pliku
     */
    private String findT17bStream(DirectoryEntry root, String fallbackFilename) {
        try {
            // Przeszukaj foldery urządzeń
            for (org.apache.poi.poifs.filesystem.Entry entry : root) {
                if (entry.isDirectoryEntry()) {
                    DirectoryEntry deviceDir = (DirectoryEntry) entry;

                    // Szukaj dokumentu "t17b"
                    if (deviceDir.hasEntry("t17b")) {
                        DocumentEntry t17bDoc = (DocumentEntry) deviceDir.getEntry("t17b");

                        try (DocumentInputStream dis = new DocumentInputStream(t17bDoc)) {
                            byte[] data = new byte[dis.available()];
                            dis.read(data);

                            if (data.length < 20) {
                                log.warn("Strumień t17b za mały: {} bajtów", data.length);
                                continue;
                            }

                            // Offset 13+: Numer seryjny jako ASCII
                            String serialFromT17b = extractSerialFromT17bData(data);

                            if (serialFromT17b != null && serialFromT17b.matches("\\d{6,12}")) {
                                log.info("✅ Znaleziono numer seryjny z t17b: {}", serialFromT17b);
                                return serialFromT17b;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Błąd podczas odczytu t17b stream: {}", e.getMessage());
        }

        // Fallback na starą metodę z nazwy pliku
        String fallbackSerial = extractSerialNumber(fallbackFilename);
        log.warn("Nie znaleziono t17b - fallback na numer z nazwy: {}", fallbackSerial);
        return fallbackSerial;
    }

    /**
     * FAZA 1.2 Helper: Wyciąga numer seryjny z danych t17b
     */
    private String extractSerialFromT17bData(byte[] data) {
        try {
            // Offset 13+: szukamy ciągu cyfr ASCII
            for (int i = 13; i < Math.min(data.length - 8, 50); i++) {
                StringBuilder serial = new StringBuilder();

                // Czytaj cyfry ASCII dopóki są poprawne
                for (int j = i; j < Math.min(data.length, i + 12); j++) {
                    byte b = data[j];
                    if (b >= '0' && b <= '9') {
                        serial.append((char) b);
                    } else if (serial.length() > 0) {
                        break; // Koniec numeru seryjnego
                    }
                }

                if (serial.length() >= 6 && serial.length() <= 12) {
                    return serial.toString();
                }
            }
        } catch (Exception e) {
            log.debug("Błąd parsowania t17b: {}", e.getMessage());
        }
        return null;
    }

    /**
     * FAZA 1.3: Odczytuje strumień 'data/timezone' zawierający informacje o strefie
     * czasowej
     *
     * STRUKTURA (188 bajtów):
     * - Offset 16: UTC Bias w minutach (Int32)
     * - Offset 20+: Nazwa strefy standardowej (UTF-16LE)
     * - Offset ~100+: Nazwa strefy letniej (UTF-16LE)
     *
     * @param root główny folder OLE2
     * @return informacje o strefie czasowej lub null
     */
    private Vi2TimezoneInfo findTimezoneStream(DirectoryEntry root) {
        try {
            // Przeszukaj foldery urządzeń
            for (org.apache.poi.poifs.filesystem.Entry entry : root) {
                if (entry.isDirectoryEntry()) {
                    DirectoryEntry deviceDir = (DirectoryEntry) entry;

                    // Szukaj folderu "data"
                    if (deviceDir.hasEntry("data")) {
                        DirectoryEntry dataDir = (DirectoryEntry) deviceDir.getEntry("data");

                        // Szukaj dokumentu "timezone"
                        if (dataDir.hasEntry("timezone")) {
                            DocumentEntry timezoneDoc = (DocumentEntry) dataDir.getEntry("timezone");

                            try (DocumentInputStream dis = new DocumentInputStream(timezoneDoc)) {
                                byte[] data = new byte[dis.available()];
                                dis.read(data);

                                if (data.length < 100) {
                                    log.warn("Strumień timezone za mały: {} bajtów", data.length);
                                    continue;
                                }

                                ByteBuffer buffer = ByteBuffer.wrap(data);
                                buffer.order(ByteOrder.LITTLE_ENDIAN);

                                // Offset 16: UTC Bias w minutach
                                buffer.position(16);
                                int utcBiasMinutes = buffer.getInt();

                                // Offset 20+: Nazwa strefy standardowej (UTF-16LE)
                                String standardName = extractUtf16String(data, 20, 64);
                                String daylightName = extractUtf16String(data, 84, 64);

                                Vi2TimezoneInfo info = new Vi2TimezoneInfo(utcBiasMinutes, standardName, daylightName);
                                log.info("✅ Znaleziono timezone: {}", info);
                                return info;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Błąd podczas odczytu timezone stream: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Helper: Wyciąga string UTF-16LE z danych binarnych
     */
    private String extractUtf16String(byte[] data, int offset, int maxLength) {
        try {
            // Znajdź koniec stringa (null terminator w UTF-16LE to 00 00)
            int length = 0;
            for (int i = offset; i < Math.min(data.length - 1, offset + maxLength - 1); i += 2) {
                if (data[i] == 0 && data[i + 1] == 0) {
                    break;
                }
                length += 2;
            }

            if (length > 0) {
                byte[] stringBytes = new byte[length];
                System.arraycopy(data, offset, stringBytes, 0, length);
                return new String(stringBytes, StandardCharsets.UTF_16LE).trim();
            }
        } catch (Exception e) {
            log.debug("Błąd parsowania UTF-16 string: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Wyciąga numer seryjny z nazwy pliku .vi2 BEZ parsowania całego pliku.
     * Używane do walidacji czy rejestrator istnieje w bazie przed parsowaniem.
     *
     * @param filename nazwa pliku (np. "_58980778_2026_01_28_11_30_00.vi2")
     * @return numer seryjny lub null jeśli nie znaleziono
     */
    public String extractSerialNumberFromFilename(String filename) {
        return extractSerialNumber(filename);
    }

    /**
     * FAZA 2: Parsuje plik .vi2 używając nowych strumieni OLE2 (summary, t17b,
     * timezone)
     */
    public MeasurementSeries parseVi2File(byte[] fileData, String originalFilename) throws IOException {
        log.info("Rozpoczęcie parsowania pliku: {}", originalFilename);

        MeasurementSeries series = new MeasurementSeries();
        series.setOriginalFilename(originalFilename);
        series.setUploadDate(LocalDateTime.now());

        try (ByteArrayInputStream bis = new ByteArrayInputStream(fileData);
                POIFSFileSystem fs = new POIFSFileSystem(bis)) {

            DirectoryEntry root = fs.getRoot();

            // === FAZA 2.1: Odczyt informacji z nowych strumieni ===

            // Odczytaj rzeczywistą liczbę pomiarów i interwał z summary
            Vi2SummaryInfo summaryInfo = findSummaryStream(root);

            // Odczytaj numer seryjny z t17b (z fallback na nazwę pliku)
            String serialNumber = findT17bStream(root, originalFilename);
            series.setRecorderSerialNumber(serialNumber);

            // Odczytaj informacje o strefie czasowej (opcjonalne)
            Vi2TimezoneInfo timezoneInfo = findTimezoneStream(root);

            // Loguj co znaleziono
            if (summaryInfo != null) {
                log.info("✅ Summary: {} pomiarów co {} sekund",
                        summaryInfo.getMeasurementCount(),
                        summaryInfo.getIntervalMilliseconds() / 1000);
            } else {
                log.warn("⚠️ Brak summary - używam starych wartości (100 pomiarów, 3h interwał)");
            }

            if (timezoneInfo != null) {
                log.info("✅ Timezone: {} (bias: {} min)",
                        timezoneInfo.getStandardName(), timezoneInfo.getUtcBiasMinutes());
            }

            // === FAZA 2.2: Odczyt danych pomiarowych ===

            // Szukaj folderu "data/values"
            byte[] valuesData = findValuesStream(root);

            if (valuesData == null) {
                throw new IOException("Nie znaleziono strumienia 'data/values' w pliku .vi2");
            }

            log.info("Znaleziono strumień danych: {} bajtów", valuesData.length);

            // === FAZA 2.3: Parsowanie z dynamicznymi parametrami ===

            List<MeasurementPoint> points = parseTemperaturesWithDates(valuesData, summaryInfo, timezoneInfo);

            if (points.isEmpty()) {
                throw new IOException("Nie znaleziono żadnych pomiarów w pliku");
            }

            log.info("Pomyślnie sparsowano {} pomiarów", points.size());

            // Dodaj punkty do serii
            for (MeasurementPoint point : points) {
                series.addMeasurementPoint(point);
            }

            // Oznaczone jako do obliczenia w serwisie
            return series;

        } catch (IOException e) {
            log.error("Błąd podczas parsowania pliku .vi2: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Znajduje i zwraca zawartość strumienia "data/values"
     */
    private byte[] findValuesStream(DirectoryEntry root) throws IOException {
        // Szukaj folderu "19788" lub podobnego
        for (org.apache.poi.poifs.filesystem.Entry entry : root) {
            if (entry.isDirectoryEntry()) {
                DirectoryEntry dir = (DirectoryEntry) entry;

                // Szukaj folderu "data"
                try {
                    if (dir.hasEntry("data")) {
                        DirectoryEntry dataDir = (DirectoryEntry) dir.getEntry("data");

                        // Szukaj dokumentu "values"
                        if (dataDir.hasEntry("values")) {
                            DocumentEntry valuesDoc = (DocumentEntry) dataDir.getEntry("values");

                            try (DocumentInputStream dis = new DocumentInputStream(valuesDoc)) {
                                byte[] data = new byte[dis.available()];
                                dis.read(data);

                                log.info("✅ Znaleziono data/values: {} bajtów", data.length);
                                return data;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ten folder nie ma struktury data/values, szukaj dalej
                    log.debug("Folder {} nie zawiera data/values", dir.getName());
                }
            }
        }

        return null;
    }

    /**
     * Główna i NIEZAWODNA logika odczytu (V2 - zgodnie z MAPA_DEKODERA)
     */
    private List<MeasurementPoint> parseTemperaturesWithDates(byte[] data, Vi2SummaryInfo summaryInfo,
            Vi2TimezoneInfo timezoneInfo) {
        List<MeasurementPoint> points = new ArrayList<>();

        if (summaryInfo == null) {
            log.error("❌ BŁĄD KRYTYCZNY: Brak strumienia summary! Plik niemożliwy do ustalenia interwału.");
            throw new IllegalArgumentException(
                    "Plik VI2 jest uszkodzony lub nie posiada poprawnego strumienia summary dla Vi2FileDecoder V2.");
        }

        // Parametry z summary
        final int measurementsCount = summaryInfo.getMeasurementCount();
        final long intervalSeconds = summaryInfo.getIntervalMilliseconds() / 1000L;
        log.info("🔧 DYNAMICZNE: {} pomiarów co {} sekund", measurementsCount, intervalSeconds);

        final int HEADER_SIZE = 4;

        // Zabezpieczenie rozmiaru pliku (Header 4B + (N-1)*8B + 4B Ostatniej Temp) = 8
        // * N Byte
        int expectedMinSize = HEADER_SIZE + (measurementsCount - 1) * 8 + 4;
        if (data.length < expectedMinSize) {
            log.error("Strumień danych za mały: {} bajtów (wymagane minimum do deklarowanej ilości: {})", data.length,
                    expectedMinSize);
            throw new IllegalArgumentException(
                    "Deklarowana w summary liczba pomiarów przewyższa fizyczny rozmiar pliku OLE2.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // KROK 1: Czytamy Epokę (TICK) startową (zawsze jest w pierwszym bloku!)
        int firstMetaOffset = HEADER_SIZE + 4; // Header(4) + Temp0(4) = 8
        buffer.position(firstMetaOffset);
        long baseMetaValue = Integer.toUnsignedLong(buffer.getInt());

        // KROK 2: Przeliczanie na bazowy czas startu
        LocalDateTime startTime = calculateMeasurementDateFromMetadata(baseMetaValue);

        // KROK 3: Nakładanie strefy czasowej (LocalTime = UTC - Bias)
        if (timezoneInfo != null && timezoneInfo.getUtcBiasMinutes() != null) {
            // Ze względu na specyfikę sprzętową bias jest ODWROTNOŚCIĄ (-60 min tzn UTC+1)
            int biasMinutes = timezoneInfo.getUtcBiasMinutes();
            // UWAGA: Testy wykazały, że "Tick" zapisywany przez TESTO jest już
            // zsynchronizowany z czasem LOKALNYM użytkownika.
            // Dodawanie Biasu w tej funkcji sztucznie zawyża/zaniża o godzinę poprawny
            // czas, dlatego zostało wyłączone.
            // startTime = startTime.minusMinutes(biasMinutes); // np. - (-60) = +60 = UTC+1
            log.info(
                    "Zdekodowano Bias strefy czasowej rzędu: {} minut. Czas pozostawiony nieruszony (Tick jest Native-Local).",
                    biasMinutes);
        } else {
            log.warn("⚠️ Brak timezone w pliku.");
        }

        log.info("Poprawnie odkodowana data T0 (Start Sesji): {}", startTime);

        // KROK 4: Szybkie iteracyjne sczytywanie samych temperatur i matematyczne
        // budowanie osi czasu
        for (int i = 0; i < measurementsCount; i++) {
            int offsetTemp = HEADER_SIZE + (i * 8);
            buffer.position(offsetTemp);

            float temperature = buffer.getFloat();
            LocalDateTime measurementDate = startTime.plusSeconds(intervalSeconds * i);

            points.add(new MeasurementPoint(measurementDate, (double) temperature));

            if (i <= 3 || i >= measurementsCount - 3) {
                log.debug("Pomiar {}: {:.1f}°C w {}", i + 1, temperature, measurementDate);
            }
        }

        log.info("Sparsowano idealnie {} pomiarów temperatury na precyzyjnych wektorach offsetowych.", points.size());

        return points;
    }

    /**
     * Oblicza datę i czas pomiaru BEZPOŚREDNIO z wartości metadata
     * 
     * ALGORYTM (odkryty przez analizę plików TESTO):
     * - BASE_DATE = 1961-07-09 00:45:00
     * - DAY_TICK = 131072 (ticków na dzień)
     * - days = metadata / DAY_TICK
     * - date = BASE_DATE + days
     * 
     * @param metadata wartość metadata pomiaru
     * @return data i czas pomiaru
     */
    private LocalDateTime calculateMeasurementDateFromMetadata(long metadata) {
        // Liczba dni od METADATA_BASE_DATE
        double daysFromBase = (double) metadata / DAY_TICK;

        // Część całkowita = pełne dni
        long fullDays = (long) daysFromBase;

        // Część ułamkowa = czas w dniu (w sekundach)
        double fractionalDay = daysFromBase - fullDays;
        long secondsInDay = (long) (fractionalDay * 86400); // 86400 sekund = 1 dzień

        // Oblicz datę
        LocalDateTime result = METADATA_BASE_DATE
                .plusDays(fullDays)
                .plusSeconds(secondsInDay);

        return result;
    }

    /**
     * Wyciąga numer seryjny rejestratora z nazwy pliku.
     * Format nazwy: _58980778_2026_01_28_07_26_01.vi2 → "58980778"
     * Jeśli format nie pasuje, zwraca null.
     */
    private String extractSerialNumber(String filename) {
        if (filename == null)
            return null;
        // Usuń rozszerzenie i ew. ścieżkę
        String name = filename.replaceAll("(?i)\\.vi2$", "");
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0)
            name = name.substring(lastSlash + 1);
        // Format: _SERIAL_... lub SERIAL_...
        String[] parts = name.split("_");
        for (String part : parts) {
            if (part.matches("\\d{6,12}")) {
                return part;
            }
        }
        return null;
    }
}
