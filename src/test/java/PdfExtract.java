import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import java.io.IOException;

public class PdfExtract {
    public static void main(String[] args) throws IOException {
        String file1 = "C:\\Users\\macie\\Desktop\\Day zero\\Przykładowy pdf TESTO.pdf";
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(file1));
        System.out.println("--- PRZYKŁAD 1 (Page 1) ---");
        System.out.println(PdfTextExtractor.getTextFromPage(pdfDoc.getPage(1)));
        if (pdfDoc.getNumberOfPages() > 1) {
            System.out.println("--- PRZYKŁAD 1 (Page 2) ---");
            System.out.println(PdfTextExtractor.getTextFromPage(pdfDoc.getPage(2)));
        }
        pdfDoc.close();

        String file2 = "C:\\Users\\macie\\Desktop\\Day zero\\Przykładowy pdf TESTO 2.pdf";
        PdfDocument pdfDoc2 = new PdfDocument(new PdfReader(file2));
        System.out.println("--- PRZYKŁAD 2 (Page 1) ---");
        System.out.println(PdfTextExtractor.getTextFromPage(pdfDoc2.getPage(1)));
        if (pdfDoc2.getNumberOfPages() > 1) {
            System.out.println("--- PRZYKŁAD 2 (Page 2) ---");
            System.out.println(PdfTextExtractor.getTextFromPage(pdfDoc2.getPage(2)));
        }
        pdfDoc2.close();
    }
}
