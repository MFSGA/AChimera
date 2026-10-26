package rs.chimera.android.ui.onboarding

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import rs.chimera.android.MainActivity
import rs.chimera.android.theme.ChimeraTheme

class OnboardingActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_FIRST_RUN = "rs.chimera.android.onboarding.first_run"

        fun intent(context: Context, firstRun: Boolean = false): Intent =
            Intent(context, OnboardingActivity::class.java)
                .putExtra(EXTRA_FIRST_RUN, firstRun)
    }

    private val onboardingViewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lightSystemBars = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK !=
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightSystemBars
            isAppearanceLightNavigationBars = lightSystemBars
        }
        val firstRun = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)
        onboardingViewModel.initialize(firstRun)
        setContent {
            ChimeraTheme {
                OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onFinish = ::finishOnboarding,
                )
            }
        }
    }

    private fun finishOnboarding() {
        onboardingViewModel.finishGuide()
        val mainIntent = MainActivity.intent(this).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(mainIntent)
        finish()
    }
}
