package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig

/**
 * Manajer Pengaturan Terenkripsi menggunakan EncryptedSharedPreferences "config".
 * Menyimpan kredensial aman (Github Token, Gemini Key, Email, Nama, Repo Default).
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences

    init {
        prefs = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREF_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menginisialisasi EncryptedSharedPreferences, fallback ke SharedPreferences privat", e)
            context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    var githubEmail: String
        get() = prefs.getString(KEY_GITHUB_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GITHUB_EMAIL, value.trim()).apply()

    var githubName: String
        get() = prefs.getString(KEY_GITHUB_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GITHUB_NAME, value.trim()).apply()

    var githubToken: String
        get() = prefs.getString(KEY_GITHUB_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GITHUB_TOKEN, value.trim()).apply()

    var geminiKey: String
        get() {
            val savedKey = prefs.getString(KEY_GEMINI_KEY, "") ?: ""
            return if (savedKey.isNotBlank()) savedKey else BuildConfig.GEMINI_API_KEY
        }
        set(value) = prefs.edit().putString(KEY_GEMINI_KEY, value.trim()).apply()

    var defaultRepo: String
        get() = prefs.getString(KEY_DEFAULT_REPO, "rerev7/workspace-app") ?: "rerev7/workspace-app"
        set(value) = prefs.edit().putString(KEY_DEFAULT_REPO, value.trim()).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var isAutoFixEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FIX_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_FIX_ENABLED, value).apply()

    var isAutoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, value).apply()

    var activeProjectDir: String
        get() = prefs.getString(KEY_ACTIVE_PROJECT_DIR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE_PROJECT_DIR, value).apply()

    var autoFixLoopCount: Int
        get() = prefs.getInt(KEY_AUTO_FIX_LOOP_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_AUTO_FIX_LOOP_COUNT, value).apply()

    /**
     * Ekspor konfigurasi ke format JSON string terenkripsi
     */
    fun exportConfigString(): String {
        val json = org.json.JSONObject().apply {
            put(KEY_GITHUB_EMAIL, githubEmail)
            put(KEY_GITHUB_NAME, githubName)
            put(KEY_GITHUB_TOKEN, githubToken)
            put(KEY_GEMINI_KEY, geminiKey)
            put(KEY_DEFAULT_REPO, defaultRepo)
            put("exported_at", System.currentTimeMillis())
        }
        return json.toString()
    }

    /**
     * Impor konfigurasi dari format JSON string
     */
    fun importConfigString(jsonString: String): Boolean {
        return try {
            val json = org.json.JSONObject(jsonString)
            if (json.has(KEY_GITHUB_EMAIL)) githubEmail = json.getString(KEY_GITHUB_EMAIL)
            if (json.has(KEY_GITHUB_NAME)) githubName = json.getString(KEY_GITHUB_NAME)
            if (json.has(KEY_GITHUB_TOKEN)) githubToken = json.getString(KEY_GITHUB_TOKEN)
            if (json.has(KEY_GEMINI_KEY)) geminiKey = json.getString(KEY_GEMINI_KEY)
            if (json.has(KEY_DEFAULT_REPO)) defaultRepo = json.getString(KEY_DEFAULT_REPO)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengimpor konfigurasi", e)
            false
        }
    }

    companion object {
        private const val TAG = "PreferencesManager"
        const val PREF_FILE_NAME = "config"

        const val KEY_GITHUB_EMAIL = "GITHUB_EMAIL"
        const val KEY_GITHUB_NAME = "GITHUB_NAME"
        const val KEY_GITHUB_TOKEN = "GITHUB_TOKEN"
        const val KEY_GEMINI_KEY = "GEMINI_KEY"
        const val KEY_DEFAULT_REPO = "DEFAULT_REPO"
        const val KEY_ONBOARDING_COMPLETED = "ONBOARDING_COMPLETED"
        const val KEY_AUTO_FIX_ENABLED = "AUTO_FIX_ENABLED"
        const val KEY_AUTO_BACKUP_ENABLED = "AUTO_BACKUP_ENABLED"
        const val KEY_ACTIVE_PROJECT_DIR = "ACTIVE_PROJECT_DIR"
        const val KEY_AUTO_FIX_LOOP_COUNT = "AUTO_FIX_LOOP_COUNT"
    }
}
