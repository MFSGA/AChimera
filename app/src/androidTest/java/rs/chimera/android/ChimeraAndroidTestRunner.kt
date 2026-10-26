package rs.chimera.android

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import rs.chimera.android.ui.onboarding.FirstRunOnboardingStore

class ChimeraAndroidTestRunner : AndroidJUnitRunner() {
    private var originalOnboardingPreferences: Map<String, *>? = null

    override fun onStart() {
        val preferences = onboardingPreferences()
        originalOnboardingPreferences = preferences.all.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }
        FirstRunOnboardingStore.markCompleted(targetContext)
        super.onStart()
    }

    override fun finish(resultCode: Int, results: Bundle) {
        restoreOnboardingPreferences()
        super.finish(resultCode, results)
    }

    private fun restoreOnboardingPreferences() {
        val original = originalOnboardingPreferences ?: return
        val editor = onboardingPreferences().edit().clear()
        original.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        check(editor.commit()) { "Failed to restore first-run onboarding preferences after instrumentation tests" }
    }

    private fun onboardingPreferences(): SharedPreferences =
        targetContext.getSharedPreferences(ONBOARDING_PREFERENCES, Context.MODE_PRIVATE)

    private companion object {
        const val ONBOARDING_PREFERENCES = "first_run_onboarding"
    }
}
