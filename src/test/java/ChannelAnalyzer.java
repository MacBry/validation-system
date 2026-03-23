import org.apache.poi.poifs.filesystem.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;

/**
 * ANALIZA CHANNELS/1/ STREAM - szukamy brakujących temperatur!
 */
public class ChannelAnalyzer {

    private static final LocalDateTime METADATA_BASE_DATE = LocalDateTime.of(1961, 7, 9, 1, 30, 0);
    private static final int DAY_TICK = 131072;

    public static void main(String[] args) throws Exception {
        System.out.println("=== ANALIZA CHANNELS/1/ STREAM ===\n");

        analyzeChannelData("10pomiarów.vi2", "POCZATEK SERII - 10 pomiarow");
        System.out.println("\n" + "=".repeat(60) + "\n");
        analyzeChannelData("srodek.vi2", "SRODEK SERII - 6 pomiarow");
    }

    private static void analyzeChannelData(String filename, String description) throws Exception {
        System.out.println("PLIK: " + filename + " (" + description + ")");

        File file = new File(filename);
        if (!file.exists()) {
            System.out.println("ERROR: Plik nie istnieje!");
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             POIFSFileSystem fs = new POIFSFileSystem(fis)) {

            DirectoryEntry root = fs.getRoot();

            // Znajdź channels/1/ stream
            byte[] channelData = findChannelData(root);
            if (channelData == null) {
                System.out.println("ERROR: Brak channels/1/ stream!");
                return;
            }

            System.out.println("CHANNELS/1/ STREAM ZNALEZIONY: " + channelData.length + " bajtow\n");

            // Analizuj różne formaty
            analyzeAsTemperatures(channelData);
            analyzeAsRawData(channelData);
            analyzeWithDifferentOffsets(channelData);

        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static byte[] findChannelData(DirectoryEntry root) throws Exception {
        for (org.apache.poi.poifs.filesystem.Entry entry : root) {
            if (entry.isDirectoryEntry()) {
                DirectoryEntry deviceDir = (DirectoryEntry) entry;

                if (deviceDir.hasEntry("channels")) {
                    DirectoryEntry channelsDir = (DirectoryEntry) deviceDir.getEntry("channels");

                    if (channelsDir.hasEntry("1")) {
                        DirectoryEntry channel1Dir = (DirectoryEntry) channelsDir.getEntry("1");

                        // Znajdź pierwszy dokument w channel 1
                        for (org.apache.poi.poifs.filesystem.Entry channelEntry : channel1Dir) {
                            if (channelEntry.isDocumentEntry()) {
                                DocumentEntry doc = (DocumentEntry) channelEntry;

                                try (DocumentInputStream dis = new DocumentInputStream(doc)) {
                                    byte[] data = new byte[dis.available()];
                                    dis.read(data);
                                    return data;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void analyzeAsTemperatures(byte[] data) {
        System.out.println("🌡️ ANALIZA JAKO TEMPERATURY (standardowy format 8B):");

        if (data.length < 8) {
            System.out.println("  Za mało danych dla analizy temperaturowej");
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int pomiar = 1;
        boolean foundValidTemperatures = false;

        // Sprawdź bez headera
        buffer.position(0);

        while (buffer.remaining() >= 8 && pomiar <= 15) {
            float temp = buffer.getFloat();
            long metadata = Integer.toUnsignedLong(buffer.getInt());

            // Sprawdź czy wygląda jak temperatura
            if (temp >= -50 && temp <= 50 && !Float.isNaN(temp) && !Float.isInfinite(temp)) {
                if (!foundValidTemperatures) {
                    System.out.println("  🎯 ZNALEZIONE TEMPERATURY:");
                    foundValidTemperatures = true;
                }

                LocalDateTime date = calculateDateFromMetadata(metadata);
                System.out.printf("    Pomiar %2d: %8.3f°C | metadata=%10d | data=%s\n",
                    pomiar, temp, metadata, date);
            } else {
                if (foundValidTemperatures) {
                    System.out.println("  ⚠️ Koniec poprawnych temperatur na pomiarze " + pomiar);
                    break;
                }
            }

            pomiar++;
        }

        if (!foundValidTemperatures) {
            System.out.println("  ❌ Brak poprawnych temperatur w standardowym formacie");
        } else {
            System.out.println("  ✅ Znaleziono " + (pomiar - 1) + " poprawnych temperatur!");
        }
    }

    private static void analyzeAsRawData(byte[] data) {
        System.out.println("\n🔍 ANALIZA RAW DATA (hex dump):");

        for (int i = 0; i < Math.min(data.length, 80); i += 16) {
            System.out.printf("  %04X: ", i);

            // Hex values
            for (int j = i; j < Math.min(i + 16, data.length); j++) {
                System.out.printf("%02X ", data[j] & 0xFF);
            }

            // ASCII representation
            System.out.print(" | ");
            for (int j = i; j < Math.min(i + 16, data.length); j++) {
                char c = (char) (data[j] & 0xFF);
                System.out.print(c >= 32 && c <= 126 ? c : '.');
            }

            System.out.println();
        }

        if (data.length > 80) {
            System.out.println("  ... (pokazano pierwsze 80 bajtow z " + data.length + ")");
        }
    }

    private static void analyzeWithDifferentOffsets(byte[] data) {
        System.out.println("\n🔬 ANALIZA Z RÓŻNYMI OFFSETAMI:");

        // Sprawdź offsety 0, 4, 8, 12, 16
        for (int offset : new int[]{0, 4, 8, 12, 16}) {
            if (data.length - offset < 16) continue;

            System.out.println("  📍 OFFSET " + offset + ":");

            ByteBuffer buffer = ByteBuffer.wrap(data, offset, data.length - offset);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            int validCount = 0;
            for (int i = 0; i < 5 && buffer.remaining() >= 8; i++) {
                float temp = buffer.getFloat();
                long metadata = Integer.toUnsignedLong(buffer.getInt());

                if (temp >= -50 && temp <= 50 && !Float.isNaN(temp) && !Float.isInfinite(temp)) {
                    System.out.printf("    [%d] %.3f°C (meta: %d)\n", i + 1, temp, metadata);
                    validCount++;
                } else {
                    break;
                }
            }

            if (validCount > 0) {
                System.out.println("    ✅ " + validCount + " poprawnych temperatur!");
            }
        }
    }

    private static LocalDateTime calculateDateFromMetadata(long metadata) {
        double daysFromBase = (double) metadata / DAY_TICK;
        long fullDays = (long) daysFromBase;
        double fractionalDay = daysFromBase - fullDays;
        long secondsInDay = (long) (fractionalDay * 86400);

        return METADATA_BASE_DATE.plusDays(fullDays).plusSeconds(secondsInDay);
    }
}
