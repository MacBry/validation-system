package com.mac.bry.validationsystem.validation;

import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service do generowania raportu z walidacji - NOWA WERSJA
 * Radzi sobie z merged cells w Word
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationReportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public byte[] generateValidationReport(Validation validation, List<MeasurementSeries> seriesList)
            throws IOException {
        log.info("=== Generowanie raportu walidacji ===");
        log.info("Validation ID: {}", validation.getId());
        log.info("Liczba serii pomiarowych: {}", seriesList.size());

        for (int i = 0; i < seriesList.size(); i++) {
            MeasurementSeries s = seriesList.get(i);
            log.info("  Seria {}: ID={}, Rejestrator={}, Pozycja={}",
                    i, s.getId(), s.getRecorderSerialNumber(),
                    s.getRecorderPosition() != null ? s.getRecorderPosition().getDisplayName() : "BRAK");
        }

        ClassPathResource resource = new ClassPathResource("templates-docx/raport_walidacji_template.docx");

        try (InputStream templateStream = resource.getInputStream();
                XWPFDocument document = new XWPFDocument(templateStream);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XWPFTable table = document.getTables().get(0);
            log.info("Tabela ma {} wierszy", table.getRows().size());

            CoolingDevice device = validation.getCoolingDevice();

            // Wypełnij raport
            fillReport(table, validation, device, seriesList);

            document.write(out);
            log.info("Raport wygenerowany pomyślnie, rozmiar: {} bajtów", out.size());
            return out.toByteArray();
        }
    }

    private void fillReport(XWPFTable table, Validation validation, CoolingDevice device,
            List<MeasurementSeries> seriesList) {
        // TYTUŁ - format: "{numer RPW bez roku}/{skrót pracowni}/{rok}"
        // Przykład: "1/ORS/2026"
        String protocolNumber = buildProtocolNumber(validation, device);
        replaceText(table, 0, "Raport z walidacji procesu przechowywania nr",
                "Raport z walidacji procesu przechowywania nr " + protocolNumber);

        // PODSTAWOWE INFO
        String labAbbrev = device.getLaboratory() != null ? device.getLaboratory().getAbbreviation() : "–";
        appendToLastCell(table, 1, labAbbrev);
        appendToLastCell(table, 2, "Walidacja warunków przechowywania " + device.getMaterialName());
        appendToLastCell(table, 3, device.getMaterialName());

        // DATA WALIDACJI - zakres od pierwszego do ostatniego pomiaru
        String dateRange = getValidationDateRange(seriesList);
        appendToLastCell(table, 4, dateRange);

        appendToLastCell(table, 5, device.getName());
        appendToLastCell(table, 6, device.getInventoryNumber());

        // KRYTERIA - zakres temperatur
        String minTempStr = device.getMinOperatingTemp() != null ? String.format("%.1f°C", device.getMinOperatingTemp()) : "BRAK";
        String maxTempStr = device.getMaxOperatingTemp() != null ? String.format("%.1f°C", device.getMaxOperatingTemp()) : "BRAK";
        String tempRange = minTempStr + " do " + maxTempStr;
        appendToLastCell(table, 9, tempRange);

        // CHECKBOX - zaznacz odpowiedni wiersz według typu komory i materiału
        markCorrectCheckbox(table, device);

        // METODA
        String method = "Walidacja metodą pomiaru ciągłego temperatury przy użyciu rejestratorów TESTO. " +
                "Czujniki rozmieszczone w różnych punktach urządzenia.";
        appendToLastCell(table, 18, method);

        // WYNIKI - zaczynamy od wiersza 25
        fillResults(table, validation, seriesList, 25);

        log.info("========================================");
        log.info("ROZPOCZYNAM WYPEŁNIANIE DRUGIEJ STRONY");
        log.info("========================================");

        // ŚREDNIA TEMPERATURA URZĄDZENIA - wiersz 33
        // Struktura: [0]=LP, [1]=nagłówek, [2]=WARTOŚĆ
        Double avgTemp = validation.getAverageDeviceTemperature();
        log.info("Średnia temperatura z walidacji: {}", avgTemp);

        if (avgTemp != null) {
            log.info("PRÓBA wypełnienia wiersza 33, komórki [2] wartością: {}°C", avgTemp);
            try {
                fillCellDirect(table, 33, 2, String.format("%.1f°C", avgTemp));
                log.info("✅ SUKCES: Wypełniono średnią temperaturę urządzenia");
            } catch (Exception e) {
                log.error("❌ BŁĄD wypełniania średniej temperatury: ", e);
            }
        } else {
            log.warn("⚠️ Średnia temperatura jest NULL - nie wypełniam!");
        }

        // WNIOSKI I UWAGI - na podstawie średniej temperatury urządzenia i
        // poszczególnych serii
        log.info("Generowanie wniosków...");
        ConclusionsResult conclusionsResult = generateConclusions(validation, device, seriesList);
        boolean hasProblems = (conclusionsResult.remarks != null && !conclusionsResult.remarks.isEmpty());

        log.info("Wnioski długość: {} znaków",
                conclusionsResult.conclusions != null ? conclusionsResult.conclusions.length() : 0);
        log.info("Uwagi: {}", hasProblems ? "TAK (negatywny wynik)" : "NIE (pozytywny wynik)");

        // Wiersz 34 - Wnioski
        // Struktura: [0]="11.", [1]="Wnioski:", [2]=TREŚĆ
        log.info("PRÓBA wypełnienia wiersza 34, komórki [2] wnioskami");
        try {
            fillCellDirect(table, 34, 2, conclusionsResult.conclusions);
            log.info("✅ SUKCES: Wypełniono wnioski");
        } catch (Exception e) {
            log.error("❌ BŁĄD wypełniania wniosków: ", e);
        }

        // Wiersz 36 - Uwagi
        // Struktura: [0]="12.", [1]="Uwagi:", [2]=TREŚĆ
        if (hasProblems) {
            log.info("PRÓBA wypełnienia wiersza 36, komórki [2] uwagami");
            try {
                fillCellDirect(table, 36, 2, conclusionsResult.remarks);
                log.info("✅ SUKCES: Wypełniono uwagi");
            } catch (Exception e) {
                log.error("❌ BŁĄD wypełniania uwag: ", e);
            }
        } else {
            log.info("Brak uwag (wynik pozytywny) - pomijam wiersz 36");
        }

        // Wiersz 38 - Akceptacja (zaznacz odpowiednią komórkę)
        log.info("PRÓBA zaznaczenia akceptacji w wierszu 38");
        // Jeśli negatywny wynik → zaznacz ostatnią komórkę (NIE)
        // Jeśli pozytywny wynik → zaznacz komórkę TAK
        if (hasProblems) {
            // Negatywny - znajdź ostatnią unikalną komórkę
            try {
                XWPFTableRow row38 = table.getRow(38);
                List<XWPFTableCell> cells38 = row38.getTableCells();
                XWPFTableCell lastCell = findLastUniqueCell(cells38);
                if (lastCell != null) {
                    setTextDirect(lastCell, "[X]");
                    log.info("✅ SUKCES: Zaznaczono NIE w akceptacji (negatywny wynik)");
                } else {
                    log.error("❌ Nie znaleziono ostatniej komórki w wierszu 38!");
                }
            } catch (Exception e) {
                log.error("❌ BŁĄD zaznaczania NIE: ", e);
            }
        } else {
            // Pozytywny - zaznacz TAK (pierwsza dostępna komórka po nagłówku)
            try {
                // Znajdź komórkę TAK - może być [2] lub inna
                XWPFTableRow row38 = table.getRow(38);
                List<XWPFTableCell> cells38 = row38.getTableCells();
                log.info("Wiersz 38 ma {} komórek", cells38.size());

                // Spróbuj komórkę [2] (pierwsza po nagłówku)
                if (cells38.size() > 2) {
                    fillCellDirect(table, 38, 2, "[X]");
                } else {
                    log.warn("Za mało komórek w wierszu 38 dla TAK");
                }
                log.info("✅ SUKCES: Zaznaczono TAK w akceptacji (pozytywny wynik)");
            } catch (Exception e) {
                log.error("❌ BŁĄD zaznaczania TAK: ", e);
            }
        }

        // Wiersz 40 - Data następnej walidacji
        // Struktura: [0]="14.", [1]="Data...", [2]=TREŚĆ
        String nextValidationDate;
        if (hasProblems) {
            // Negatywny - NIEZWŁOCZNIE PO DZIAŁANIACH NAPRAWCZYCH
            nextValidationDate = "NIEZWŁOCZNIE PO PODJĘTYCH DZIAŁANIACH NAPRAWCZYCH";
        } else {
            // Pozytywny - rok od ostatniego pomiaru
            nextValidationDate = calculateNextValidationDate(seriesList);
        }

        log.info("PRÓBA wypełnienia wiersza 40, komórki [2] datą: {}", nextValidationDate);
        try {
            fillCellDirect(table, 40, 2, nextValidationDate);
            log.info("✅ SUKCES: Wypełniono datę następnej walidacji");
        } catch (Exception e) {
            log.error("❌ BŁĄD wypełniania daty: ", e);
        }

        log.info("========================================");
        log.info("ZAKOŃCZONO WYPEŁNIANIE DRUGIEJ STRONY");
        log.info("========================================");
    }

    /**
     * Znajduje ostatnią unikalną komórkę w wierszu (pomija merged)
     */
    private XWPFTableCell findLastUniqueCell(List<XWPFTableCell> cells) {
        XWPFTableCell lastUnique = null;

        for (int i = cells.size() - 1; i >= 0; i--) {
            if (i == 0 || cells.get(i).getCTTc() != cells.get(i - 1).getCTTc()) {
                lastUnique = cells.get(i);
                break;
            }
        }

        return lastUnique;
    }

    /**
     * Wylicza datę następnej walidacji (rok od ostatniego pomiaru)
     */
    private String calculateNextValidationDate(List<MeasurementSeries> seriesList) {
        if (seriesList.isEmpty()) {
            return "Za 12 miesięcy od daty walidacji";
        }

        // Znajdź najpóźniejszy pomiar
        var lastTime = seriesList.stream()
                .map(MeasurementSeries::getLastMeasurementTime)
                .filter(java.util.Objects::nonNull)
                .max(java.time.LocalDateTime::compareTo)
                .orElse(null);

        if (lastTime != null) {
            // Dodaj rok
            java.time.LocalDate nextDate = lastTime.toLocalDate().plusYears(1);
            return nextDate.format(DATE_FORMATTER);
        }

        return "Za 12 miesięcy od daty walidacji";
    }

    /**
     * Zaznacza odpowiedni checkbox w sekcji 7 (Kryteria akceptacji)
     * według typu komory i przechowywanego materiału
     */
    private void markCorrectCheckbox(XWPFTable table, CoolingDevice device) {
        String chamberType = device.getChamberType().name();
        String materialName = device.getMaterialName().toLowerCase();

        log.info("Określanie checkboxu: ChamberType={}, Material={}", chamberType, materialName);

        int checkboxRow = -1;

        // MAPOWANIE: typ komory + materiał → wiersz w tabeli

        // Wiersz 10: zamrażarka/mroźnia (ogólne, < -25°C)
        if ((chamberType.equals("FREEZER") || chamberType.equals("FREEZE_ROOM")) &&
                device.getMinOperatingTemp() != null && device.getMinOperatingTemp() < -20) {
            checkboxRow = 10;
        }

        // Wiersz 11: zamrażarka do przechowywania FFP do 3 miesięcy (< -18°C)
        else if (chamberType.equals("FREEZER") &&
                (materialName.contains("ffp") || materialName.contains("osocze"))) {
            checkboxRow = 11;
        }

        // Wiersz 12: zamrażarka do przechowywania odczynników/prób (< -20°C)
        else if (chamberType.equals("FREEZER") &&
                (materialName.contains("odczynnik") || materialName.contains("prób"))) {
            checkboxRow = 12;
        }

        // Wiersz 13: chłodnia/lodówka do przechowywania KKCz (2-6°C)
        else if ((chamberType.equals("FRIDGE") || chamberType.equals("COLD_ROOM")) &&
                (materialName.contains("kkcz") || materialName.contains("koncentrat"))) {
            checkboxRow = 13;
        }

        // Wiersz 14: inkubator do przechowywania KKP (20-24°C)
        else if (materialName.contains("kkp") || materialName.contains("płytki")) {
            checkboxRow = 14;
        }

        // Wiersz 15: lodówka do przechowywania odczynników/prób (2-8°C)
        else if (chamberType.equals("FRIDGE") &&
                (materialName.contains("odczynnik") || materialName.contains("prób"))) {
            checkboxRow = 15;
        }

        // Fallback - zaznacz według samego typu komory
        else {
            if (chamberType.contains("FREEZER") || chamberType.equals("FREEZE_ROOM")) {
                checkboxRow = 10; // Ogólna zamrażarka
            } else if (chamberType.equals("FRIDGE") || chamberType.equals("COLD_ROOM")) {
                checkboxRow = 13; // Ogólna chłodnia/lodówka
            }
        }

        if (checkboxRow > 0) {
            log.info("Zaznaczam checkbox w wierszu: {}", checkboxRow);
            prependToFirstCell(table, checkboxRow, "[X] ");
        } else {
            log.warn("Nie znaleziono odpowiedniego wiersza dla checkboxu!");
        }
    }

    private void replaceText(XWPFTable table, int rowIndex, String search, String replace) {
        try {
            XWPFTableRow row = table.getRow(rowIndex);
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph para : cell.getParagraphs()) {
                    if (para.getText().contains(search)) {
                        while (para.getRuns().size() > 0)
                            para.removeRun(0);
                        XWPFRun run = para.createRun();
                        run.setText(replace);
                        run.setFontSize(12);
                        run.setBold(true);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Replace failed row {}: {}", rowIndex, e.getMessage());
        }
    }

    private void appendToLastCell(XWPFTable table, int rowIndex, String text) {
        try {
            XWPFTableRow row = table.getRow(rowIndex);
            List<XWPFTableCell> cells = row.getTableCells();

            // Szukamy ostatniej niepustej komórki lub pustej po ':'
            XWPFTableCell target = null;
            for (int i = cells.size() - 1; i >= 0; i--) {
                String cellText = cells.get(i).getText().trim();
                if (cellText.isEmpty() || cellText.endsWith(":")) {
                    target = cells.get(i);
                    break;
                }
            }

            if (target == null)
                target = cells.get(cells.size() - 1);

            XWPFParagraph para = target.getParagraphs().isEmpty() ? target.addParagraph()
                    : target.getParagraphs().get(0);
            XWPFRun run = para.createRun();
            run.setText(text);
            run.setFontSize(10);
        } catch (Exception e) {
            log.warn("Append failed row {}: {}", rowIndex, e.getMessage());
        }
    }

    /**
     * Dodaje checkbox [X] do komórki checkboxowej (kolumna [1])
     */
    private void prependToFirstCell(XWPFTable table, int rowIndex, String text) {
        try {
            XWPFTableRow row = table.getRow(rowIndex);
            if (row == null || row.getTableCells().size() < 2) {
                log.warn("Wiersz {} nie ma wystarczająco komórek", rowIndex);
                return;
            }

            // CHECKBOX W KOMÓRCE [1] (pusta komórka między nagłówkiem [0] a opisem [2])
            XWPFTableCell checkboxCell = row.getTableCells().get(1);

            log.info("Zaznaczam checkbox w wierszu {}, komórka [1]", rowIndex);

            // Wyczyść komórkę
            while (checkboxCell.getParagraphs().size() > 0) {
                checkboxCell.removeParagraph(0);
            }

            // Dodaj [X]
            XWPFParagraph para = checkboxCell.addParagraph();
            para.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);

            XWPFRun run = para.createRun();
            run.setText(text);
            run.setFontSize(10);
            run.setBold(true);

            log.info("Checkbox wstawiony: '{}'", text);

        } catch (Exception e) {
            log.error("Błąd wstawiania checkboxu w wierszu {}: ", rowIndex, e);
        }
    }

    /**
     * Wypełnia konkretną komórkę w wierszu (bezpośredni dostęp po indeksie)
     */
    private void fillCellDirect(XWPFTable table, int rowIndex, int cellIndex, String text) {
        log.info("→ fillCellDirect: wiersz={}, komórka={}, tekst={}",
                rowIndex, cellIndex, text != null && text.length() > 50 ? text.substring(0, 50) + "..." : text);

        try {
            XWPFTableRow row = table.getRow(rowIndex);
            if (row == null) {
                log.error("  ❌ Wiersz {} NIE ISTNIEJE!", rowIndex);
                return;
            }

            List<XWPFTableCell> cells = row.getTableCells();
            log.info("  → Wiersz {} ma {} komórek", rowIndex, cells.size());

            if (cellIndex >= cells.size()) {
                log.error("  ❌ Komórka [{}] NIE ISTNIEJE! (max: {})", cellIndex, cells.size() - 1);
                return;
            }

            XWPFTableCell cell = cells.get(cellIndex);
            log.info("  → Znaleziono komórkę [{}]", cellIndex);

            setTextDirect(cell, text);

            log.info("  ✅ Wypełniono wiersz {}, komórka [{}]", rowIndex, cellIndex);

        } catch (Exception e) {
            log.error("  ❌ WYJĄTEK w fillCellDirect(row={}, cell={}): {}", rowIndex, cellIndex, e.getMessage(), e);
        }
    }

    /**
     * Zamienia tekst w komórce (już nie potrzebne dla checkboxów, ale zostawiam)
     */
    private void replaceTextInCell(XWPFTableCell cell, String oldText, String newText) {
        for (XWPFParagraph para : cell.getParagraphs()) {
            String paraText = para.getText();
            if (paraText.contains(oldText)) {
                // Usuń wszystkie runs
                while (para.getRuns().size() > 0) {
                    para.removeRun(0);
                }
                // Dodaj nowy tekst
                XWPFRun run = para.createRun();
                run.setText(newText);
                run.setFontSize(10);
                run.setBold(true);
                return;
            }
        }
    }

    private void fillResults(XWPFTable table, Validation validation, List<MeasurementSeries> seriesList, int startRow) {
        log.info("Wypełnianie wyników: {} serii, start row: {}", seriesList.size(), startRow);

        int currentRow = startRow;

        for (MeasurementSeries series : seriesList) {
            if (currentRow >= table.getRows().size()) {
                log.warn("Brak więcej wierszy! Row: {}, max: {}", currentRow, table.getRows().size());
                break;
            }

            try {
                XWPFTableRow row = table.getRow(currentRow);

                // Znajdź RZECZYWISTE (nie-merged) komórki
                List<XWPFTableCell> allCells = row.getTableCells();
                log.info("Wiersz {}: {} total cells", currentRow, allCells.size());

                // Budujemy listę UNIKALNYCH komórek (pomijamy merged)
                List<XWPFTableCell> uniqueCells = new java.util.ArrayList<>();
                XWPFTableCell lastCell = null;

                for (XWPFTableCell cell : allCells) {
                    if (lastCell == null || cell.getCTTc() != lastCell.getCTTc()) {
                        uniqueCells.add(cell);
                        lastCell = cell;
                    }
                }

                log.info("  Unikalne komórki: {}", uniqueCells.size());

                // MAPPING według rzeczywistych komórek (nie indeksów):
                // Unique 0 = LP
                // Unique 1 = Numer rejestratora
                // Unique 2 = Data wzorcowania
                // Unique 3 = Numer świadectwa
                // Unique 4 = Lokalizacja
                // Unique 5 = Min
                // Unique 6 = Max
                // Unique 7 = Średnia

                if (uniqueCells.size() >= 8) {
                    // LP (opcjonalnie)
                    // uniqueCells.get(0) - pomijamy lub wpisz numer

                    // Numer rejestratora
                    setTextDirect(uniqueCells.get(1), series.getRecorderSerialNumber());
                    log.info("  [1] Rejestrator: {}", series.getRecorderSerialNumber());

                    // Data wzorcowania i certyfikat
                    if (series.getThermoRecorder() != null &&
                            series.getThermoRecorder().getLatestCalibration() != null) {

                        String calDate = series.getThermoRecorder()
                                .getLatestCalibration()
                                .getCalibrationDate()
                                .format(DATE_FORMATTER);
                        setTextDirect(uniqueCells.get(2), calDate);
                        log.info("  [2] Data: {}", calDate);

                        String certNo = series.getThermoRecorder()
                                .getLatestCalibration()
                                .getCertificateNumber();
                        setTextDirect(uniqueCells.get(3), certNo);
                        log.info("  [3] Cert: {}", certNo);
                    }

                    // Lokalizacja
                    if (series.getRecorderPosition() != null) {
                        String pos = series.getRecorderPosition().getDisplayName();
                        setTextDirect(uniqueCells.get(4), pos);
                        log.info("  [4] Pozycja: {}", pos);
                    }

                    // Temperatury
                    String minTemp = String.format("%.1f", series.getMinTemperature());
                    String maxTemp = String.format("%.1f", series.getMaxTemperature());
                    String avgTemp = String.format("%.1f", series.getAvgTemperature());

                    setTextDirect(uniqueCells.get(5), minTemp);
                    setTextDirect(uniqueCells.get(6), maxTemp);
                    setTextDirect(uniqueCells.get(7), avgTemp);

                    log.info("  [5,6,7] Temp: {}, {}, {}", minTemp, maxTemp, avgTemp);

                    log.info("Wypełniono wiersz {} pomyślnie", currentRow);
                } else {
                    log.warn("Za mało unikalnych komórek: {}", uniqueCells.size());
                }

                currentRow++;

            } catch (Exception e) {
                log.error("BŁĄD wypełniania wiersza {}: ", currentRow, e);
                currentRow++;
            }
        }

        log.info("Zakończono wypełnianie {} wierszy", currentRow - startRow);

        // Średnia temperatura w urządzeniu
        findAndFillAverage(table, validation, currentRow);
    }

    /**
     * Szuka wiersza ze średnią i wypełnia z validation.averageDeviceTemperature
     */
    private void findAndFillAverage(XWPFTable table, Validation validation, int startSearchRow) {
        // Szukaj w zakresie do 10 wierszy po wypełnionych danych
        int searchEnd = Math.min(startSearchRow + 10, table.getRows().size());

        log.info("Szukam wiersza ze średnią w zakresie {}-{}", startSearchRow, searchEnd);

        for (int i = startSearchRow; i < searchEnd; i++) {
            XWPFTableRow tableRow = table.getRow(i);

            StringBuilder rowText = new StringBuilder();
            for (XWPFTableCell cell : tableRow.getTableCells()) {
                rowText.append(cell.getText()).append(" ");
            }
            String rowString = rowText.toString();

            if (rowString.contains("Średnia temperatura w urządzeniu")) {

                log.info("Znaleziono wiersz ze średnią: wiersz {}", i);

                // Użyj zapisanej średniej z walidacji
                Double avgTemp = validation.getAverageDeviceTemperature();

                if (avgTemp == null) {
                    log.warn("Brak średniej temperatury w walidacji! Pomijam wypełnianie.");
                    return;
                }

                // Wiersz 33 ma strukturę:
                // [0] = pusta
                // [1-8] = label "Średnia temperatura w urządzeniu:" (merged)
                // [9] = WARTOŚĆ ← TUTAJ!
                List<XWPFTableCell> cells = tableRow.getTableCells();

                if (cells.size() > 9) {
                    XWPFTableCell valueCell = cells.get(9);
                    setTextDirect(valueCell, String.format("%.1f°C", avgTemp));
                    log.info("Wypełniono średnią temperaturę urządzenia: {}°C w wierszu {} komórka [9]", avgTemp, i);
                } else {
                    log.warn("Za mało komórek w wierszu {}: {}", i, cells.size());
                }

                return; // Znaleziono i wypełniono, koniec
            }
        }

        log.warn("Nie znaleziono wiersza ze średnią temperaturą w zakresie {}-{}", startSearchRow, searchEnd);
    }

    /**
     * Ustawia tekst BEZPOŚREDNIO w komórce
     */
    private void setTextDirect(XWPFTableCell cell, String text) {
        if (cell == null) {
            log.warn("setTextDirect: cell is null!");
            return;
        }

        try {
            // NOWA STRATEGIA: NIE usuwaj paragrafów, użyj istniejącego lub dodaj nowy
            XWPFParagraph para;
            if (cell.getParagraphs().isEmpty()) {
                para = cell.addParagraph();
            } else {
                para = cell.getParagraphs().get(0);
                // Usuń tylko runy z pierwszego paragrafu
                while (para.getRuns().size() > 0) {
                    para.removeRun(0);
                }
            }

            para.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);

            // Dodaj run z tekstem
            XWPFRun run = para.createRun();
            run.setText(text != null ? text : "");
            run.setFontSize(10);
            run.setFontFamily("Calibri");

            log.debug("setTextDirect OK: '{}'", text);
        } catch (Exception e) {
            log.error("setTextDirect FAILED: ", e);
        }
    }

    private void setText(XWPFTableCell cell, String text) {
        XWPFParagraph para = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        while (para.getRuns().size() > 0)
            para.removeRun(0);
        XWPFRun run = para.createRun();
        run.setText(text != null ? text : "");
        run.setFontSize(10);
    }

    /**
     * Klasa pomocnicza dla wyników generowania wniosków
     */
    private static class ConclusionsResult {
        String conclusions;
        String remarks;

        ConclusionsResult(String conclusions, String remarks) {
            this.conclusions = conclusions;
            this.remarks = remarks;
        }
    }

    /**
     * Generuje wnioski walidacji na podstawie:
     * 1. Średniej temperatury urządzenia
     * 2. Średnich temperatur poszczególnych serii
     * 
     * Sprawdza każdą serię osobno - jeśli którakolwiek wykracza poza zakres,
     * generuje negatywny wniosek nawet jeśli średnia całego urządzenia jest OK.
     * 
     * Format pozytywny:
     * "Urządzenie [nr] pracuje poprawnie w operacyjnym zakresie temperatur ([min] -
     * [max]°C)
     * oraz w zakresie temperatur dla przechowywanego materiału: [nazwa].
     * Średnia temperatura urządzenia wynosi [wartość]°C."
     * 
     * Format negatywny (średnia urządzenia):
     * "UWAGA! Urządzenie [nr] NIE pracuje poprawnie. Wykryto odchylenie:
     * średnia temperatura ([wartość]°C) jest [NIŻSZA/WYŻSZA] niż [dolna/górna]
     * granica
     * zakresu operacyjnego ([granica]°C). Zakres operacyjny: [min] - [max]°C.
     * Materiał: [nazwa]."
     * 
     * Format negatywny (seria):
     * "UWAGA! Urządzenie [nr] NIE pracuje poprawnie. Wykryto odchylenie w serii
     * pomiarowej:
     * Rejestrator [nr], pozycja: [lokalizacja], średnia [wartość]°C - wykracza poza
     * zakres operacyjny."
     * 
     * Uwagi (dla negatywnych):
     * "WYMAGANA ANALIZA I DZIAŁANIA KORYGUJĄCE!"
     */
    private ConclusionsResult generateConclusions(Validation validation, CoolingDevice device,
            List<MeasurementSeries> seriesList) {

        Double avgTemp = validation.getAverageDeviceTemperature();

        if (avgTemp == null) {
            log.warn("Brak średniej temperatury - nie można wygenerować wniosków!");
            return new ConclusionsResult(
                    "Brak danych do wygenerowania wniosków (brak średniej temperatury urządzenia).",
                    null);
        }

        // Pobierz zakresy temperatur
        Double minOperating = device.getMinOperatingTemp();
        Double maxOperating = device.getMaxOperatingTemp();
        String materialName = device.getMaterialName();
        String inventoryNumber = device.getInventoryNumber();

        log.info("Generowanie wniosków:");
        log.info("  Średnia temperatura urządzenia: {}°C", avgTemp);
        log.info("  Zakres operacyjny: {} - {}°C", minOperating, maxOperating);
        log.info("  Materiał: {}", materialName);
        log.info("  Liczba serii: {}", seriesList.size());

        // KROK 1: Sprawdź średnią urządzenia
        boolean deviceInRange = true;
        if (minOperating != null && avgTemp < minOperating) deviceInRange = false;
        if (maxOperating != null && avgTemp > maxOperating) deviceInRange = false;

        // KROK 2: Sprawdź KAŻDĄ serię osobno
        List<MeasurementSeries> seriesOutOfRange = new java.util.ArrayList<>();

        for (MeasurementSeries series : seriesList) {
            Double seriesAvg = series.getAvgTemperature();
            if (seriesAvg != null) {
                boolean seriesInRange = true;
                if (minOperating != null && seriesAvg < minOperating) seriesInRange = false;
                if (maxOperating != null && seriesAvg > maxOperating) seriesInRange = false;

                log.info("  Seria {}: avg={}°C, w zakresie: {}",
                        series.getRecorderSerialNumber(), seriesAvg, seriesInRange);

                if (!seriesInRange) {
                    seriesOutOfRange.add(series);
                }
            }
        }

        // KROK 3: Generuj wniosek

        // Jeśli WSZYSTKO OK (średnia urządzenia OK + wszystkie serie OK)
        if (deviceInRange && seriesOutOfRange.isEmpty()) {
            String conclusion = String.format(
                    "Urządzenie %s pracuje poprawnie w operacyjnym zakresie temperatur " +
                            "(%s - %s) oraz w zakresie temperatur dla przechowywanego materiału: %s. " +
                            "Średnia temperatura urządzenia wynosi %.1f°C.",
                    inventoryNumber,
                    minOperating != null ? String.format("%.1f°C", minOperating) : "BRAK",
                    maxOperating != null ? String.format("%.1f°C", maxOperating) : "BRAK",
                    materialName,
                    avgTemp);

            log.info("Wygenerowano POZYTYWNY wniosek");
            return new ConclusionsResult(conclusion, null);
        }

        // Jeśli są problemy - zbuduj szczegółowy wniosek
        StringBuilder conclusion = new StringBuilder();
        conclusion.append(String.format("UWAGA! Urządzenie %s NIE pracuje poprawnie. ", inventoryNumber));

        // Problem ze średnią urządzenia?
        if (!deviceInRange) {
            String reason;
            if (minOperating != null && avgTemp < minOperating) {
                reason = String.format("średnia temperatura urządzenia (%.1f°C) jest NIŻSZA niż dolna granica " +
                        "zakresu operacyjnego (%.1f°C)",
                        avgTemp, minOperating);
            } else if (maxOperating != null && avgTemp > maxOperating) {
                reason = String.format("średnia temperatura urządzenia (%.1f°C) jest WYŻSZA niż górna granica " +
                        "zakresu operacyjnego (%.1f°C)",
                        avgTemp, maxOperating);
            } else {
                reason = "wykryto nieokreślone odchylenie (brak zdefiniowanych granic?)";
            }

            conclusion.append(String.format("Wykryto odchylenie: %s. ", reason));
            log.warn("Średnia urządzenia poza zakresem: {}", reason);
        }

        // Problemy z poszczególnymi seriami?
        if (!seriesOutOfRange.isEmpty()) {
            if (!deviceInRange) {
                conclusion.append("Dodatkowo ");
            } else {
                conclusion.append("Wykryto odchylenie ");
            }

            conclusion.append(String.format("w %d serii pomiarowej%s: ",
                    seriesOutOfRange.size(),
                    seriesOutOfRange.size() > 1 ? "ch" : ""));

            for (int i = 0; i < seriesOutOfRange.size(); i++) {
                MeasurementSeries series = seriesOutOfRange.get(i);
                String position = series.getRecorderPosition() != null ? series.getRecorderPosition().getDisplayName()
                        : "nieznana";

                conclusion.append(String.format("Rejestrator %s, pozycja: %s, średnia %.1f°C",
                        series.getRecorderSerialNumber(),
                        position,
                        series.getAvgTemperature()));

                if (i < seriesOutOfRange.size() - 1) {
                    conclusion.append("; ");
                } else {
                    conclusion.append(". ");
                }

                log.warn("Seria poza zakresem: {} ({}): {}°C",
                        series.getRecorderSerialNumber(), position, series.getAvgTemperature());
            }
        }

        conclusion.append(String.format("Zakres operacyjny: %s - %s. Materiał: %s.",
                minOperating != null ? String.format("%.1f°C", minOperating) : "BRAK",
                maxOperating != null ? String.format("%.1f°C", maxOperating) : "BRAK",
                materialName));

        // UWAGI - ostrzeżenie
        String remarks = "WYMAGANA ANALIZA I DZIAŁANIA KORYGUJĄCE!";

        log.warn("Wygenerowano NEGATYWNY wniosek");
        return new ConclusionsResult(conclusion.toString(), remarks);
    }

    /**
     * Buduje numer protokołu walidacji w formacie: {numer RPW bez roku}/{skrót
     * pracowni}/{rok}
     * 
     * Przykład: "1/ORS/2026"
     * 
     * @param validation - walidacja (zawiera validationPlanNumber w formacie
     *                   "1/2026")
     * @param device     - urządzenie (zawiera laboratory.abbreviation, np. "ORS")
     * @return sformatowany numer protokołu
     */
    private String buildProtocolNumber(Validation validation, CoolingDevice device) {
        String validationPlanNumber = validation.getValidationPlanNumber();

        if (validationPlanNumber == null || validationPlanNumber.isEmpty()) {
            log.warn("Brak numeru RPW w walidacji!");
            validationPlanNumber = "?/2026";
        }

        // Wyciągnij numer bez roku (np. "1/2026" -> "1")
        String[] parts = validationPlanNumber.split("/");
        String numberOnly = parts.length > 0 ? parts[0] : "?";

        // Rok z numeru RPW lub aktualny
        String year = parts.length > 1 ? parts[1] : String.valueOf(java.time.Year.now().getValue());

        // Skrót pracowni
        String labAbbreviation = device.getLaboratory() != null ? device.getLaboratory().getAbbreviation() : "???";

        // Format: {numer}/{skrót}/{rok}
        String protocolNumber = String.format("%s/%s/%s", numberOnly, labAbbreviation, year);

        log.info("Numer protokołu: {}", protocolNumber);

        return protocolNumber;
    }

    /**
     * Generuje zakres dat walidacji od pierwszego do ostatniego pomiaru
     */
    private String getValidationDateRange(List<MeasurementSeries> seriesList) {
        if (seriesList.isEmpty()) {
            return "Brak danych";
        }

        // Znajdź najwcześniejszy i najpóźniejszy pomiar
        var firstTime = seriesList.stream()
                .map(MeasurementSeries::getFirstMeasurementTime)
                .filter(java.util.Objects::nonNull)
                .min(java.time.LocalDateTime::compareTo)
                .orElse(null);

        var lastTime = seriesList.stream()
                .map(MeasurementSeries::getLastMeasurementTime)
                .filter(java.util.Objects::nonNull)
                .max(java.time.LocalDateTime::compareTo)
                .orElse(null);

        if (firstTime != null && lastTime != null) {
            return firstTime.format(DATE_FORMATTER) + " - " + lastTime.format(DATE_FORMATTER);
        } else if (firstTime != null) {
            return firstTime.format(DATE_FORMATTER);
        }

        return "Brak danych";
    }
}
