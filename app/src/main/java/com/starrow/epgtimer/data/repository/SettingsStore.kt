package com.starrow.epgtimer.data.repository

import android.content.Context

interface SettingsStore {
    fun getString(key: String, default: String): String?
    fun getInt(key: String, default: Int): Int
    fun getBoolean(key: String, default: Boolean): Boolean
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun apply()
    }
}

class SharedPreferencesStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("epg_timer_settings", Context.MODE_PRIVATE)

    override fun getString(key: String, default: String): String? = prefs.getString(key, default)

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun edit(): SettingsStore.Editor {
        val editor = prefs.edit()
        return object : SettingsStore.Editor {
            override fun putString(key: String, value: String?): SettingsStore.Editor = apply { editor.putString(key, value) }

            override fun putInt(key: String, value: Int): SettingsStore.Editor = apply { editor.putInt(key, value) }

            override fun putBoolean(key: String, value: Boolean): SettingsStore.Editor = apply { editor.putBoolean(key, value) }

            override fun apply() = editor.apply()
        }
    }
}

class InMemorySettingsStore(
    private val values: MutableMap<String, Any> = mutableMapOf(),
) : SettingsStore {
    override fun getString(key: String, default: String): String? = values[key] as? String ?: default

    override fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default

    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default

    override fun edit(): SettingsStore.Editor {
        val pending = mutableMapOf<String, Any>()
        val editor = object : SettingsStore.Editor {
            override fun putString(key: String, value: String?): SettingsStore.Editor = apply {
                if (value == null) values.remove(key) else pending[key] = value
            }

            override fun putInt(key: String, value: Int): SettingsStore.Editor = apply { pending[key] = value }

            override fun putBoolean(key: String, value: Boolean): SettingsStore.Editor = apply { pending[key] = value }

            override fun apply() {
                values.putAll(pending)
            }
        }
        return editor
    }
}
