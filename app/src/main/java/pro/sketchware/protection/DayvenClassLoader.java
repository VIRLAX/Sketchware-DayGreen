package pro.sketchware.protection;

import android.content.Context;
import dalvik.system.InMemoryDexClassLoader;
import dalvik.system.DexClassLoader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * SISTEM 30 — LAYER 1: Runtime DEX Decryptor & In-Memory Loader
 * Reads AES-256 GCM encrypted DEX files (.dvnx) from assets/dex
 * Decrypts purely in RAM and boots via InMemoryDexClassLoader (API 26+)
 * or ephemeral private cache fallback for older devices.
 */
public class DayvenClassLoader {

    private static final byte[] DEX_KEY = {
        (byte)0x44, (byte)0x61, (byte)0x79, (byte)0x76,
        (byte)0x65, (byte)0x6E, (byte)0x5F, (byte)0x4B,
        (byte)0x45, (byte)0x59, (byte)0x5F, (byte)0x56,
        (byte)0x34, (byte)0x2E, (byte)0x30, (byte)0x5F,
        (byte)0x53, (byte)0x45, (byte)0x43, (byte)0x55,
        (byte)0x52, (byte)0x45, (byte)0x5F, (byte)0x32,
        (byte)0x30, (byte)0x32, (byte)0x34, (byte)0x5F,
        (byte)0x58, (byte)0x58, (byte)0x58, (byte)0x58
    };

    private static final byte[] MAGIC = {
        (byte)0x44, (byte)0x56, (byte)0x4E, (byte)0x58
    }; // DVNX

    private static ClassLoader loadedLoader = null;

    public static void init(Context context) {
        if (loadedLoader != null) return;
        try {
            loadedLoader = loadAllEncryptedDex(context);
        } catch (Exception e) {
            android.util.Log.e("DCL", "Init failed: " + e.getClass().getSimpleName());
        }
    }

    private static ClassLoader loadAllEncryptedDex(Context context) throws Exception {
        String[] assetFiles = context.getAssets().list("dex");
        if (assetFiles == null || assetFiles.length == 0) {
            return context.getClassLoader();
        }

        List<ByteBuffer> dexBuffers = new ArrayList<>();

        for (String assetFile : assetFiles) {
            if (!assetFile.endsWith(".dvnx")) continue;

            byte[] encryptedData = readAsset(context, "dex/" + assetFile);
            byte[] decryptedDex = decryptDex(encryptedData);

            if (decryptedDex != null) {
                dexBuffers.add(ByteBuffer.wrap(decryptedDex));
            }
        }

        if (dexBuffers.isEmpty()) return context.getClassLoader();

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            ByteBuffer[] bufferArray = dexBuffers.toArray(new ByteBuffer[0]);
            return new InMemoryDexClassLoader(bufferArray, context.getClassLoader());
        }

        File dexDir = new File(context.getCacheDir(), "dcl_" + Long.toHexString(System.currentTimeMillis()));
        dexDir.mkdirs();
        dexDir.deleteOnExit();

        List<String> dexPaths = new ArrayList<>();
        for (int i = 0; i < dexBuffers.size(); i++) {
            File tempDex = new File(dexDir, "c" + i + ".dex");
            FileOutputStream fos = new FileOutputStream(tempDex);
            byte[] data = new byte[dexBuffers.get(i).remaining()];
            dexBuffers.get(i).get(data);
            fos.write(data);
            fos.close();
            tempDex.deleteOnExit();
            dexPaths.add(tempDex.getAbsolutePath());
        }

        String pathList = android.text.TextUtils.join(File.pathSeparator, dexPaths);
        return new DexClassLoader(
                pathList,
                dexDir.getAbsolutePath(),
                null,
                context.getClassLoader()
        );
    }

    public static byte[] decryptDex(byte[] data) {
        if (data == null || data.length < 16) return null;
        try {
            for (int i = 0; i < 4; i++) {
                if (data[i] != MAGIC[i]) return null;
            }

            byte[] iv = new byte[12];
            System.arraycopy(data, 4, iv, 0, 12);

            byte[] ciphertext = new byte[data.length - 16];
            System.arraycopy(data, 16, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(DEX_KEY, "AES");
            GCMParameterSpec paramSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readAsset(Context ctx, String path) throws IOException {
        InputStream is = ctx.getAssets().open(path);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = is.read(buf)) != -1) bos.write(buf, 0, read);
        is.close();
        return bos.toByteArray();
    }

    public static ClassLoader getLoader() {
        return loadedLoader;
    }
}
