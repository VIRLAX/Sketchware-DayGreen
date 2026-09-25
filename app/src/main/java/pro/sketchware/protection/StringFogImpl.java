package pro.sketchware.protection;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Standard StringFog implementation for AES-256 GCM authenticated encryption.
 * Injected automatically into target projects when StringFog AES-256 is active.
 */
public final class StringFogImpl {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_LENGTH = 12; // bytes

    private StringFogImpl() {}

    /**
     * Decrypts byte array encrypted with AES-256 GCM.
     * Expected layout: [12-byte IV][Ciphertext + 16-byte Auth Tag]
     */
    public static String decrypt(byte[] data, byte[] key) {
        if (data == null || key == null) return "";
        try {
            if (data.length <= IV_LENGTH) {
                // Fallback XOR if data format is raw XOR
                byte[] xorResult = new byte[data.length];
                for (int i = 0; i < data.length; i++) {
                    xorResult[i] = (byte) (data[i] ^ key[i % key.length]);
                }
                return new String(xorResult, StandardCharsets.UTF_8);
            }

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, data, 0, IV_LENGTH);
            SecretKeySpec keySpec = new SecretKeySpec(normalizeKey(key), "AES");
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            byte[] decrypted = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Graceful fallback: XOR decode
            byte[] fallback = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                fallback[i] = (byte) (data[i] ^ key[i % key.length]);
            }
            return new String(fallback, StandardCharsets.UTF_8);
        }
    }

    /**
     * Encrypts plain text bytes using AES-256 GCM.
     */
    public static byte[] encrypt(String data, byte[] key) {
        if (data == null) return new byte[0];
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            SecretKeySpec keySpec = new SecretKeySpec(normalizeKey(key), "AES");
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);

            byte[] cipherText = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return combined;
        } catch (Exception e) {
            // Fallback XOR
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] out = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                out[i] = (byte) (bytes[i] ^ key[i % key.length]);
            }
            return out;
        }
    }

    public static boolean overflow(String data, byte[] key) {
        return data != null && data.length() > 65535;
    }

    private static byte[] normalizeKey(byte[] key) {
        byte[] k = new byte[32]; // 256-bit AES key
        if (key == null || key.length == 0) return k;
        for (int i = 0; i < 32; i++) {
            k[i] = key[i % key.length];
        }
        return k;
    }
}
