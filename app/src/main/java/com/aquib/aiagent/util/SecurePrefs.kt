package com.aquib.aiagent.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePrefs(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ai_agent_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun getString(key: String, def: String = ""): String = prefs.getString(key, def) ?: def
}
