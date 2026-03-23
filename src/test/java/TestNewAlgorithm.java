import com.mac.bry.validationsystem.measurement.Vi2FileDecoder;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.MeasurementPoint;
import org.apache.poi.poifs.filesystem.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.util.*;

/**
 * Test porównujący STARY vs NOWY algorytm dekodowania Vi2
 */
public class TestNewAlgorithm {

    private static final LocalDateTime METADATA_BASE_DATE = LocalDateTime.of(1961, 7, 9, 1, 30, 0);
    private static final int DAY_TICK = 131072;
    private static final int BYTES_PER_MEASUREMENT = 8;

    public static void main(String[] args) throws Exception {
        System.out.println("=== TEST NOWEGO ALGORYTMU VS STARY ===\n");

        testFile("10pomiarów.vi2", "POCZATEK SERII - 10 pomiarow");
        System.out.println("\n" + "=".repeat(80) + "\n");
        testFile("srodek.vi2", "SRODEK SERII - 6 pomiarow");
    }

    private static void testFile(String filename, String description) throws Exception {
        System.out.println("PLIK: " + filename + " (" + description + ")");

        File file = new File(filename);
        if (!file.exists()) {
            System.out.println("ERROR: Plik nie istnieje!");
            return;
        }

        // === TEST 1: STARY ALGORYTM ===
        Vi2FileDecoder oldDecoder = new Vi2FileDecoder();
        byte[] fileData = Files.readAllBytes(file.toPath());

        System.out.println("🔴 STARY ALGORYTM:");
        MeasurementSeries oldResult = oldDecoder.parseVi2File(fileData, filename);
        System.out.println("   Liczba pomiarów: " + oldResult.getMeasurementPoints().size());

        // === TEST 2: NOWY ALGORYTM ===
        System.out.println("\n🟢 NOWY ALGORYTM (POPRAWIONY):");
        List<MeasurementPoint> newResult = parseWithNewAlgorithm(fileData, filename);
        System.out.println("   Liczba pomiarów: " + newResult.size());

        // === PORÓWNANIE ===
        System.out.println("\n📊 PORÓWNANIE:");
        System.out.printf("   Stary: %d pomiarów\n", oldResult.getMeasurementPoints().size());
        System.out.printf("   Nowy:  %d pomiarów\n", newResult.size());

        if (newResult.size() > oldResult.getMeasurementPoints().size()) {
            System.out.println("   ✅ NOWY ALGORYTM ODCZYTAŁ WIĘCEJ DANYCH!");
        } else if (newResult.size() == oldResult.getMeasurementPoints().size()) {
            System.out.println("   ⚠️ Identyczna liczba pomiarów");
        } else {
            System.out.println("   ❌ Nowy algorytm odczytał mniej - błąd implementacji");
        }

        // Pokaż pierwsze i ostatnie pomiary z obu algorytmów
        System.out.println("\n🔍 SZCZEGÓŁY:");
        if (oldResult.getMeasurementPoints().size() > 0 && newResult.size() > 0) {
            var oldFirst = oldResult.getMeasurementPoints().get(0);
            var newFirst = newResult.get(0);

            System.out.println("   Pierwszy pomiar:");
            System.out.printf("     Stary: %.3f°C w %s\n", oldFirst.getTemperature(), oldFirst.getMeasurementTime());
            System.out.printf("     Nowy:  %.3f°C w %s\n", newFirst.getTemperature(), newFirst.getMeasurementTime());

            if (newResult.size() > oldResult.getMeasurementPoints().size()) {
                var newLast = newResult.get(newResult.size() - 1);
                System.out.println("   DODATKOWY pomiar (nowy algorytm):");
                System.out.printf("     %.3f°C w %s\n", newLast.getTemperature(), newLast.getMeasurementTime());
            }
        }
    }

    private static List<MeasurementPoint> parseWithNewAlgorithm(byte[] fileData, String filename) throws Exception {
        List<MeasurementPoint> points = new ArrayList<>();

        try (ByteArrayInputStream bis = new ByteArrayInputStream(fileData);
             POIFSFileSystem fs = new POIFSFileSystem(bis)) {

            DirectoryEntry root = fs.getRoot();

            // Odczytaj summary info
            SummaryInfo summary = readSummary(root);
            if (summary == null) {
                System.out.println("ERROR: Brak summary stream");
                return points;
            }

            System.out.printf("   Summary: %d pomiarów co %d ms\n",
                summary.count, summary.intervalMs);

            // Odczytaj values stream
            byte[] valuesData = findValuesStream(root);
            if (valuesData == null) {
                System.out.println("ERROR: Brak values stream");
                return points;
            }

            System.out.printf("   Values stream: %d bajtów\n", valuesData.length);

            // === NOWY ALGORYTM DEKODOWANIA ===
            return parseWithCorrectAlgorithm(valuesData, summary.count, summary.intervalMs / 1000L);

        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            return points;
        }
    }

