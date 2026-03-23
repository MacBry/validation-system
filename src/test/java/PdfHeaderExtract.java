import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import java.io.IOException;

public class PdfHeaderExtract {
    public static void main(String[] args) throws IOException {
        String file1 = "C:\\Users\\macie\\Desktop\\Day zero\\Przykładowy pdf TESTO.pdf";
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(file1));

        System.out.println("=== TESTO PDF 1 EXTRACT ===");
        // Default extraction
        String text = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(1));
        System.out.println(text);

        // Location-based extraction (often catches headers)
        LocationTextExtractionStrategy strat = new LocationTextExtractionStrategy();
        String locText = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(1), strat);
        System.out.println("=== LOCATION EXTRACT ===");
        System.out.println(locText);

        pdfDoc.close();

        String file2 = "C:\\Users\\macie\\Desktop\\Day zero\\Przykładowy pdf TESTO 2.pdf";
        PdfDocument pdfDoc2 = new PdfDocument(new PdfReader(file2));

        System.out.println("=== TESTO PDF 2 EXTRACT ===");
        String locText2 = PdfTextExtractor.getTextFromPage(pdfDoc2.getPage(1), new LocationTextExtractionStrategy());
        System.out.println(locText2);

        pdfDoc2.close();
    }
}
