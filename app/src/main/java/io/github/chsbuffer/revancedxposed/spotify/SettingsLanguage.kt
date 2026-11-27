package io.github.chsbuffer.revancedxposed.spotify

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import io.github.chsbuffer.revancedxposed.PREF_SETTINGS_LANGUAGE
import java.util.Locale

internal enum class SettingsLanguage(val tag: String, val displayName: String) {
    ENGLISH("en", "English"),
    SIMPLIFIED_CHINESE("zh-Hans", "简体中文"),
    TRADITIONAL_CHINESE("zh-Hant", "正體中文");

    fun applyTo(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(configuration)
    }

    companion object {
        fun read(prefs: SharedPreferences): SettingsLanguage {
            val system = Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
            return resolve(prefs.getString(PREF_SETTINGS_LANGUAGE, null), system)
        }

        private fun resolve(tag: String?, system: Locale): SettingsLanguage {
            entries.firstOrNull { it.tag == tag }?.let { return it }
            if (system.language != "zh") return ENGLISH
            return if (system.script == "Hant" ||
                (system.script.isEmpty() && system.country in setOf("TW", "HK", "MO"))
            ) TRADITIONAL_CHINESE else SIMPLIFIED_CHINESE
        }
    }
}
