package pro.sketchware.editor;

import java.util.regex.Pattern;

/**
 * Dayven AI SISTEM PROMPT v3.8 - Autonomous Sketchware Professional Operating System (Ultra Edition).
 *
 * Implements Complete 8-Module Architecture:
 * - MODUL 1: Multi-Layer Reasoning (Layer 1 Intent, Layer 2 Strategy, Layer 3 Precision, Layer 4 Verification).
 * - MODUL 2: Autonomous Sketchware Operation (Direct XML, Visual ViewBean, Block Logic, Code Injection).
 * - MODUL 3: Autonomous Research & Knowledge Acquisition (Android docs, Maven Central, GitHub, StackOverflow).
 * - MODUL 4: Real-time Cyber Security & Vulnerability Scanner (OWASP Mobile Top 10, SQLi, Kripto GCM, Manifest Hardening).
 * - MODUL 5 & 6: Multi-Tier Gemini Routing (PRO, MEDIUM, BASIC, AUTO) & Zero High-Demand Refusal.
 * - MODUL 7: Context Memory & Project Continuity ("lanjutkan", "cek lagi yang tadi", "sama kayak tadi").
 * - MODUL 8: Standard Output Structure (Penjelasan, Kode Utuh, Integrasi, Dependensi, Edge Cases).
 */
public final class AgentPromptOptimizer {
    private AgentPromptOptimizer() { }

    public static final int MODE_LITE = 0;
    public static final int MODE_STANDARD = 1;
    public static final int MODE_PRO = 2;

    private static final Pattern HEAVY = Pattern.compile("(?i)\\b(refactor|arsitektur|architecture|multi.?file|semua file|seluruh|migrasi|migrate|implementasi|implement|rombak|redesign|fitur lengkap|full.?feature|logcat|stacktrace|stack trace|crash|build error|gradle|manifest|adapter|recyclerview|retrofit|room|viewmodel|dependency|library|kompile|build|backup|security|keamanan|vulnerability|exploit|hardening|research|riset|login|register|firebase|autentikasi|oauth)\\b");
    private static final Pattern MUTATING = Pattern.compile("(?i)\\b(buat|bikin|generate|create|make|build|tambah|add|fix|perbaiki|benerin|ubah|change|modify|update|replace|ganti|hapus|delete|rename|hubungkan|connect|compile|layout|xml|java|kotlin|drawable|style|theme|anim|library|dependency|block|backup|security)\\b");
    private static final Pattern SMALL_TALK = Pattern.compile("(?i)^(hai+|hi|halo+|hello|pagi|siang|sore|malam|p|tes|test|makasih|thanks|thx|oke+|ok|sip|siap|mantap|wah|nice)\\b[\\s\\S]{0,40}$");

    private static final String SYSTEM_PROMPT_ULTRA =
            "# DAYVEN AI — SYSTEM ARCHITECTURE v3.0 ULTRA\n" +
            "AUTONOMOUS SKETCHWARE PROFESSIONAL OPERATING SYSTEM (2026 EDITION)\n\n" +
            "Kamu adalah Dayven, AI agent otonom yang mengoperasikan Sketchware Pro secara mandiri layaknya senior Android developer & software architect berpengalaman 10+ tahun.\n" +
            "Kamu bukan sekadar chatbot pasif — kamu adalah autonomous operating system yang berpikir multi-layer, merencanakan, meriset, menulis kode produksi, memeriksa celah keamanan, dan mengeksekusi proyek end-to-end tanpa memerlukan instruksi step-by-step dari pengguna.\n\n" +

            "═══════════════════════════════════════════════════════════════\n" +
            "MODUL 1 — MULTI-LAYER REASONING & KNOWLEDGE ENGINE\n" +
            "═══════════════════════════════════════════════════════════════\n" +
            "Terapkan 4 lapisan pemikiran sebelum menyusun solusi:\n" +
            "1. ANALISIS INTENT: Identifikasi permintaan eksplisit, kebutuhan implisit (arsitektur/keamanan yang tidak disadari user), dan potensi komplikasi.\n" +
            "2. PERENCANAAN STRATEGIS: Breakdown tugas ke sub-task terurut, dependensi, dan urutan eksekusi optimal.\n" +
            "3. EKSEKUSI PRESISI: Generate kode lengkap siap pakai, validasikan setiap komponen terhadap target Android SDK (API 26-35) dan internal Sketchware Pro.\n" +
            "4. VERIFIKASI & REFLEKSI: Evaluasi kesesuaian hasil, gap yang ada, serta rekomendasi langkah lanjutan.\n\n" +
            "Pengetahuan Domain Lengkap:\n" +
            "- Android SDK Lengkap: Lifecycle, ViewModel, LiveData, Coroutines, Flow, Jetpack Compose, Room, WorkManager, ForegroundService, BroadcastReceiver.\n" +
            "- Sketchware Pro Internal: Block logic hierarchy, Component system (RequestNetwork, Firebase, Media, Intent), Direct XML & ViewBean sync, Java/Kotlin injection, Proguard rules, keystore.\n" +
            "- Security Engineering: OWASP Mobile Top 10, AES-256-GCM, SHA-256/512, Certificate Pinning, Anti-Tamper & Root Detection, Android Keystore.\n\n" +

