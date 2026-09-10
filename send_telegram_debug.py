import os
import glob

api_id_env = os.environ.get("API_ID", "").strip()
api_hash = os.environ.get("API_HASH", "").strip()
session_string = os.environ.get("SESSION_STRING", "").strip()
chat_id_env = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
topic_id2_env = os.environ.get("TELEGRAM_THREAD_ID2", "").strip()

if not (api_id_env and api_hash and session_string and chat_id_env and topic_id2_env):
    print("Telegram secrets belum diset atau kosong. Melewati pengiriman ke Telegram.")
    exit(0)

try:
    from pyrogram import Client

    api_id = int(api_id_env)
    chat_id = int(chat_id_env)
    topic_id2 = int(topic_id2_env)

    version = os.environ.get("VERSION", "1")
    author = os.environ.get("COMMIT_AUTHOR", "Unknown")
    commit_msg = os.environ.get("COMMIT_MESSAGE", "-").strip()
    full_sha = os.environ.get("COMMIT_SHA", "")
    short_sha = f"#{full_sha[:7]}" if full_sha else "#unknown"

    all_apks = glob.glob("app/build/outputs/apk/**/debug/*.apk", recursive=True)

    apk_v33 = None
    for apk in all_apks:
        if "android33" in os.path.basename(apk):
            apk_v33 = apk
            break

    if not apk_v33:
        print("APK android33 tidak ditemukan, tidak ada yang dikirim.")
        exit(0)

    display_name = "app-debug33.apk"
    caption = (
        f"📱 **Debug Build APK (API 33)**\n\n"
        f"🚀 **New Debug Build ({version})**\n\n"
        f"**Commit by:** {author}\n"
        f"**Commit message:** {commit_msg}\n"
        f"**Commit hash:** {short_sha}"
    )

    app = Client(
        "userbot_session",
        api_id=api_id,
        api_hash=api_hash,
        session_string=session_string,
        in_memory=True
    )

    with app:
        print(f"Mengirim APK debug android33 ke topic {topic_id2}: {apk_v33}")
        app.send_document(
            chat_id=chat_id,
            document=apk_v33,
            file_name=display_name,
            caption=caption,
            message_thread_id=topic_id2
        )

    print("APK debug android33 berhasil diunggah ke topik Telegram!")
except Exception as e:
    print(f"Gagal mengirim Telegram: {e}")
    exit(0)
