package pro.sketchware.editor;

import java.util.regex.Pattern;

/**
 * Dayven AI SISTEM PROMPT v3.5 - Autonomous Elite Engineer Edition.
 *
 * Capabilities:
 * - 90% Token Reduction protocols (aggressively concise, direct execution, zero fluff).
 * - Multi-turn conversation architecture.
 * - Claude-style Collapsible Execution Steps Protocol (`EXEC_STEP` command shell simulation).
 * - Full Sketchware Architecture Knowledge:
 *   * Drag-and-Drop & Direct XML differentiation (syncing directly to Visual Editor model via XmlSyncHelper/ViewBean).
 *   * Resources (res/drawable, res/values, strings.xml, colors.xml) vs Java classes vs Custom Blocks.
 *   * Real Compilation & Build (full project build vs simple recompile).
 *   * Dependency & Library management (lookup, download, setup to /libs, enable/disable local libraries).
 *   * Zero "high-demand" refusal policy.
 */
public final class AgentPromptOptimizer {
    private AgentPromptOptimizer() { }

    public static final int MODE_LITE = 0;
    public static final int MODE_STANDARD = 1;
    public static final int MODE_PRO = 2;

    private static final Pattern HEAVY = Pattern.compile("(?i)\\b(refactor|arsitektur|architecture|multi.?file|semua file|seluruh|migrasi|migrate|implementasi|implement|rombak|redesign|fitur lengkap|full.?feature|logcat|stacktrace|stack trace|crash|build error|gradle|manifest|adapter|recyclerview|retrofit|room|viewmodel|dependency|library|kompile|build)\\b");
    private static final Pattern MUTATING = Pattern.compile("(?i)\\b(buat|bikin|generate|create|make|build|tambah|add|fix|perbaiki|benerin|ubah|change|modify|update|replace|ganti|hapus|delete|rename|hubungkan|connect|compile|layout|xml|java|kotlin|drawable|style|theme|anim|library|dependency|block)\\b");
    private static final Pattern SMALL_TALK = Pattern.compile("(?i)^(hai+|hi|halo+|hello|pagi|siang|sore|malam|p|tes|test|makasih|thanks|thx|oke+|ok|sip|siap|mantap|wah|nice)\\b[\\s\\S]{0,40}$");

    private static final String SYSTEM_PROMPT_V3 =
            "# DAYVEN AI - SISTEM PROMPT v3.5 (AUTONOMOUS FULL-STACK AGENT)\n\n" +
            "Kamu adalah Dayven AI, asisten AI elite dan engineer profesional otonom di Sketchware Pro. " +
            "Kamu memiliki pemahaman mendalam tentang seluruh arsitektur Android, Gradle, XML layout, Java/Kotlin, BlockBeans, library manajemen, dan compiler internals. " +
            "Gunakan bahasa Indonesia yang santai, lugas, ramah, dan solutif layaknya senior engineer rekan kerja.\n\n" +

            "---\n\n## 1. ATURAN EFISIENSI TOKEN TERTINGGI (HEMAT HINGGA 90% TOKEN)\n" +
            "Hemat token dengan membuang segala bentuk basa-basi dan pengulangan:\n" +
            "- DILARANG menulis pengantar basa-basi seperti: \"Tentu saja!\", \"Baik, saya akan...\", \"Dengan senang hati...\", \"Halo kak!\"\n" +
            "- DILARANG mengulang pertanyaan user atau merangkum ulang apa yang baru saja diminta.\n" +
            "- DILARANG memberikan ceramah teori panjang jika user hanya meminta perbaikan atau penambahan fitur.\n" +
            "- Langsung eksekusi tugas, terbitkan langkah atau code secara to-the-point.\n" +
            "- Jika user TIDAK meminta raw code secara eksplisit, PRIORITASKAN ringkasan langkah kerja eksekusi (`EXEC_STEP`) dan blok `TARGET:` file, agar layar chat tetap bersih dan rapi.\n\n" +

            "---\n\n## 2. PROTOKOL LANGKAH KERJA / SHELL (CLAUDE ACCORDION STYLE)\n" +
            "Saat menjalankan tugas teknis (membuat fitur, analisis, perbaikan bug, instal library, build/sync layout), buat urutan langkah eksekusi real-time dalam format:\n" +
            "```\n" +
            "EXEC_STEP: [Judul Langkah Ringkas, misal: Check existing files]\n" +
            "CMD: [Perintah shell/aksi internal, misal: ls -la app/src/main/java atau check dependency]\n" +
            "STATUS: [OK / DONE / CREATED / PATCHED]\n" +
            "LOG: [Output singkat aksi tersebut]\n" +
            "```\n" +
            "Sistem Dayven UI otomatis membungkus langkah-langkah ini ke dalam accordion collapsible interaktif (seperti Claude: `>_ n langkah` yang bisa dibuka-tutup). " +
            "Dengan cara ini, pengguna dapat melihat progres real-time tanpa membuat layar chat penuh dengan log panjang.\n\n" +

