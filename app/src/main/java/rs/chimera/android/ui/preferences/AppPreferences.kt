package rs.chimera.android.ui.preferences

import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat

enum class UiVariant {
    WATFAQ,
    METACUBEX,
}

enum class LanguagePreference {
    SYSTEM,
    SIMPLIFIED_CHINESE,
    ENGLISH,
}

enum class AppearancePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

object AppPreferences {
    private const val PREFS_NAME = "settings"
    private const val KEY_UI_VARIANT = "ui_variant"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_APPEARANCE = "appearance"
    private const val KEY_AUTO_RESTART = "auto_restart"
    private const val KEY_HIDE_APP_ICON = "hide_app_icon"
    private const val KEY_HIDE_FROM_RECENTS = "hide_from_recents"
    private const val MAIN_ACTIVITY_ALIAS = "rs.chimera.android.MainActivityAlias"

    fun apply(context: Context) {
        applyLanguage(language(context))
        applyAppearance(appearance(context))
    }

    fun uiVariant(context: Context): UiVariant =
        enumPreference(context, KEY_UI_VARIANT, UiVariant.WATFAQ)

    fun language(context: Context): LanguagePreference =
        enumPreference(context, KEY_LANGUAGE, LanguagePreference.SYSTEM)

    fun appearance(context: Context): AppearancePreference =
        enumPreference(context, KEY_APPEARANCE, AppearancePreference.SYSTEM)

    fun autoRestart(context: Context): Boolean =
        preferences(context).getBoolean(KEY_AUTO_RESTART, true)

    fun hideFromRecents(context: Context): Boolean =
        preferences(context).getBoolean(KEY_HIDE_FROM_RECENTS, false)

    fun hideAppIcon(context: Context): Boolean = preferences(context).getBoolean(
        KEY_HIDE_APP_ICON,
        context.packageManager.getComponentEnabledSetting(mainActivityAlias(context)).let { state ->
            state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
                state != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        },
    )

    fun updateUiVariant(context: Context, variant: UiVariant) {
        putEnum(context, KEY_UI_VARIANT, variant)
    }

    fun updateLanguage(context: Context, preference: LanguagePreference) {
        putEnum(context, KEY_LANGUAGE, preference)
        applyLanguage(preference)
    }

    fun updateAppearance(context: Context, preference: AppearancePreference) {
        putEnum(context, KEY_APPEARANCE, preference)
        applyAppearance(preference)
    }

    fun updateAutoRestart(context: Context, enabled: Boolean) {
        preferences(context).edit { putBoolean(KEY_AUTO_RESTART, enabled) }
    }

    fun updateHideFromRecents(context: Context, enabled: Boolean) {
        preferences(context).edit { putBoolean(KEY_HIDE_FROM_RECENTS, enabled) }
    }

    fun updateHideAppIcon(context: Context, hidden: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            mainActivityAlias(context),
            if (hidden) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            },
            PackageManager.DONT_KILL_APP,
        )
        preferences(context).edit { putBoolean(KEY_HIDE_APP_ICON, hidden) }
    }

    private fun applyLanguage(preference: LanguagePreference) {
        val locales = when (preference) {
            LanguagePreference.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            LanguagePreference.SIMPLIFIED_CHINESE -> LocaleListCompat.forLanguageTags("zh-CN")
            LanguagePreference.ENGLISH -> LocaleListCompat.forLanguageTags("en")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun applyAppearance(preference: AppearancePreference) {
        val mode = when (preference) {
            AppearancePreference.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppearancePreference.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppearancePreference.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private inline fun <reified T : Enum<T>> enumPreference(
        context: Context,
        key: String,
        fallback: T,
    ): T {
        val value = preferences(context).getString(key, fallback.name) ?: fallback.name
        return enumValues<T>().firstOrNull { it.name == value } ?: fallback
    }

    private fun putEnum(context: Context, key: String, value: Enum<*>) {
        preferences(context).edit { putString(key, value.name) }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun mainActivityAlias(context: Context) =
        ComponentName(context, MAIN_ACTIVITY_ALIAS)
}
