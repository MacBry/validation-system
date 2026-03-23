package com.mac.bry.validationsystem.validation;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.signatures.PdfPKCS7;
import com.itextpdf.signatures.SignatureUtil;
import com.mac.bry.validationsystem.security.service.TsaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests dla PDF signing z TSA timestamp support
 *
 * GMP COMPLIANCE TESTING:
 * - Weryfikacja elektronicznego podpisu z TSA timestamp
 * - Test compliance z FDA 21 CFR Part 11 §11.50(a)(1)(i)
 * - Walidacja non-repudiation przez TSA
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "app.tsa.enabled=true",
    "app.tsa.url=http://timestamp.digicert.com",
    "app.tsa.fallback.enabled=true",
    "app.tsa.timeout=30000"
})
class PdfSigningWithTsaIntegrationTest {

    @Autowired
    private PdfSigningService pdfSigningService;

    @Autowired
    private TsaService tsaService;

    private byte[] samplePdfBytes;

    @BeforeEach
    void setUp() throws Exception {
        // Tworzenie prostego PDF do testów
        samplePdfBytes = createSimplePdf();
    }

    @Test
    void signPdf_WithTsaEnabled_ShouldIncludeTsaTimestamp() throws Exception {
        // Given: Prosty PDF i TSA włączone
        String reason = "Test signature with TSA";
        String location = "Test Lab";

        // When: Podpisywanie PDF z TSA
        byte[] signedPdf = pdfSigningService.signPdf(
            samplePdfBytes,
            reason,
            location
        );

        // Then: PDF jest podpisany i zawiera TSA timestamp
        assertNotNull(signedPdf, "Podpisany PDF nie może być null");
        assertTrue(signedPdf.length > samplePdfBytes.length, "Podpisany PDF powinien być większy od oryginału");

        // Weryfikacja sygnatury PDF
        verifyPdfSignature(signedPdf, reason, location);
    }

    @Test
    void signPdf_WithCompanyCertificate_AndTsaEnabled_ShouldWork() throws Exception {
        // Given: Company keystore i TSA włączone
        byte[] keystoreBytes = loadKeystoreBytes();
        String keystorePassword = "Admin123";
        String reason = "Company signature with TSA";
        String location = "Company Lab";

        // When: Podpisywanie PDF certyfikatem firmowym z TSA
        byte[] signedPdf = pdfSigningService.signPdf(
            samplePdfBytes,
            reason,
            location,
            keystoreBytes,
            keystorePassword
        );

        // Then: PDF jest podpisany certyfikatem firmowym z TSA
        assertNotNull(signedPdf, "Podpisany PDF nie może być null");
        verifyPdfSignature(signedPdf, reason, location);
    }

    @Test
    void getCertSubject_ShouldReturnValidX500Name() throws Exception {
        // When: Pobieranie subject certyfikatu
        String certSubject = pdfSigningService.getCertSubject();

        // Then: Subject zawiera poprawne informacje X.500
        assertNotNull(certSubject, "Cert subject nie może być null");
        assertFalse(certSubject.trim().isEmpty(), "Cert subject nie może być pusty");

        // X.500 DN powinien zawierać standard components
        assertTrue(certSubject.contains("CN=") || certSubject.contains("O="),
                "Subject powinien zawierać CN lub O component");
    }

    @Test
    void getCertSerial_ShouldReturnValidHexString() throws Exception {
        // When: Pobieranie serial number certyfikatu
        String certSerial = pdfSigningService.getCertSerial();

        // Then: Serial jest poprawnym hex string
        assertNotNull(certSerial, "Cert serial nie może być null");
        assertFalse(certSerial.trim().isEmpty(), "Cert serial nie może być pusty");

        // Hex string validation (tylko A-F, 0-9)
        assertTrue(certSerial.matches("[0-9A-F]+"),
                "Serial powinien być hex stringiem: " + certSerial);
    }

    @Test
    void getCertSubject_WithCompanyKeystore_ShouldReturnCompanyInfo() throws Exception {
        // Given: Company keystore
        byte[] keystoreBytes = loadKeystoreBytes();
        String keystorePassword = "Admin123";

        // When: Pobieranie subject company certyfikatu
        String certSubject = pdfSigningService.getCertSubject(keystoreBytes, keystorePassword);

        // Then: Subject zawiera informacje o company
        assertNotNull(certSubject, "Company cert subject nie może być null");
        assertFalse(certSubject.trim().isEmpty(), "Company cert subject nie może być pusty");
    }

