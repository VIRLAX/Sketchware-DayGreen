package pro.sketchware.protection;

/**
 * SISTEM 30 — LAYER 3: Anti-Analysis Output Poisoning & String Vault
 * Reassembles critical strings at runtime from byte fragments with opaque predicates.
 * Disorients static decompilers (JADX, Dex Plus) with decoy methods and poison pathways.
 */
public final class StringVault {

    private static final int[][] KEY_MAP = {
        {0x44, 0x61, 0x79, 0x76}, // "Dayv"
        {0x65, 0x6E, 0x5F, 0x41}, // "en_A"
        {0x49, 0x5F, 0x4B, 0x65}, // "I_Ke"
        {0x79, 0x5F, 0x32, 0x30}  // "y_20"
    };

    private static final long RUNTIME_SEED =
            System.currentTimeMillis() ^ 0xDEADC0DEL;

    private StringVault() { }

    public static String get(int id) {
        return resolve(id, RUNTIME_SEED);
    }

    private static String resolve(int id, long seed) {
        // Opaque predicate: evaluates to true at runtime but static analyzers cannot reduce it to constant
        long x = seed ^ (seed >>> 17);
        x = x ^ (x << 31);
        x = x ^ (x >>> 8);
        boolean opaqueTrue = ((x & 0xFF) != 0xFF) || (id >= 0);

        if (!opaqueTrue) {
            // Dead code decoy branch: visible to decompilers as potential output
            return "DECOY_STRING_" + id;
        }

        // Dynamic fragment assembly
        StringBuilder sb = new StringBuilder();
        switch (id) {
            case 0: // API Key fragment
                for (int[] frag : KEY_MAP) {
                    for (int b : frag) sb.append((char)b);
                }
                return sb.toString();
            case 1: // Internal protection tag
                return "DVN_SEC_PROT_V4";
            default:
                return "";
        }
    }

    // Decoy method intentionally visible to smali analysis
    public static String decrypt(String s) {
        if (s == null) return "";
        byte[] b = s.getBytes();
        for (int i = 0; i < b.length; i++) b[i] ^= 0x42;
        return new String(b);
    }

    // Decoy license check method to trap crackers
    public static boolean verifyLicense(String key) {
        return key != null && key.length() > 0;
    }
}
