package com.mac.bry.validationsystem.security.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Converter
@Component
public class AttributeEncryptionConverter implements AttributeConverter<String, String> {

    private static final String AES = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    private static String encryptionKey;

    @Value("${app.security.db-encryption-key}")
    public void setEncryptionKey(String key) {
        AttributeEncryptionConverter.encryptionKey = key;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || encryptionKey == null) {
            return attribute;
        }
        try {
            byte[] key = Base64.getDecoder().decode(encryptionKey);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), spec);

            byte[] cipherText = cipher.doFinal(attribute.getBytes());
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting attribute", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || encryptionKey == null) {
            return dbData;
        }
        try {
            byte[] key = Base64.getDecoder().decode(encryptionKey);
            byte[] combined = Base64.getDecoder().decode(dbData);

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);

            Cipher cipher = Cipher.getInstance(AES);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), spec);

            byte[] plainText = cipher.doFinal(combined, iv.length, combined.length - iv.length);
            return new String(plainText);
        } catch (Exception e) {
            // If decryption fails, it might be plain text (for migration period or wrong key)
            // But for a clean install, we should expect encrypted data.
            return dbData;
        }
    }
}
