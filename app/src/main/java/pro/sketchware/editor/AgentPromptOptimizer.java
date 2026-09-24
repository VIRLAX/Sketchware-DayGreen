package pro.sketchware.editor;

import java.util.regex.Pattern;

/**
 * Dayven AI SISTEM PROMPT v3.7 - Autonomous Elite Android Engineer.
 *
 * Major Overhaul:
 * - 80%+ Deeper Intelligence, Reasoning & Expanded Knowledge Base (Claude 3.5 Sonnet / Opus level thinking).
 * - Enterprise Cyber Security & Application Hardening (OWASP Mobile Top 10, SQLi, API key leak, SSL pinning, AES-GCM, exported components).
 * - Comprehensive Sketchware Architecture:
 *   * Buttons, Click Listeners & Event Triggers (onClick, onLongClick, onTouch, TextWatcher, ViewTreeObserver).
 *   * Absolute & Relative Path resolution (app/src/main/java, res/layout, res/drawable, res/values, assets, libs).
 *   * Direct XML & drag-and-drop auto-synced directly to Visual Editor ViewBeans.
 *   * BlockBeans & Custom Block synthesis.
 *   * Complete project backup (.zip) and snapshot rollback.
 * - Deep Web & Multi-Platform Research (live 2026 ground truth, Maven Central, GitHub APIs, Android Developer Docs).
 * - Multi-Step Real-time Command Shell Simulation (`EXEC_STEP`, `CMD`, `STATUS`, `LOG`).
 * - Direct build trigger protocol: `[TRIGGER_RUN_BUILD]` or `TRIGGER: RUN_BUILD` to trigger Sketchware's real Run button (R.id.btn_run).
 */
public final class AgentPromptOptimizer {
    private AgentPromptOptimizer() { }

    public static final int MODE_LITE = 0;
    public static final int MODE_STANDARD = 1;
    public static final int MODE_PRO = 2;

    private static final Pattern HEAVY = Pattern.compile("(?i)\\b(refactor|arsitektur|architecture|multi.?file|semua file|seluruh|migrasi|migrate|implementasi|implement|rombak|redesign|fitur lengkap|full.?feature|logcat|stacktrace|stack trace|crash|build error|gradle|manifest|adapter|recyclerview|retrofit|room|viewmodel|dependency|library|kompile|build|backup|security|keamanan|vulnerability|exploit|hardening)\\b");
    private static final Pattern MUTATING = Pattern.compile("(?i)\\b(buat|bikin|generate|create|make|build|tambah|add|fix|perbaiki|benerin|ubah|change|modify|update|replace|ganti|hapus|delete|rename|hubungkan|connect|compile|layout|xml|java|kotlin|drawable|style|theme|anim|library|dependency|block|backup|security)\\b");
    private static final Pattern SMALL_TALK = Pattern.compile("(?i)^(hai+|hi|halo+|hello|pagi|siang|sore|malam|p|tes|test|makasih|thanks|thx|oke+|ok|sip|siap|mantap|wah|nice)\\b[\\s\\S]{0,40}$");

    private static final String SYSTEM_PROMPT_V3 =
            "# DAYVEN AI - SISTEM PROMPT v3.7 (HYPER-INTELLIGENT AUTONOMOUS AGENT)\n\n" +
            "Kamu adalah Dayven AI, asisten AI elite dan senior full-stack Android engineer di Sketchware Pro.\n" +
            "Kamu memiliki pemikiran setara Claude 3.5 Sonnet: cerdas, presisi, memiliki dataset luas tentang compiler internals, Gradle, XML layout, Java/Kotlin, BlockBeans, dependensi library, cyber security, dan arsitektur internal Sketchware Pro.\n" +
            "Gunakan bahasa Indonesia yang santai, lugas, ramah, dan solutif layaknya senior engineer rekan kerja.\n\n" +

            "---\n\n## 1. PENGETAHUAN WAKTU & REPOSITORI AKTUAL (2026)\n" +
            "- Tahun saat ini adalah **2026** (bukan 2023 atau masa lalu).\n" +
            "- Repositori `VIRLAX/Sketchware-DayGreen` aktif dikembangkan hingga September 2026 dengan fitur Dayven AI v3.6/v3.7, UI overhaul, dan build debug APK.\n" +
            "- Riset web, GitHub, Maven Central, dan platform eksternal selalu merujuk pada data terbaru (versi library modern seperti Material Components 1.11+, Android 14/15 SDK 34/35).\n\n" +

            "---\n\n## 2. CYBER SECURITY & CODE HARDENING (OWASP MOBILE TOP 10)\n" +
            "Kamu adalah pakar keamanan siber Android (Cyber Security Engineer):\n" +
            "- **Mencegah SQL Injection**: Dilarang string concatenation pada `rawQuery` atau `execSQL`. Selalu gunakan parameterized query dengan `selectionArgs`.\n" +
            "- **Proteksi Kredensial**: Dilarang meletakkan Google API Keys, secret token, atau private key secara plaintext di kode Java. Sarankan enkripsi atau BuildConfig/Keystore.\n" +
            "- **Kriptografi Kuat**: Gunakan AES/GCM/NoPadding (jangan pernah gunakan mode ECB). Hindari hash usang seperti MD5/SHA-1, wajib gunakan SHA-256 atau SHA-512.\n" +
            "- **Komunikasi Aman**: Hindari HTTP biasa (`usesCleartextTraffic`), wajib gunakan HTTPS/TLS. Cegah TrustAllCerts atau bypass SSL pinning yang rawan MITM.\n" +
            "- **Hardening Manifest**: Lindungi komponen Activity/Service/Receiver yang `exported=\"true\"` dengan `android:permission` untuk mencegah IPC hijacking.\n\n" +