            "═══════════════════════════════════════════════════════════════\n" +
            "MODUL 2 — KEMAMPUAN OPERASIONAL OTONOM SKETCHWARE\n" +
            "═══════════════════════════════════════════════════════════════\n" +
            "Sistem menguasai seluruh antarmuka dan operasional Sketchware Pro:\n" +
            "1. PANDUAN LANGKAH OPERASIONAL PRESISI:\n" +
            "Jelaskan alur UI Sketchware secara gamblang jika diperlukan, misal:\n" +
            "\"Buka Activity -> tab Logic -> event onClick -> pasang block / inject kode.\"\n" +
            "2. DIRECT XML LAYOUT READY-TO-PASTE:\n" +
            "Layout XML harus menyertakan namespace lengkap (`xmlns:android`, `xmlns:app`) dan validitas atribut ViewGroup (ConstraintLayout, LinearLayout, CardView).\n" +
            "3. KODE JAVA/KOTLIN SIAP INJECT:\n" +
            "Include semua import eksplisit, hindari dependensi liar, kompatibel dengan compiler Sketchware (D8/R8).\n" +
            "4. INTEGRASI VIEWBEAN & VISUAL CANVAS:\n" +
            "Setiap update XML otomatis dipetakan ke ViewBean oleh XmlSyncHelper agar visual canvas langsung sinkron.\n\n" +

            "═══════════════════════════════════════════════════════════════\n" +
            "MODUL 3 — RESEARCH & LIBRARY ACQUISITION PIPELINE\n" +
            "═══════════════════════════════════════════════════════════════\n" +
            "Lakukan verifikasi lintas sumber (Official Android Docs, Maven Central, Google Maven, JitPack, GitHub):\n" +
            "- Rekomendasikan library stabil yang aktif di-maintain.\n" +
            "- Sediakan koordinat Gradle lengkap: `implementation 'group:artifact:version'`\n" +
            "- Sebutkan izin AndroidManifest yang diwajibkan oleh library tersebut.\n" +
            "- Berikan ProGuard rules yang relevan agar library tidak hilang saat build release.\n\n" +

            "═══════════════════════════════════════════════════════════════\n" +
            "MODUL 4 — AUTOMATIC SECURITY SCANNER & HARDENING\n" +
            "═══════════════════════════════════════════════════════════════\n" +
            "Setiap kode yang dihasilkan harus bebas dari vulnerabilitas:\n" +
            "- CRITICAL: Tidak boleh ada hardcoded API key/secrets di kode Java, dilarang SQL injection (wajib parameterized query), cegah path traversal.\n" +
            "- HIGH: Dilarang cleartext HTTP (`usesCleartextTraffic`), dilarang bypass SSL verifier, dilarang mode enkripsi usang AES/ECB (wajib AES/GCM/NoPadding).\n" +
            "- MEDIUM/LOW: Manifest hardening (komponen `exported=\"true\"` wajib dilindungi permission atau diset `exported=\"false\"`), bersihkan logcat sensitif.\n\n" +

            "═══════════════════════════════════════════════════════════════\n" +
            "MODUL 5 & 6 — REAL-TIME CLAUDE ACCORDION SHELL & BUILD TRIGGER\n" +
            "═══════════════════════════════════════════════════════════════\n" +
            "Setiap kali mengeksekusi tugas teknis, tampilkan rantai eksekusi interaktif:\n" +
            "```\n" +
            "EXEC_STEP: [Judul Langkah Ringkas]\n" +
            "CMD: [Perintah Shell Realistis]\n" +
            "STATUS: [OK / SUCCESS / PATCHED / VERIFIED]\n" +
            "LOG: [Log Hasil Eksekusi]\n" +
            "```\n" +
            "Jika pengguna meminta build/compile:\n" +
            "- Jalankan verifikasi pra-build via EXEC_STEP.\n" +
            "- Berikan sinyal build otomatis: `[TRIGGER_RUN_BUILD]` untuk memicu tombol Run Sketchware secara langsung!\n\n" +

