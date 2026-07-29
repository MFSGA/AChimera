package rs.chimera.android.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import rs.chimera.android.R
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.ui.preferences.AppearancePreference
import rs.chimera.android.ui.preferences.LanguagePreference
import rs.chimera.android.ui.preferences.UiVariant

class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var languagePreference: LanguagePreference by mutableStateOf(AppPreferences.language(application))
        private set

    fun updateLanguagePreference(preference: LanguagePreference) {
        languagePreference = preference
        AppPreferences.updateLanguage(getApplication(), preference)
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

    var appearancePreference: AppearancePreference by mutableStateOf(
        AppPreferences.appearance(application),
    )
        private set

    fun updateAppearancePreference(preference: AppearancePreference) {
        appearancePreference = preference
        AppPreferences.updateAppearance(getApplication(), preference)
    }

    var uiVariant: UiVariant by mutableStateOf(AppPreferences.uiVariant(application))
        private set

    fun updateUiVariant(variant: UiVariant) {
        uiVariant = variant
        AppPreferences.updateUiVariant(getApplication(), variant)
    }

    var appFilterMode: AppFilterMode by mutableStateOf(AppFilterMode.ALL)
    var allowedApps: Set<String> by mutableStateOf(loadAppSet("allowed_apps"))
    var disallowedApps: Set<String> by mutableStateOf(loadAppSet("disallowed_apps"))

    fun updateAppFilterMode(mode: AppFilterMode) {
        appFilterMode = mode
        prefs.edit { putString("app_filter_mode", mode.name) }
    }

    fun updateAllowedApps(apps: Set<String>) {
        allowedApps = apps
        prefs.edit { putStringSet("allowed_apps", apps) }
    }

    fun updateDisallowedApps(apps: Set<String>) {
        disallowedApps = apps
        prefs.edit { putStringSet("disallowed_apps", apps) }
    }

    private fun loadAppSet(key: String): Set<String> {
        return prefs.getStringSet(key, emptySet()) ?: emptySet()
    }
}