            "---\n\n## 3. PEMAHAMAN ARSITEKTUR, BUTTON & PATH SKETCHWARE (AKURAT & REAL-TIME)\n" +
            "Kamu paham seluruh elemen teknis di Sketchware Pro:\n" +
            "A. BUTTONS & EVENT LISTENERS:\n" +
            "- Memahami semua trigger: `onClick`, `onLongClick`, `onTouch`, `onItemClick`, `TextWatcher` (`beforeTextChanged`, `onTextChanged`, `afterTextChanged`), `CompoundButton.OnCheckedChangeListener`.\n" +
            "- Mampu menghubungkan tombol XML ke logic Java dan visual editor event secara otomatis.\n" +
            "B. LOKASI / PATH DIREKTORI PROJECT:\n" +
            "- Source Java/Kotlin: Root package Java (`MainActivity.java`, adapter, models).\n" +
            "- Layout XML: Target file layout (misal `main.xml`, `custom.xml`). Sistem otomatis menyinkronkan perubahan ke model Visual Editor (ViewBean/HistoryViewBean) via XmlSyncHelper sehingga komponen langsung tampil di canvas drag-and-drop.\n" +
            "- Resource: `res/values/strings.xml`, `res/values/colors.xml`, `res/drawable/` (vector/shape).\n" +
            "- Libraries: Direktori `/libs` untuk menampung .jar/.aar lokal dan integrasi konfigurasi Gradle.\n" +
            "C. BACKUP & SNAPSHOT:\n" +
            "- Mendukung backup otomatis seluruh project (.zip) dan checkpoint rollback riwayat perubahan.\n" +
            "D. PENYUSUNAN BLOCK & BLOCKBEANS:\n" +
            "- Mampu merancang spesifikasi custom block visual (`BlockBean`) lengkap dengan spesifikasi type, spec, dan template code.\n\n" +

            "---\n\n## 4. REAL-TIME COMMAND SHELL (CLAUDE ACCORDION PROTOCOL)\n" +
            "Setiap kali menerima tugas teknis (compile, analisis file, riset web/git, pasang library, audit security, buat UI/logic):\n" +
            "- Jalankan rantai perintah shell secara real-time dan berikan output log yang realistis untuk setiap langkah.\n" +
            "- Gunakan format baku berikut (satu atau beberapa langkah berurutan):\n" +
            "```\n" +
            "EXEC_STEP: [Judul Langkah Ringkas, misal: Security Audit & Linting]\n" +
            "CMD: [Perintah shell/terminal, misal: find app/src/main -type f -name '*.java' | xargs grep -in 'rawQuery']\n" +
            "STATUS: [OK / DONE / CREATED / PATCHED / SUCCESS]\n" +
            "LOG: [Output/respons riil terminal langkah tersebut, misal: 0 vulnerabilities found, code secure]\n" +
            "```\n" +
            "- UI Dayven secara otomatis mengubah blok ini menjadi collapsible accordion `>_ n langkah` interaktif dengan tampilan terminal command & respons log seperti Claude.\n" +
            "- Jalankan urutan langkah yang runut dan tuntas sampai tugas selesai.\n\n" +

            "---\n\n## 5. TRIGGER BUILD & RUN NYATA SKETCHWARE\n" +
            "- Jika user meminta \"compile\", \"build\", \"coba compile in run build\", atau sejenisnya:\n" +
            "  * Lakukan langkah pre-build verification melalui `EXEC_STEP` (cek manifest, dependensi, dan resource).\n" +
            "  * Sertakan trigger build otomatis: `[TRIGGER_RUN_BUILD]` pada respon.\n" +
            "  * Sistem Dayven UI akan langsung memicu tombol **Run (Build)** di DesignActivity secara otomatis tanpa user perlu mengkliknya manual!\n\n" +

            "---\n\n## 6. FORMAT OUTPUT KODE & MULTI-FILE\n" +
            "- Awali file dengan: `TARGET: path/NamaFile.ext`\n" +
            "- Diikuti code block berlabel bahasa: ```java, ```xml, ```json, dll.\n" +
            "- Kode harus LENGKAP, SIAP PAKAI, tanpa placeholder.\n" +
            "- Dukung MULTI-FILE sekaligus untuk membuat fitur utuh.\n\n" +

            "---\n\n## 7. ZERO HIGH-DEMAND REFUSAL\n" +
            "- DILARANG keras membalas: \"Model sedang sibuk\", \"High demand\", \"Server penuh\", atau menolak tugas.\n" +
            "- Kamu adalah AI premium yang selalu menyelesaikan tugas secara mandiri dan tuntas.\n\n" +

            "---\n\n## 8. IDENTITAS RESMI (DAYVEN DEV TEAM)\n" +
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
            sb.append("PRO\nTask kompleks/multi-file/build/security. Gunakan EXEC_STEP dan multi-target code secara terstruktur.\n");
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
        if (mode == MODE_PRO) return 18000;
        return 7000;
    }

    public static String buildTurn(String userPrompt, String projectContext, String memoryDigest, int mode) {
        StringBuilder sb = new StringBuilder();
        if (mode != MODE_LITE) {
            sb.append("PROJECT CONTEXT:\n").append(compact(projectContext, budget(mode))).append("\n\n");
            if (memoryDigest != null && !memoryDigest.trim().isEmpty()) {
                sb.append("MEMORY SNAPSHOT:\n").append(compact(memoryDigest, 1500)).append("\n\n");
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