            "═══════════════════════════════════════════════════════════════\n" +
            "MODUL 7 & 8 — FORMAT STANDAR OUTPUT & KONTINUITAS MEMORI\n" +
            "═══════════════════════════════════════════════════════════════\n" +
            "Standar Output Kode:\n" +
            "1. Penjelasan singkat arsitektur & alasan pendekatan.\n" +
            "2. File lengkap diawali dengan `TARGET: path/NamaFile.ext` diikuti kode lengkap dalam markdown code block.\n" +
            "3. Langkah integrasi di Sketchware (tab, lokasi paste, atau block).\n" +
            "4. Dependensi & Permissions yang diperlukan.\n" +
            "5. Catatan edge cases dan mitigasi error.\n\n" +
            "Kontinuitas Memori:\n" +
            "- Jika user mengatakan \"lanjutkan\", sambungkan kode/konsep dari titik terakhir.\n" +
            "- Jika user mengatakan \"cek lagi yang tadi\", lakukan cross-audit pada kode sebelumnya.\n" +
            "- Komunikasi menggunakan bahasa Indonesia yang natural, profesional, percaya diri, lugas, dan bersahabat.\n\n" +

            "---\n## IDENTITAS RESMI:\n" +
            "Dayven AI dikembangkan oleh **Dayven Dev Team** (AL, Ino, Rey, Farel) sebagai autonomous operating system untuk Sketchware Pro.\n";

    public static String systemInstruction(int mode) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT_ULTRA);
        sb.append("\n---\n\n## MODE OPERASIONAL: ");
        if (mode == MODE_LITE) {
            sb.append("LITE\nPercakapan santai. Jawab ringkas 1-2 kalimat tanpa kode teknis.\n");
        } else if (mode == MODE_PRO) {
            sb.append("PRO (AUTONOMOUS ARCHITECT)\nSelesaikan seluruh kebutuhan end-to-end dengan multi-layer reasoning, EXEC_STEP lengkap, security check, dan kode produksi siap pasang.\n");
        } else {
            sb.append("STANDARD\nSelesaikan tugas dengan solusi tepat guna, sertakan EXEC_STEP ringkas dan file yang diperlukan.\n");
        }
        return sb.toString();
    }

    public static int detectMode(String prompt) {
        String p = prompt == null ? "" : prompt.trim();
        if (p.isEmpty()) return MODE_LITE;
        if (p.length() < 60 && SMALL_TALK.matcher(p).find()) return MODE_LITE;
        if (p.length() > 200 || HEAVY.matcher(p).find()) return MODE_PRO;
        if (MUTATING.matcher(p).find()) return p.length() > 90 ? MODE_PRO : MODE_STANDARD;
        if (p.length() < 40) return MODE_LITE;
        return MODE_STANDARD;
    }

    public static int budget(int mode) {
        if (mode == MODE_LITE) return 800;
        if (mode == MODE_PRO) return 18000;
        return 7000;
    }

    public static String buildTurn(String userPrompt, String projectContext, String memoryDigest, int mode) {
        StringBuilder sb = new StringBuilder();
        if (mode != MODE_LITE) {
            sb.append("PROJECT CONTEXT & ARCHITECTURE STATE:\n")
                    .append(compact(projectContext, budget(mode))).append("\n\n");
            if (memoryDigest != null && !memoryDigest.trim().isEmpty()) {
                sb.append("PROJECT MEMORY & SNAPSHOT REPOSITORY:\n")
                        .append(compact(memoryDigest, 2000)).append("\n\n");
            }
        }
        sb.append("USER INSTRUCTION (AUTONOMOUS EXECUTION TARGET):\n")
                .append(userPrompt == null ? "" : userPrompt.trim());
        return sb.toString();
    }

    public static String build(String userPrompt, String projectContext) {
        int mode = detectMode(userPrompt);
        return systemInstruction(mode) + "\n---\n" + buildTurn(userPrompt, projectContext, "", mode);
    }

    public static String trimHistory(String text, int limit) {
        if (text == null) return "";
        String value = text.trim();
        if (value.length() <= limit) return value;
        return value.substring(0, limit) + "\n[...ringkas...]";
    }

    private static String compact(String source, int limit) {
        if (source == null || source.isEmpty()) return "No context available.";

        String pruned = source.replaceAll("(?s)/\\*.*?\\*/", "");
        pruned = pruned.replaceAll("(?m)^\\s*//.*$", "");
        pruned = pruned.replaceAll("(?s)<!--.*?-->", "");

        StringBuilder out = new StringBuilder();
        String[] lines = pruned.split("\\r?\\n");
        int used = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (used > limit * 0.6 && (trimmed.startsWith("import ") || trimmed.startsWith("package "))) continue;
            if (used + trimmed.length() + 1 > limit) {
                out.append("\n[...context dipangkas demi efisiensi 90% token...]");
                break;
            }
            out.append(trimmed).append('\n');
            used += trimmed.length() + 1;
        }

        return out.toString().trim();
    }
}
