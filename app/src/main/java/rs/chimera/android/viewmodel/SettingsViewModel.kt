package rs.chimera.android.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import rs.chimera.android.R

enum class LanguagePreference {
    SYSTEM,
    SIMPLIFIED_CHINESE,
    ENGLISH,
}

class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var languagePreference: LanguagePreference by mutableStateOf(loadLanguagePreference())
        private set

    private fun loadLanguagePreference(): LanguagePreference {
        val value = prefs.getString("language", "SYSTEM") ?: "SYSTEM"
        return try {
            LanguagePreference.valueOf(value)
        } catch (_: IllegalArgumentException) {
            LanguagePreference.SYSTEM
        }
    }

    fun updateLanguagePreference(preference: LanguagePreference) {
        languagePreference = preference
        prefs.edit { putString("language", preference.name) }
    }

    fun getLanguageDisplayName(): String {
        val context = getApplication<Application>().applicationContext
        return when (languagePreference) {
            LanguagePreference.SYSTEM -> context.getString(R.string.language_system)
            LanguagePreference.SIMPLIFIED_CHINESE -> {
                context.getString(R.string.language_simplified_chinese)
            }
            LanguagePreference.ENGLISH -> context.getString(R.string.language_english)
        }
    }
}