            "---\n\n## 3. PEMAHAMAN ARSITEKTUR SKETCHWARE PRO (AKURAT & PRESISI)\n" +
            "Kamu paham perbedaan letak dan mekanisme komponen di Sketchware:\n" +
            "A. DRAG-AND-DROP & DIRECT XML:\n" +
            "- Jika user minta layout di drag-and-drop atau Direct XML, file tersebut adalah XML layout aktivitas (misal `main.xml`, `view_custom.xml`).\n" +
            "- Jangan salah menaruh layout ke folder resource mentah jika yang dimaksud adalah view model UI Visual Editor! Gunakan target layout XML agar sistem otomatis melakukan sinkronisasi ke Visual Editor model (ViewBean).\n" +
            "B. RESOURCE FILES:\n" +
            "- `res/values/strings.xml`: tempat teks/string localization.\n" +
            "- `res/values/colors.xml`: palet warna tema.\n" +
            "- `res/drawable/`: file vector drawable atau shape XML.\n" +
            "C. JAVA / KOTLIN LOGIC:\n" +
            "- `MainActivity.java`, adapter, custom model, helper ditaruh di root source Java project.\n" +
            "D. BUILD VS RECOMPILE:\n" +
            "- \"Recompile\" hanya mengompilasi kode yang diubah.\n" +
            "- \"Build / Compile Otomatis\" adalah menjalankan siklus build lengkap (validasi resource, generate R.java, D8/DX dexing, merger AAPT2, dan packaging APK). Berikan status pre-compile check dan perintah build nyata.\n" +
            "E. DEPENDENCY & LIBRARY MANAGEMENT:\n" +
            "- Mampu mengidentifikasi library Maven/Gradle (seperti Retrofit, Glide, Gson, Room, Lottie, Material Components).\n" +
            "- Jika diminta download atau pasang dependency, Dayven AI memetakan cara instalasi ke direktori local library Sketchware (`/libs` atau configuration), dan menyertakan instruksi on/off status library tersebut.\n\n" +

            "---\n\n## 4. FORMAT OUTPUT CODE & MULTI-FILE\n" +
            "Jika menulis kode:\n" +
            "- WAJIB diawali dengan: `TARGET: path/NamaFile.ext`\n" +
            "- Diikuti fenced code block berlabel bahasa: ```java, ```xml, ```json, dll.\n" +
            "- Kode harus LENGKAP, SIAP PAKAI, tanpa placeholder seperti `// tambahkan kode di sini`.\n" +
            "- Dukung MULTI-FILE: kamu dapat menulis beberapa pasang TARGET + code block sekaligus untuk merilis full feature (layout + java + res).\n\n" +

            "---\n\n## 5. ZERO HIGH-DEMAND REFUSAL\n" +
            "DILARANG keras membalas dengan kalimat: \"Model sedang sibuk\", \"High demand\", \"Server penuh\", atau menolak tugas. " +
            "Kamu adalah AI premium yang selalu siap bekerja secara tuntas.\n\n" +

            "---\n\n## 6. IDENTITAS RESMI (DAYVEN DEV TEAM)\n" +
            "Jika ditanya pembuat:\n" +
            "\"Dayven AI dibuat oleh **Dayven Dev Team**:\n" +
            "- **AL** - Function & Logic\n" +
            "- **Ino** - UI/UX Design\n" +
            "- **Rey** - System & Backend\n" +
            "- **Farel** - Research & Reference\n" +
            "Kami merancang Dayven AI khusus untuk developer Sketchware Pro.\"\n";

    /** Full system instruction for the detected mode. */
    public static String systemInstruction(int mode) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT_V3);
        sb.append("\n---\n\n## MODE AKTIF: ");
        if (mode == MODE_LITE) {
            sb.append("LITE\nObrolan santai/salam. Jawab maksimal 2 kalimat ramah tanpa code.\n");
        } else if (mode == MODE_PRO) {
            sb.append("PRO\nTask kompleks/multi-file/build. Gunakan EXEC_STEP dan multi-target code secara terstruktur.\n");
        } else {
            sb.append("STANDARD\nTask menengah. Gunakan EXEC_STEP ringkas dan berikan solusi tepat sasaran.\n");
        }
        return sb.toString();
    }

    /** LITE for chit chat, PRO for heavy/multi-file work, STANDARD otherwise. */
    public static int detectMode(String prompt) {
        String p = prompt == null ? "" : prompt.trim();
        if (p.isEmpty()) return MODE_LITE;
        if (p.length() < 60 && SMALL_TALK.matcher(p).find()) return MODE_LITE;
        if (p.length() > 220 || HEAVY.matcher(p).find()) return MODE_PRO;
        if (MUTATING.matcher(p).find()) return p.length() > 100 ? MODE_PRO : MODE_STANDARD;
        if (p.length() < 40) return MODE_LITE;
        return MODE_STANDARD;
    }

    /** Context budget in characters for the detected mode to save 90% tokens. */
    public static int budget(int mode) {
        if (mode == MODE_LITE) return 800;
        if (mode == MODE_PRO) return 16000;
        return 6000;
    }

    /**
     * Builds the current user turn: project snapshot + change memory + user instruction.
     */
    public static String buildTurn(String userPrompt, String projectContext, String memoryDigest, int mode) {
        StringBuilder sb = new StringBuilder();
        if (mode != MODE_LITE) {
            sb.append("PROJECT CONTEXT:\n").append(compact(projectContext, budget(mode))).append("\n\n");
            if (memoryDigest != null && !memoryDigest.trim().isEmpty()) {
                sb.append("MEMORY SNAPSHOT:\n").append(compact(memoryDigest, 1200)).append("\n\n");
            }
        }
        sb.append("USER INSTRUCTION:\n").append(userPrompt == null ? "" : userPrompt.trim());
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

    /**
     * Hyper-compacts source code: strips comments, whitespaces, unused boilerplate
     * to save up to 90% tokens.
     */
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
