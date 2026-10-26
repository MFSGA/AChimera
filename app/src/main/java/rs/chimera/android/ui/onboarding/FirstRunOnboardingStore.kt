package rs.chimera.android.ui.onboarding

import android.content.Context
import androidx.core.content.edit
import rs.chimera.android.ui.preferences.UiVariant

internal object FirstRunOnboardingStore {
    private const val PREFS_NAME = "first_run_onboarding"
    private const val KEY_COMPLETED = "completed"
    private const val KEY_STEP = "step"
    private const val KEY_SELECTED_UI = "selected_ui"

    fun shouldStartFirstRun(context: Context): Boolean {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull() ?: return false
        return OnboardingPolicy.shouldStartFirstRun(
            completed = preferences(context).getBoolean(KEY_COMPLETED, false),
            firstInstallTime = packageInfo.firstInstallTime,
            lastUpdateTime = packageInfo.lastUpdateTime,
        )
    }

    fun step(context: Context): OnboardingStep =
        preferences(context).getString(KEY_STEP, null)
            ?.let { value -> OnboardingStep.entries.firstOrNull { it.name == value } }
            ?: OnboardingStep.WELCOME

    fun selectedUi(context: Context): UiVariant? =
        preferences(context).getString(KEY_SELECTED_UI, null)
            ?.let { value -> UiVariant.entries.firstOrNull { it.name == value } }

    fun saveStep(context: Context, step: OnboardingStep) {
        preferences(context).edit { putString(KEY_STEP, step.name) }
    }

    fun saveSelectedUi(context: Context, variant: UiVariant) {
        preferences(context).edit { putString(KEY_SELECTED_UI, variant.name) }
    }

    fun markCompleted(context: Context) {
        preferences(context).edit {
            putBoolean(KEY_COMPLETED, true)
            putString(KEY_STEP, OnboardingStep.COMPLETE.name)
        }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
