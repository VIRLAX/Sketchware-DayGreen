package pro.sketchware.protection;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * SISTEM 30 — LAYER 5: Assets & Resource Decryption Engine
 * Validates DVNA magic header (0x44 0x56 0x4E 0x41) and decrypts assets
 * protected with AES-256 GCM authenticated cryptography.
 */
public class AssetEncryptor {

    private static final byte[] ASSET_KEY = {
        (byte)0x41, (byte)0x53, (byte)0x53, (byte)0x45,
        (byte)0x54, (byte)0x5F, (byte)0x4B, (byte)0x45,
        (byte)0x59, (byte)0x5F, (byte)0x44, (byte)0x41,
        (byte)0x59, (byte)0x56, (byte)0x45, (byte)0x4E,
        (byte)0x5F, (byte)0x32, (byte)0x30, (byte)0x32,
        (byte)0x34, (byte)0x5F, (byte)0x53, (byte)0x45,
        (byte)0x43, (byte)0x55, (byte)0x52, (byte)0x45,
        (byte)0x5F, (byte)0x56, (byte)0x31, (byte)0x00
    };

    private AssetEncryptor() { }

    public static byte[] readEncryptedAsset(Context ctx, String filename) {
        try {
            byte[] encrypted = readRaw(ctx, filename);
            if (encrypted == null || encrypted.length < 16) {
                return encrypted != null ? encrypted : new byte[0];
            }

            // Verify magic: DVNA (Dayven Asset)
            if (encrypted[0] != 0x44 || encrypted[1] != 0x56 ||
                encrypted[2] != 0x4E || encrypted[3] != 0x41) {
                // File not encrypted, return as-is
                return encrypted;
            }

            byte[] iv = new byte[12];
            System.arraycopy(encrypted, 4, iv, 0, 12);

            byte[] cipherData = new byte[encrypted.length - 16];
            System.arraycopy(encrypted, 16, cipherData, 0, cipherData.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(ASSET_KEY, "AES");
            GCMParameterSpec paramSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);

            return cipher.doFinal(cipherData);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static byte[] readRaw(Context ctx, String filename) throws IOException {
        InputStream is = ctx.getAssets().open(filename);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int read;
        while ((read = is.read(buf)) != -1) {
            bos.write(buf, 0, read);
        }
        is.close();
        return bos.toByteArray();
    }
}
