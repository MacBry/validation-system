import com.mac.bry.validationsystem.measurement.Vi2FileDecoder;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class TestVi2Analysis2 {

    public static void main(String[] args) throws IOException {
        Vi2FileDecoder decoder = new Vi2FileDecoder();

        System.out.println("=== ANALIZA PLIKOW WYCIETYCH PRZEZ TESTO ===\n");

        // Test pliku z początku serii
        testFile(decoder, "10pomiarów.vi2", "POCZATEK SERII (10 pomiarow)");

        System.out.println("\n============================================\n");

        // Test pliku ze środka serii
        testFile(decoder, "srodek.vi2", "SRODEK SERII");
    }

    private static void testFile(Vi2FileDecoder decoder, String filename, String description) {
        try {
            System.out.println("PLIK: " + filename + " (" + description + ")");

            File file = new File(filename);
            if (!file.exists()) {
                System.out.println("ERROR: Plik nie istnieje w: " + file.getAbsolutePath());
                return;
            }

            byte[] fileData = Files.readAllBytes(file.toPath());
            System.out.println("Rozmiar pliku: " + fileData.length + " bajtow");

            // Najpierw sprawdź numer seryjny z nazwy
            String serialFromFilename = decoder.extractSerialNumberFromFilename(filename);
            System.out.println("Serial z nazwy pliku: " + serialFromFilename);

            // Parsuj plik
            MeasurementSeries series = decoder.parseVi2File(fileData, filename);

            System.out.println("Wyniki parsowania:");
            System.out.println("  Serial z pliku: " + series.getRecorderSerialNumber());
            System.out.println("  Liczba pomiarów: " + series.getMeasurementPoints().size());
            System.out.println("  Upload date: " + series.getUploadDate());

            // Pokaż pierwsze i ostatnie pomiary
            if (series.getMeasurementPoints().size() > 0) {
                System.out.println("  Pierwszy pomiar: " + series.getMeasurementPoints().get(0));

                if (series.getMeasurementPoints().size() > 1) {
                    int lastIdx = series.getMeasurementPoints().size() - 1;
                    System.out.println("  Ostatni pomiar: " + series.getMeasurementPoints().get(lastIdx));
                }

                // Pokaż wszystkie pomiary dla plików testowych
                System.out.println("  Wszystkie pomiary:");
                for (int i = 0; i < series.getMeasurementPoints().size(); i++) {
                    var point = series.getMeasurementPoints().get(i);
                    System.out.printf("    %2d: %s | %.3f°C\n",
                        i+1, point.getMeasurementTime(), point.getTemperature());
                }
            }

        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
