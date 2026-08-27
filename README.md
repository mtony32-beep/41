# rerev7 - Mobile AI Studio & Android IDE

rerev7 adalah aplikasi Android all-in-one untuk coding, AI vibe coding, integrasi GitHub, eksekusi Termux CLI, Cloud Build via GitHub Actions, Auto-Fix Service otomatis dengan Gemini, Postman API Tester, dan SQLite Database Browser langsung di smartphone tanpa perlu PC.

## 🚀 Fitur Utama
1. **Chat AI (Gemini Flash)**: Streaming response, Dropdown file aktif, "Perbaiki File Ini", Lampiran Gambar, Voice Microphone Vibe Coding.
2. **Editor Kode**: Syntax highlighting Kotlin/XML/Gradle/Java, Line numbers, Undo/Redo, Search/Replace, AI Code Completion bar, Generate Test.
3. **GitHub Integration**: Login token, Clone, Commit with AI Message (feat/fix/refactor), Push, Pull, Branch switcher, PR & Actions viewer.
4. **Terminal CLI**: Terminal built-in dengan shortcut keyboard coding (`|`, `/`, `-`, `~`, `Tab`, `Ctrl`, `Esc`, dll).
5. **Cloud Build**: Trigger `workflow_dispatch` ke GitHub Actions, Antrian Build Offline, Download APK langsung dari artifacts.
6. **Auto-Fix Service**: Periodic WorkManager pemantau Actions failure -> parsing error log -> perbaikan otomatis oleh Gemini -> auto commit & trigger rebuild.
7. **Database Viewer**: Jelajahi database SQLite/Room .db, tabel, skema, dan jalankan custom SQL queries.
8. **API Tester (Postman Mobile)**: URL, Method, Custom Headers, JSON Request Body, Formatter JSON Response, Status & Latency counter.
9. **Bug Hunter & AI Code Reviewer**: Pemindaian statis pattern berbahaya di file Kotlin & ulasan kode otomatis dari Gemini.
10. **Security**: EncryptedSharedPreferences untuk menyimpan API Keys & Token terenkripsi, autentikasi Biometrik.

## 🛠️ Build di GitHub (CI / CD)

Repositori ini sudah terintegrasi dengan **GitHub Actions** (`.github/workflows/android.yml`):
- **Otomatis**: Setiap `push` atau `pull_request` ke branch `main`/`master`, serta opsi manual **Run workflow** (`workflow_dispatch`).
- **Java SDK**: Menggunakan OpenJDK 17 (Temurin) dengan caching dependensi Gradle otomatis.
- **Keystore & Env**: Workflow otomatis menyiapkan `.env` dari `.env.example` dan mengekstrak `debug.keystore` dari `debug.keystore.base64`.
- **Hasil Build (Artifacts)**: File APK Debug (`rerev7-debug-apk`) otomatis di-compile dan siap diunduh langsung dari tab **Actions** di GitHub.

### Build Lokal via Terminal / CLI
```bash
# Berikan izin eksekusi gradle wrapper
chmod +x gradlew

# Kompilasi dan buat Debug APK
./gradlew assembleDebug

# Jalankan unit test
./gradlew testDebugUnitTest
```