    private static List<MeasurementPoint> parseWithCorrectAlgorithm(byte[] data, int expectedCount, long intervalSeconds) {
        List<MeasurementPoint> points = new ArrayList<>();

        if (data.length < 12) {
            System.out.println("   ERROR: Za mało danych");
            return points;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Skip header (4 bytes)
        buffer.position(4);

        System.out.println("   🎯 KROK 1: Pierwszy pomiar (T0)");

        // KROK 1: Pierwszy pomiar - czytaj temp + tick
        float firstTemp = buffer.getFloat();
        long firstTick = Integer.toUnsignedLong(buffer.getInt());

        LocalDateTime T0 = calculateDateFromMetadata(firstTick);

        System.out.printf("     T0: %.3f°C w %s (tick: %d)\n", firstTemp, T0, firstTick);

        // Dodaj pierwszy pomiar
        points.add(new MeasurementPoint(T0, (double) firstTemp));

        System.out.println("   🎯 KROK 2: Pozostałe pomiary (ignoruj tick)");

        // KROK 2: Pozostałe pomiary - TYLKO temperatura
        int pointsRead = 1;

        while (buffer.remaining() >= 8 && pointsRead < expectedCount) {
            float temp = buffer.getFloat();
            long ignoredTick = Integer.toUnsignedLong(buffer.getInt()); // IGNORUJ!

            // Wylicz datę ręcznie: T0 + (i * interwał)
            LocalDateTime date = T0.plusSeconds(intervalSeconds * pointsRead);

            points.add(new MeasurementPoint(date, (double) temp));
            pointsRead++;

            System.out.printf("     Pomiar %d: %.3f°C w %s (ignoredTick: %d)\n",
                pointsRead, temp, date, ignoredTick);
        }

        System.out.printf("   ✅ Sparsowano %d z %d oczekiwanych\n", pointsRead, expectedCount);

        return points;
    }

    // Helper classes i metody
    static class SummaryInfo {
        int count;
        int intervalMs;
        SummaryInfo(int count, int intervalMs) {
            this.count = count;
            this.intervalMs = intervalMs;
        }
    }

    private static SummaryInfo readSummary(DirectoryEntry root) throws Exception {
        for (org.apache.poi.poifs.filesystem.Entry entry : root) {
            if (entry.isDirectoryEntry()) {
                DirectoryEntry deviceDir = (DirectoryEntry) entry;
                if (deviceDir.hasEntry("summary")) {
                    DocumentEntry summaryDoc = (DocumentEntry) deviceDir.getEntry("summary");
                    try (DocumentInputStream dis = new DocumentInputStream(summaryDoc)) {
                        byte[] data = new byte[dis.available()];
                        dis.read(data);

                        if (data.length >= 32) {
                            ByteBuffer buffer = ByteBuffer.wrap(data);
                            buffer.order(ByteOrder.LITTLE_ENDIAN);

                            buffer.position(12);
                            int count = buffer.getInt();
                            buffer.position(28);
                            int intervalMs = buffer.getInt();

                            return new SummaryInfo(count, intervalMs);
                        }
                    }
                    break;
                }
            }
        }
        return null;
    }

    private static byte[] findValuesStream(DirectoryEntry root) throws Exception {
        for (org.apache.poi.poifs.filesystem.Entry entry : root) {
            if (entry.isDirectoryEntry()) {
                DirectoryEntry deviceDir = (DirectoryEntry) entry;
                if (deviceDir.hasEntry("data")) {
                    DirectoryEntry dataDir = (DirectoryEntry) deviceDir.getEntry("data");
                    if (dataDir.hasEntry("values")) {
                        DocumentEntry valuesDoc = (DocumentEntry) dataDir.getEntry("values");
                        try (DocumentInputStream dis = new DocumentInputStream(valuesDoc)) {
                            byte[] data = new byte[dis.available()];
                            dis.read(data);
                            return data;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static LocalDateTime calculateDateFromMetadata(long metadata) {
        double daysFromBase = (double) metadata / DAY_TICK;
        long fullDays = (long) daysFromBase;
        double fractionalDay = daysFromBase - fullDays;
        long secondsInDay = (long) (fractionalDay * 86400);

        return METADATA_BASE_DATE.plusDays(fullDays).plusSeconds(secondsInDay);
    }
}
