package rs.chimera.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import rs.chimera.android.theme.ChimeraTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.i("chimera", "Notification permission granted")
        } else {
            android.util.Log.i("chimera", "Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLanguagePreference()
        requestNotificationPermission()
        setContent {
            ChimeraTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ChimeraAppRoot()
                }
            }
        }
    }

    private fun applyLanguagePreference() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val languagePreference = prefs.getString("language", "SYSTEM") ?: "SYSTEM"

        val locale = when (languagePreference) {
            "SIMPLIFIED_CHINESE" -> Locale.SIMPLIFIED_CHINESE
            "ENGLISH" -> Locale.ENGLISH
            else -> return
        }

        val config = Configuration(resources.configuration)
        config.setLocale(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            createConfigurationContext(config)
        }

        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
