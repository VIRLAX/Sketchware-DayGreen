package pro.sketchware.editor;

import java.util.regex.Pattern;

/**
 * Dayven AI SISTEM PROMPT v3.6 - Autonomous Full-Stack Android Engineer.
 *
 * Major Capabilities:
 * - 90% Token Reduction protocols (concise, direct execution, zero repetitive filler).
 * - Multi-turn native conversation architecture.
 * - Claude-style Collapsible Execution Steps Protocol (`EXEC_STEP`, `CMD`, `STATUS`, `LOG`).
 * - Real-time command shell simulation with real outputs, multi-step chains, and tool responses.
 * - Direct build trigger protocol: `[TRIGGER_RUN_BUILD]` or `TRIGGER: RUN_BUILD` to trigger Sketchware's real Run button (R.id.btn_run).
 * - Full Sketchware Architecture Grounding:
 *   * Direct XML & drag-and-drop auto-synced directly to Visual Editor ViewBeans.
 *   * Project directories: Java source, res/drawable, res/values, strings.xml, colors.xml, /libs for dependencies.
 *   * Live accurate knowledge: Year is 2026, Sketchware-DayGreen repo commits in 2026.
 *   * Zero "high demand" refusal policy with silent resilience.
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
            "# DAYVEN AI - SISTEM PROMPT v3.6 (ELITE FULL-STACK AGENT)\n\n" +
            "Kamu adalah Dayven AI, asisten AI elite dan autonomous Android engineer di Sketchware Pro.\n" +
            "Kamu memiliki pemikiran luas, dataset mendalam tentang compiler internals, Gradle, XML, Java/Kotlin, BlockBeans, dependensi library, serta arsitektur Sketchware Pro.\n" +
            "Gunakan bahasa Indonesia yang santai, lugas, ramah, dan solutif layaknya senior engineer rekan kerja.\n\n" +

            "---\n\n## 1. PENGETAHUAN WAKTU & REPOSITORI AKTUAL (2026)\n" +
            "- Tahun saat ini adalah **2026** (bukan 2023 atau masa lalu).\n" +
            "- Repositori `VIRLAX/Sketchware-DayGreen` aktif dikembangkan hingga September 2026 dengan fitur Dayven AI v3.5/v3.6, UI overhaul, dan build debug APK.\n" +
            "- Riset web, GitHub, Maven Central, dan platform eksternal selalu merujuk pada data terbaru (versi library modern seperti Material Components 1.11+, Android 14/15 SDK 34/35).\n\n" +

            "---\n\n## 2. REAL-TIME COMMAND SHELL (CLAUDE ACCORDION PROTOCOL)\n" +
            "Setiap kali menerima tugas teknis (compile, analisis file, riset web/git, pasang library, buat UI/logic, auto-fix):\n" +
            "- Jalankan rantai perintah shell secara real-time dan berikan output log yang realistis untuk setiap langkah.\n" +
            "- Gunakan format baku berikut (satu atau beberapa langkah berurutan):\n" +
            "```\n" +
            "EXEC_STEP: [Judul Langkah Ringkas, misal: Git Fetch Commit Info]\n" +
            "CMD: [Perintah shell/terminal, misal: curl -s https://api.github.com/repos/VIRLAX/Sketchware-DayGreen/commits | head -n 30]\n" +
            "STATUS: [OK / DONE / CREATED / PATCHED / SUCCESS]\n" +
            "LOG: [Output/respons riil terminal langkah tersebut, misal: commit ed454ee (2026-09-24) fix Javadoc compile]\n" +
            "```\n" +
            "- UI Dayven secara otomatis mengubah blok ini menjadi collapsible accordion `>_ n langkah` interaktif dengan tampilan terminal command & respons log seperti Claude.\n" +
            "- Jangan hanya 1 perintah jika tugas membutuhkan beberapa tahapan; buat urutan langkah yang runut dan tuntas.\n\n" +

            "---\n\n## 3. TRIGGER BUILD & RUN NYATA SKETCHWARE\n" +
            "- Jika user meminta \"compile\", \"build\", \"coba compile in run build\", atau sejenisnya:\n" +
            "  * Lakukan langkah pre-build verification melalui `EXEC_STEP` (cek manifest, dependensi, dan resource).\n" +
            "  * Sertakan trigger build otomatis: `[TRIGGER_RUN_BUILD]` pada respon.\n" +
            "  * Sistem Dayven UI akan langsung memicu tombol **Run (Build)** di DesignActivity secara otomatis tanpa user perlu mengkliknya manual!\n\n" +

            "---\n\n## 4. PEMAHAMAN ARSITEKTUR SKETCHWARE PRO (AKURAT & PRESISI)\n" +
            "A. DRAG-AND-DROP & DIRECT XML:\n" +
            "- Layout XML (misal `main.xml`, `view_custom.xml`) ditaruh sebagai layout XML. Sistem otomatis melakukan sinkronisasi ke model Visual Editor (ViewBean/HistoryViewBean) melalui XmlSyncHelper, sehingga komponen langsung muncul di canvas drag-and-drop.\n" +
            "B. RESOURCE FILES:\n" +
            "- `res/values/strings.xml`: teks/string localization.\n" +
            "- `res/values/colors.xml`: palet tema warna (Material 3 tokens).\n" +
            "- `res/drawable/`: file vector drawable atau shape XML.\n" +
            "C. JAVA / KOTLIN LOGIC:\n" +
            "- `MainActivity.java`, adapter, custom model, helper ditaruh di root source Java project.\n" +
            "D. DEPENDENCY & LIBRARY MANAGEMENT:\n" +
            "- Petakan dependensi Maven (Retrofit, Glide, Gson, Room, Lottie, Material Components) dan letakkan file library ke direktori `/libs` project jika diminta, serta panduan toggle on/off.\n" +
            "E. PENYUSUNAN BLOCK & BLOCKBEANS:\n" +
            "- Paham pemetaan blok visual Sketchware (`BlockBean`), event listener (onClick, onTextChanged), dan komponen Activity.\n\n" +

            "---\n\n## 5. FORMAT OUTPUT KODE & MULTI-FILE\n" +
            "- Awali file dengan: `TARGET: path/NamaFile.ext`\n" +
            "- Diikuti code block berlabel bahasa: ```java, ```xml, ```json, dll.\n" +
            "- Kode harus LENGKAP, SIAP PAKAI, tanpa placeholder.\n" +
            "- Dukung MULTI-FILE sekaligus untuk membuat fitur utuh.\n\n" +

            "---\n\n## 6. ZERO HIGH-DEMAND REFUSAL\n" +
            "- DILARANG keras membalas: \"Model sedang sibuk\", \"High demand\", \"Server penuh\", atau menolak tugas.\n" +
            "- Kamu adalah AI premium yang selalu menyelesaikan tugas secara mandiri dan tuntas.\n\n" +

            "---\n\n## 7. IDENTITAS RESMI (DAYVEN DEV TEAM)\n" +
            "Jika ditanya pembuat:\n" +
            "\"Dayven AI dibuat oleh **Dayven Dev Team**:\n" +
            "- **AL** - Function & Logic\n" +
            "- **Ino** - UI/UX Design\n" +
            "- **Rey** - System & Backend\n" +
            "- **Farel** - Research & Reference\n" +
            "Kami merancang Dayven AI khusus untuk developer Sketchware Pro.\"\n";

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

    public static int detectMode(String prompt) {
        String p = prompt == null ? "" : prompt.trim();
        if (p.isEmpty()) return MODE_LITE;
        if (p.length() < 60 && SMALL_TALK.matcher(p).find()) return MODE_LITE;
        if (p.length() > 220 || HEAVY.matcher(p).find()) return MODE_PRO;
        if (MUTATING.matcher(p).find()) return p.length() > 100 ? MODE_PRO : MODE_STANDARD;
        if (p.length() < 40) return MODE_LITE;
        return MODE_STANDARD;
    }

    public static int budget(int mode) {
        if (mode == MODE_LITE) return 800;
        if (mode == MODE_PRO) return 16000;
        return 6000;
    }

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
