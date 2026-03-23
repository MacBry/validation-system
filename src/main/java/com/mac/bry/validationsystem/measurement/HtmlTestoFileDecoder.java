package com.mac.bry.validationsystem.measurement;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class HtmlTestoFileDecoder {

    private static final Logger log = LoggerFactory.getLogger(HtmlTestoFileDecoder.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    /**
     * Główna metoda dekodująca plik HTML.
     * UWAGA: Zabezpieczenie przed XSS jest nałożone za pomocą Jsoup.clean() z
     * Safelist.basicWithImages().
     */
    public MeasurementSeries decode(byte[] fileData, String originalFilename) throws IOException {
        String rawHtml = new String(fileData, StandardCharsets.UTF_8);

        // Zabezpieczenie przed XSS - używamy relaxed(), aby zachować tabele potrzebne
        // do parsowania
        String cleanHtml = Jsoup.clean(rawHtml, Safelist.relaxed());
        Document document = Jsoup.parse(cleanHtml);

        MeasurementSeries series = new MeasurementSeries();
        series.setOriginalFilename(originalFilename);
        series.setUploadDate(LocalDateTime.now());

        try {
            extractSerialNumber(document, series);
            extractMeasurements(document, series);
        } catch (Exception e) {
            log.error("Błąd podczas parsowania pliku HTML Testo: {}", e.getMessage(), e);
            throw new RuntimeException(
                    "Nie można odczytać formatu pliku HTML. Upewnij się, że jest to poprawny raport Testo.", e);
        }

        return series;
    }

    private void extractSerialNumber(Document doc, MeasurementSeries series) {
        Elements tables = doc.select("table");
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("Brak tabel w dokumencie HTML.");
        }

        // Zakładamy, że pierwsza tabela zawiera metadane (wg formatu
        // Raport_Tabela.html)
        Element metaTable = tables.first();
        Elements rows = metaTable.select("tr");

        if (rows.size() >= 6) {
            // Wiersz 6 (indeks 5), pierwsza komórka (indeks 0) zawiera numer seryjny, np.
            // "58980778\n"
            Element snCell = rows.get(5).select("td").first();
            if (snCell != null) {
                String snText = snCell.text().trim();
                if (!snText.isEmpty() && snText.matches("\\d+")) {
                    series.setRecorderSerialNumber(snText);
                    log.info("Poprawnie odczytano numer seryjny z raportu HTML: {}", snText);
                    return;
                }
            }
        }

        log.warn("Nie udało się odczytać numeru seryjnego z wnętrza pliku HTML. Będzie wymagane ręczne uzupełnienie.");
        // Fallback: próba z nazwy pliku
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{8,}").matcher(series.getOriginalFilename());
        String snFallback = m.find() ? m.group() : null;
        series.setRecorderSerialNumber(snFallback);
    }

    private void extractMeasurements(Document doc, MeasurementSeries series) {
        Elements tables = doc.select("table");
        if (tables.size() < 2) {
            throw new IllegalArgumentException("Brak drugiej tabeli (z pomiarami) w dokumencie HTML.");
        }

        Element dataTable = tables.get(1);
        Elements rows = dataTable.select("tr");

        List<MeasurementPoint> points = new ArrayList<>();

        // Iterujemy od 1, bo row 0 to nagłówki (ID | Data | Godzina | no name[°C])
        for (int i = 1; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements cells = row.select("td");

            // Format rzędu: [ID, Data (dd.MM.yyyy), Godzina (HH:mm:ss), Temperatura]
            if (cells.size() >= 4) {
                try {
                    String dateStr = cells.get(1).text().trim();
                    String timeStr = cells.get(2).text().trim();
                    String tempStr = cells.get(3).text().trim();

                    // Łączenie daty i czasu
                    String dateTimeStr = dateStr + " " + timeStr;
                    LocalDateTime measurementTime = LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);

                    // Naprawienie polskiego formatu liczb zmiennoprzecinkowych (przecinek ->
                    // kropka)
                    tempStr = tempStr.replace(",", ".");
                    double temperature = Double.parseDouble(tempStr);

                    MeasurementPoint point = new MeasurementPoint();
                    point.setMeasurementTime(measurementTime);
                    point.setTemperature(temperature);
                    point.setSeries(series); // Powiązanie dwukierunkowe

                    points.add(point);

                } catch (Exception e) {
                    log.error("Nie udało się sparsować rzędu danych #{}: {}", i, e.getMessage());
                    // Ignorujemy zepsute rzędy, np. puste wiersze na końcu
                }
            }
        }

        if (points.isEmpty()) {
            throw new IllegalArgumentException("Nie znaleziono żadnych punktów pomiarowych w tabeli.");
        }

        series.setMeasurementPoints(points);

        // Oznaczone jako do obliczenia w serwisie
        // Obliczanie interwału pomiarowego (różnica między pierwszym a drugim pomiarem)
        if (points.size() > 1) {
            LocalDateTime first = points.get(0).getMeasurementTime();
            LocalDateTime second = points.get(1).getMeasurementTime();
            long minutes = java.time.Duration.between(first, second).toMinutes();
            series.setMeasurementIntervalMinutes((int) minutes);
        } else {
            series.setMeasurementIntervalMinutes(0);
        }
    }
}
