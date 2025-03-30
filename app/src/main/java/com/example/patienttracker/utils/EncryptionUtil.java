package com.example.patienttracker.utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class for encryption and decryption of sensitive data.
 * Uses AES/GCM/NoPadding algorithm for secure encryption.
 */
public class EncryptionUtil {
    private static final String TAG = "EncryptionUtil";
    private static final Logger LOGGER = Logger.getLogger(EncryptionUtil.class.getName());
    
    // AES-GCM parameters
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // In bits
    
    // Secret key (in a real application, this would be stored securely)
    private static SecretKey secretKey;
    
    static {
        try {
            // Generate a secret key or fetch it from secure storage
            secretKey = generateSecretKey();
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, "Error initializing encryption", e);
        }
    }
    
    /**
     * Generate a secret key for AES encryption
     */
    private static SecretKey generateSecretKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256); // 256-bit AES key
        return keyGenerator.generateKey();
    }
    
    /**
     * Encrypt sensitive data
     * @param plainText The text to encrypt
     * @return The encrypted text with IV prefixed, base64 encoded
     */
    public static String encryptData(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText; // Return as is if empty
        }
        
        try {
            // Generate a random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            
            // Create GCM parameter spec
            AlgorithmParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            
            // Initialize cipher for encryption
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            // Encrypt the data
            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and encrypted data
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedData.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedData);
            byte[] combinedData = byteBuffer.array();
            
            // Base64 encode for storage/transmission
            return Base64.getEncoder().encodeToString(combinedData);
            
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            LOGGER.log(Level.SEVERE, "Encryption error", e);
            return plainText; // Return plaintext in case of error to avoid data loss
        }
    }
    
    /**
     * Decrypt sensitive data
     * @param encryptedText The encrypted text with IV prefixed, base64 encoded
     * @return The decrypted text
     */
    public static String decryptData(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText; // Return as is if empty
        }
        
        try {
            // Base64 decode
            byte[] combinedData = Base64.getDecoder().decode(encryptedText);
            
            // Extract IV
            ByteBuffer byteBuffer = ByteBuffer.wrap(combinedData);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            
            // Extract encrypted data
            byte[] encryptedData = new byte[combinedData.length - GCM_IV_LENGTH];
            byteBuffer.get(encryptedData);
            
            // Create GCM parameter spec
            AlgorithmParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            
            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            // Decrypt the data
            byte[] decryptedData = cipher.doFinal(encryptedData);
            
            // Convert to string and return
            return new String(decryptedData, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Decryption error", e);
            return encryptedText; // Return encrypted text in case of error to avoid data loss
        }
    }
    
    /**
     * For testing: create a new secret key and return old one
     */
    public static SecretKey rotateSecretKey() throws NoSuchAlgorithmException {
        SecretKey oldKey = secretKey;
        secretKey = generateSecretKey();
        return oldKey;
    }
    
    /**
     * For testing: set a specific secret key
     */
    public static void setSecretKey(byte[] keyBytes) {
        secretKey = new SecretKeySpec(keyBytes, "AES");
    }
}