import org.apache.poi.poifs.filesystem.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;

/**
 * Eksploruje WSZYSTKIE strumienie w pliku Vi2 - nie tylko data/values!
 */
public class Vi2StreamExplorer {

    public static void main(String[] args) throws Exception {
        System.out.println("=== KOMPLETNA ANALIZA STRUMIENI VI2 ===\n");

        exploreFile("10pomiarów.vi2", "POCZATEK SERII");
        System.out.println("\n" + "=".repeat(80) + "\n");
        exploreFile("srodek.vi2", "SRODEK SERII");
    }

    private static void exploreFile(String filename, String description) throws Exception {
        System.out.println("PLIK: " + filename + " (" + description + ")");

        File file = new File(filename);
        if (!file.exists()) {
            System.out.println("ERROR: Plik nie istnieje!");
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             POIFSFileSystem fs = new POIFSFileSystem(fis)) {

            DirectoryEntry root = fs.getRoot();
            System.out.println("STRUKTURA KATALOGÓW I STRUMIENI:\n");

            exploreDirectory(root, 0);

        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void exploreDirectory(DirectoryEntry dir, int level) throws Exception {
        String indent = "  ".repeat(level);

        for (org.apache.poi.poifs.filesystem.Entry entry : dir) {
            if (entry.isDirectoryEntry()) {
                DirectoryEntry subDir = (DirectoryEntry) entry;
                System.out.println(indent + "📁 " + entry.getName() + "/");
                exploreDirectory(subDir, level + 1);
            } else if (entry.isDocumentEntry()) {
                DocumentEntry doc = (DocumentEntry) entry;
                System.out.println(indent + "📄 " + entry.getName() + " (" + doc.getSize() + " bytes)");

                // Analizuj zawartość ważnych strumieni
                analyzeStream(doc, indent);
            }
        }
    }

    private static void analyzeStream(DocumentEntry doc, String indent) throws Exception {
        String name = doc.getName();

        try (DocumentInputStream dis = new DocumentInputStream(doc)) {
            byte[] data = new byte[dis.available()];
            dis.read(data);

            if (name.equals("summary")) {
                analyzeSummaryData(data, indent);
            } else if (name.equals("values")) {
                analyzeValuesData(data, indent);
            } else if (name.equals("t17b")) {
                analyzeT17bData(data, indent);
            } else if (name.equals("timezone")) {
                analyzeTimezoneData(data, indent);
            } else if (name.contains("temp") || name.contains("data") || name.contains("measure")) {
                System.out.println(indent + "  🌡️ Potencjalne dane temperaturowe!");
                analyzeGenericData(data, indent, name);
            } else if (data.length >= 8) {
                // Sprawdź czy strumień zawiera dane podobne do temperatur
                analyzeForTemperatureData(data, indent, name);
            }
        }
    }

    private static void analyzeSummaryData(byte[] data, String indent) {
        if (data.length >= 32) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            buffer.position(12);
            int count = buffer.getInt();

            buffer.position(28);
            int intervalMs = buffer.getInt();

            System.out.println(indent + "  📊 Count: " + count + ", Interval: " + intervalMs + "ms");
        }
    }

    private static void analyzeValuesData(byte[] data, String indent) {
        System.out.println(indent + "  🎯 VALUES STREAM DETAILS:");
        System.out.println(indent + "    Dlugosc: " + data.length + " bajtow");

        if (data.length >= 12) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // Skip header i pokaż pierwsze pomiary
            buffer.position(4);

            System.out.println(indent + "    Pierwszy pomiar:");
            if (buffer.remaining() >= 8) {
                float temp1 = buffer.getFloat();
                long meta1 = Integer.toUnsignedLong(buffer.getInt());
                System.out.println(indent + "      Temp: " + temp1 + "°C, Meta: " + meta1);
            }

            // Sprawdź czy są dane na końcu
            if (buffer.remaining() >= 8) {
                buffer.position(data.length - 8);
                float lastTemp = buffer.getFloat();
                long lastMeta = Integer.toUnsignedLong(buffer.getInt());
                System.out.println(indent + "    Ostatnie 8 bajtów:");
                System.out.println(indent + "      Temp: " + lastTemp + "°C, Meta: " + lastMeta);
            }

            // Policz ile pomiarów możemy wyciągnąć
            int availableBytes = data.length - 4; // minus header
            int possibleMeasurements = availableBytes / 8;
            System.out.println(indent + "    Możliwych pomiarów: " + possibleMeasurements);
        }
    }

    private static void analyzeT17bData(byte[] data, String indent) {
        System.out.println(indent + "  🏷️ T17B: szukanie numeru seryjnego...");

        for (int i = 13; i < Math.min(data.length - 8, 50); i++) {
            StringBuilder serial = new StringBuilder();

            for (int j = i; j < Math.min(data.length, i + 12); j++) {
                byte b = data[j];
                if (b >= '0' && b <= '9') {
                    serial.append((char) b);
                } else if (serial.length() > 0) {
                    break;
                }
            }

            if (serial.length() >= 6) {
                System.out.println(indent + "    Serial: " + serial.toString());
                break;
            }
        }
    }

    private static void analyzeTimezoneData(byte[] data, String indent) {
        if (data.length >= 20) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            buffer.position(16);
            int bias = buffer.getInt();
            System.out.println(indent + "  🌍 Timezone bias: " + bias + " minut");
        }
    }

    private static void analyzeGenericData(byte[] data, String indent, String name) {
        System.out.println(indent + "  📋 " + name + " - szczegółowa analiza:");

        // Sprawdź czy są float values
        if (data.length >= 4) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            System.out.println(indent + "    Pierwsze wartości jako float:");
            for (int i = 0; i < Math.min(data.length / 4, 10); i++) {
                if (buffer.remaining() >= 4) {
                    float val = buffer.getFloat();
                    System.out.printf(indent + "      [%d]: %.3f\n", i, val);
                }
            }
        }
    }

    private static void analyzeForTemperatureData(byte[] data, String indent, String name) {
        if (data.length >= 8 && data.length % 8 == 0) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            boolean looksLikeTemperatures = true;
            int tempCount = 0;

            while (buffer.remaining() >= 8 && tempCount < 5) {
                float temp = buffer.getFloat();
                long metadata = Integer.toUnsignedLong(buffer.getInt());

                // Sprawdź czy wygląda jak temperatura (-50 do +50°C)
                if (temp < -50 || temp > 50 || Float.isNaN(temp) || Float.isInfinite(temp)) {
                    looksLikeTemperatures = false;
                    break;
                }
                tempCount++;
            }

            if (looksLikeTemperatures && tempCount > 0) {
                System.out.println(indent + "  🌡️ POTENCJALNE TEMPERATURY W: " + name);
                buffer.position(0);

                while (buffer.remaining() >= 8) {
                    float temp = buffer.getFloat();
                    long metadata = Integer.toUnsignedLong(buffer.getInt());
                    System.out.printf(indent + "    %.3f°C (meta: %d)\n", temp, metadata);
                }
            }
        }
    }
}
