// ============================================================
// SISTEM 30 — COMPILE-TIME FULL APK ENCRYPTION ENGINE
// (AUTO-ENCRYPT DEX + MANIFEST + ARSC + STRING)
// ============================================================
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

// DEX Encryption Master Key — 32 bytes AES-256
def DEX_MASTER_KEY = [
    (byte)0x44,(byte)0x61,(byte)0x79,(byte)0x76,
    (byte)0x65,(byte)0x6E,(byte)0x5F,(byte)0x4B,
    (byte)0x45,(byte)0x59,(byte)0x5F,(byte)0x56,
    (byte)0x34,(byte)0x2E,(byte)0x30,(byte)0x5F,
    (byte)0x53,(byte)0x45,(byte)0x43,(byte)0x55,
    (byte)0x52,(byte)0x65,(byte)0x5F,(byte)0x32,
    (byte)0x30,(byte)0x32,(byte)0x34,(byte)0x5F,
    (byte)0x58,(byte)0x58,(byte)0x58,(byte)0x58
] as byte[]

afterEvaluate {
    android.applicationVariants.all { variant ->
        variant.assembleProvider.get().doLast {
            def dexDir = new File(
                "${buildDir}/intermediates/dex/" +
                "${variant.name}/mergeDex${variant.name.capitalize()}"
            )

            if (!dexDir.exists()) {
                println "[DayvenProtect] DEX directory not found, skipping encryption"
                return
            }

            dexDir.listFiles()?.each { dexFile ->
                if (!dexFile.name.endsWith(".dex")) return

                println "[DayvenProtect] Encrypting DEX: ${dexFile.name}"

                byte[] dexBytes = dexFile.bytes
                byte[] iv = new byte[12]
                new SecureRandom().nextBytes(iv)

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding")
                SecretKeySpec keySpec = new SecretKeySpec(DEX_MASTER_KEY, "AES")
                GCMParameterSpec paramSpec = new GCMParameterSpec(128, iv)
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, paramSpec)

                byte[] encrypted = cipher.doFinal(dexBytes)

                // Magic bytes: DVNX
                byte[] magic = [(byte)0x44,(byte)0x56,(byte)0x4E,(byte)0x58]
                byte[] combined = new byte[4 + 12 + encrypted.length]
                System.arraycopy(magic, 0, combined, 0, 4)
                System.arraycopy(iv, 0, combined, 4, 12)
                System.arraycopy(encrypted, 0, combined, 16, encrypted.length)

                def assetsDir = new File("${projectDir}/src/main/assets/dex")
                assetsDir.mkdirs()

                def encFile = new File(assetsDir, dexFile.name.replace(".dex", ".dvnx"))
                encFile.bytes = combined

                // Replace original DEX with decoy poison DEX header + noise
                dexFile.bytes = generatePoisonDex(dexFile.name, dexBytes.length)
                println "[DayvenProtect] Protected ${dexFile.name} -> ${encFile.name}"
            }
        }
    }
}

def generatePoisonDex(String originalName, int approximateSize) {
    byte[] poisonHeader = [
        (byte)0x64,(byte)0x65,(byte)0x78,(byte)0x0A,
        (byte)0x30,(byte)0x33,(byte)0x35,(byte)0x00,
        (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0x80,(byte)0x02,(byte)0x00,(byte)0x00,
        (byte)0x70,(byte)0x00,(byte)0x00,(byte)0x00,
        (byte)0x78,(byte)0x56,(byte)0x34,(byte)0x12
    ] as byte[]

    def random = new SecureRandom()
    byte[] noise = new byte[Math.max(approximateSize, 512)]
    random.nextBytes(noise)

    def decoyStrings = [
        "com.google.android.play.core.AppUpdateManager",
        "com.facebook.react.bridge.NativeModule",
        "io.flutter.embedding.android.FlutterActivity",
        "com.unity3d.player.UnityPlayer",
        "com.adobe.phonegap.app.MainApplication"
    ]

    int offset = 128
    decoyStrings.each { s ->
        byte[] strBytes = s.getBytes("UTF-8")
        if (offset + strBytes.length < noise.length) {
            System.arraycopy(strBytes, 0, noise, offset, strBytes.length)
            offset += strBytes.length + 8
        }
    }

    byte[] result = new byte[poisonHeader.length + noise.length]
    System.arraycopy(poisonHeader, 0, result, 0, poisonHeader.length)
    System.arraycopy(noise, 0, result, poisonHeader.length, noise.length)
    return result
}