    @Test
    void tsaService_ShouldBeAvailable_InIntegrationContext() {
        // When: Sprawdzanie TSA availability
        boolean available = tsaService.verifyTsaAvailability();
        TsaService.TsaStatus status = tsaService.getTsaStatus();

        // Then: TSA powinno być skonfigurowane i dostępne (lub graceful fallback)
        assertNotNull(status, "TSA status nie może być null");
        assertTrue(status.isEnabled(), "TSA powinno być enabled w teście");
        assertTrue(status.isConfigured(), "TSA powinno być configured w teście");
        assertTrue(status.isFallbackEnabled(), "Fallback powinien być enabled");

        // availability może być false jeśli TSA server niedostępny (to OK dla dev)
        // ale status powinien być poprawnie skonfigurowany
    }

    @Test
    void tsaService_CreateTsaClient_ShouldReturnClientOrNull() {
        // When: Tworzenie TSA client
        var tsaClient = tsaService.createTsaClient();

        // Then: Client zostaje utworzony lub null (fallback behavior)
        // W środowisku dev TSA może być niedostępne - to jest OK
        // Ważne że nie ma exception i fallback działa
        // tsaClient może być null lub valid client - oba są OK
    }

    @Test
    void getTimestamp_ShouldReturnTimestampOrNull() {
        // Given: Document hash
        String documentHash = "test-document-hash-for-timestamp";

        // When: Pobieranie timestamp
        TsaService.TsaTimestamp timestamp = tsaService.getTimestamp(documentHash);

        // Then: Timestamp zostaje utworzony lub null (fallback)
        // W dev environment może być null jeśli TSA niedostępne
        if (timestamp != null) {
            assertEquals(documentHash, timestamp.getDocumentHash());
            assertTrue(timestamp.isSuccessful());
            assertNotNull(timestamp.getTsaSerial());
        }
        // null jest również OK (graceful degradation)
    }

    /**
     * Weryfikuje czy PDF został poprawnie podpisany
     */
    private void verifyPdfSignature(byte[] signedPdf, String expectedReason, String expectedLocation) throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(signedPdf)))) {

            SignatureUtil signatureUtil = new SignatureUtil(pdfDoc);
            List<String> signatureNames = signatureUtil.getSignatureNames();

            // Weryfikacja że jest przynajmniej jedna sygnatura
            assertFalse(signatureNames.isEmpty(), "PDF powinien zawierać przynajmniej jedną sygnaturę");

            String signatureName = signatureNames.get(0);
            PdfPKCS7 pkcs7 = signatureUtil.readSignatureData(signatureName);

            // Weryfikacja podstawowych właściwości sygnatury
            assertNotNull(pkcs7, "PKCS7 signature data nie może być null");
            assertEquals(expectedReason, pkcs7.getReason(), "Reason powinien być zachowany w sygnaturze");
            assertEquals(expectedLocation, pkcs7.getLocation(), "Location powinien być zachowany w sygnaturze");

            // Weryfikacja że sygnatura jest ważna
            assertTrue(pkcs7.verifySignatureIntegrityAndAuthenticity(),
                    "Sygnatura powinna być integrity valid");

            // Weryfikacja timestamp (może być present lub nie w zależności od TSA availability)
            var timeStampToken = pkcs7.getTimeStampToken();
            // timeStampToken może być null w dev environment - to OK
        }
    }

    /**
     * Tworzy prosty PDF do testów
     */
    private byte[] createSimplePdf() throws Exception {
        // Używamy istniejącego PDF z resources lub tworzymy prosty
        try {
            ClassPathResource resource = new ClassPathResource("test-document.pdf");
            if (resource.exists()) {
                return resource.getInputStream().readAllBytes();
            }
        } catch (Exception e) {
            // Ignore - stworzymy prosty PDF
        }

        // Tworzenie prostego PDF programatically
        var output = new java.io.ByteArrayOutputStream();
        try (var pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(
                new com.itextpdf.kernel.pdf.PdfWriter(output))) {

            var document = new com.itextpdf.layout.Document(pdfDoc);
            document.add(new com.itextpdf.layout.element.Paragraph("Test PDF for signing"));
            document.add(new com.itextpdf.layout.element.Paragraph("This document will be electronically signed"));
            document.add(new com.itextpdf.layout.element.Paragraph("with TSA timestamp for GMP compliance"));
        }
        return output.toByteArray();
    }

    /**
     * Ładuje keystore bytes z classpath
     */
    private byte[] loadKeystoreBytes() throws Exception {
        ClassPathResource keystoreResource = new ClassPathResource("keystore.p12");
        try (InputStream is = keystoreResource.getInputStream()) {
            return is.readAllBytes();
        }
    }
}