package com.mac.bry.validationsystem.validation;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.signatures.BouncyCastleDigest;
import com.itextpdf.signatures.DigestAlgorithms;
import com.itextpdf.signatures.ITSAClient;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.PrivateKeySignature;
import com.mac.bry.validationsystem.security.service.TsaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.util.Date;

/**
 * Serwis do kryptograficznego podpisywania dokumentów PDF certyfikatem
 * organizacji.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfSigningService {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final TsaService tsaService;

    @Value("${signing.keystore.path:classpath:keystore.p12}")
    private Resource keystorePath;

    @Value("${signing.keystore.password:changeit}")
    private String keystorePassword;

    @Value("${signing.key.alias:}")
    private String keyAlias;

    public byte[] signPdf(byte[] unsignedPdf, String reason, String location) throws Exception {
        return signPdf(unsignedPdf, reason, location, -1); // -1 means last page
    }

    /**
     * Podpisuje PDF globalnym certyfikatem na konkretnej stronie.
     */
    public byte[] signPdf(byte[] unsignedPdf, String reason, String location, int pageNumber) throws Exception {
        KeyStore ks = loadKeyStore();
        String alias = resolveAlias(ks);
        log.info("PDF podpisany certyfikatem globalnym (page {}), alias: {}", pageNumber, alias);
        return doSign(unsignedPdf, reason, location, ks, alias, keystorePassword, pageNumber);
    }

    /**
     * Podpisuje PDF certyfikatem per-firma.
     */
    public byte[] signPdf(byte[] unsignedPdf, String reason, String location,
            byte[] keystoreBytes, String ksPassword) throws Exception {
        return signPdf(unsignedPdf, reason, location, keystoreBytes, ksPassword, -1);
    }

    /**
     * Podpisuje PDF certyfikatem per-firma na konkretnej stronie.
     */
    public byte[] signPdf(byte[] unsignedPdf, String reason, String location,
            byte[] keystoreBytes, String ksPassword, int pageNumber) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(keystoreBytes), ksPassword.toCharArray());
        String alias = resolveAlias(ks);
        log.info("PDF podpisany certyfikatem firmowym (page {}), alias: {}", pageNumber, alias);
        return doSign(unsignedPdf, reason, location, ks, alias, ksPassword, pageNumber);
    }

    /**
     * Zwraca X.500 DN podmiotu globalnego certyfikatu organizacji.
     */
    public String getCertSubject() throws Exception {
        KeyStore ks = loadKeyStore();
        String alias = resolveAlias(ks);
        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
        return cert.getSubjectX500Principal().getName();
    }

    /**
     * Zwraca X.500 DN podmiotu certyfikatu z przekazanego keystore.
     */
    public String getCertSubject(byte[] keystoreBytes, String ksPassword) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(keystoreBytes), ksPassword.toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate(resolveAlias(ks));
        return cert.getSubjectX500Principal().getName();
    }

    /**
     * Zwraca numer seryjny globalnego certyfikatu jako ciąg szesnastkowy.
     */
    public String getCertSerial() throws Exception {
        KeyStore ks = loadKeyStore();
        String alias = resolveAlias(ks);
        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
        return cert.getSerialNumber().toString(16).toUpperCase();
    }

    /**
     * Zwraca numer seryjny certyfikatu z przekazanego keystore.
     */
    public String getCertSerial(byte[] keystoreBytes, String ksPassword) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(keystoreBytes), ksPassword.toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate(resolveAlias(ks));
        return cert.getSerialNumber().toString(16).toUpperCase();
    }

    private byte[] doSign(byte[] unsignedPdf, String reason, String location,
            KeyStore ks, String alias, String ksPassword, int pageNumber) throws Exception {
        PrivateKey pk = (PrivateKey) ks.getKey(alias, ksPassword.toCharArray());
        Certificate[] chain = ks.getCertificateChain(alias);

        // GMP COMPLIANCE: Validate certificate before signing
        if (chain != null && chain.length > 0 && chain[0] instanceof X509Certificate) {
            validateCertificate((X509Certificate) chain[0]);
        } else {
            throw new Exception("No valid X.509 certificate found in keystore for signing");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfReader reader = new PdfReader(new ByteArrayInputStream(unsignedPdf));
        PdfSigner signer = new PdfSigner(reader, out, new StampingProperties());

        int actualPage = pageNumber > 0 ? pageNumber : signer.getDocument().getNumberOfPages();
        log.debug("Placing digital signature on page {}", actualPage);

        signer.getSignatureAppearance()
                .setReason(reason)
                .setLocation(location)
                .setPageNumber(actualPage)
                .setPageRect(new Rectangle(360, 20, 200, 50)); // Bottom right
        signer.setCertificationLevel(PdfSigner.CERTIFIED_NO_CHANGES_ALLOWED);

        // GMP COMPLIANCE: Dodaj TSA timestamp dla FDA 21 CFR Part 11
        ITSAClient tsaClient = tsaService.createTsaClient();
        if (tsaClient != null) {
            log.info("✅ GMP TSA: Podpisywanie z trusted timestamp");
        } else {
            log.warn("⚠️ GMP TSA WARNING: Podpisywanie bez TSA timestamp");
        }

        signer.signDetached(
                new BouncyCastleDigest(),
                new PrivateKeySignature(pk, DigestAlgorithms.SHA256, null),
                chain, null, null, tsaClient, 0,
                PdfSigner.CryptoStandard.CMS);

        return out.toByteArray();
    }

    private KeyStore loadKeyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = keystorePath.getInputStream()) {
            ks.load(is, keystorePassword.toCharArray());
        }
        return ks;
    }

    private String resolveAlias(KeyStore ks) throws Exception {
        if (keyAlias != null && !keyAlias.isBlank()) {
            return keyAlias;
        }
        var aliases = ks.aliases();
        if (!aliases.hasMoreElements()) {
            throw new IllegalStateException("Keystore nie zawiera żadnych certyfikatów");
        }
        return aliases.nextElement();
    }

    /**
     * GMP COMPLIANCE: Validate certificate before signing
     * Checks expiration date and basic validity
     * TODO: Add CRL/OCSP revocation check for full production compliance
     */
    private void validateCertificate(X509Certificate certificate) throws Exception {
        try {
            // Check certificate validity dates
            Date now = new Date();
            certificate.checkValidity(now);

            log.debug("✅ Certificate validation passed for subject: {}",
                    certificate.getSubjectX500Principal().getName());

            // TODO for production: Add CRL/OCSP revocation checking
            // This would require:
            // 1. Parsing CRL Distribution Points from certificate
            // 2. Downloading and parsing CRL
            // 3. Checking certificate serial number against revoked list
            // 4. Or implementing OCSP checking
            //
            // Example implementation placeholder:
            // checkCertificateRevocation(certificate);

            log.info("✅ GMP Certificate validated: Serial={}, Subject={}, Valid until={}",
                    certificate.getSerialNumber().toString(16).toUpperCase(),
                    certificate.getSubjectX500Principal().getName(),
                    certificate.getNotAfter());

        } catch (CertificateExpiredException e) {
            String error = "Certificate has expired: " + certificate.getNotAfter();
            log.error("🚨 GMP CERT ERROR: {}", error);
            throw new Exception(error, e);
        } catch (CertificateNotYetValidException e) {
            String error = "Certificate is not yet valid: " + certificate.getNotBefore();
            log.error("🚨 GMP CERT ERROR: {}", error);
            throw new Exception(error, e);
        } catch (Exception e) {
            String error = "Certificate validation failed: " + e.getMessage();
            log.error("🚨 GMP CERT ERROR: {}", error);
            throw new Exception(error, e);
        }
    }

    // TODO: Implement for production CRL/OCSP checking
    // private void checkCertificateRevocation(X509Certificate certificate) throws
    // Exception {
    // // Implementation would check:
    // // 1. CRL Distribution Points extension
    // // 2. Download and verify CRL
    // // 3. Check if certificate serial is in revoked list
    // // 4. OCSP checking as fallback
    // }
}
