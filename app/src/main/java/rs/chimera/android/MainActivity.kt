package rs.chimera.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import rs.chimera.android.theme.ChimeraTheme
import rs.chimera.android.ui.navigation.DefaultAppUiRouter
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.ui.preferences.UiVariant
import rs.chimera.android.ui.watfaq.WatfaqAppRoot

class MainActivity : AppCompatActivity() {
    companion object {
        fun intent(context: Context) = Intent(context, MainActivity::class.java)
    }

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
        requestNotificationPermission()

        if (AppPreferences.uiVariant(this) == UiVariant.METACUBEX) {
            DefaultAppUiRouter.openMetaCubeX(this)
            finish()
            return
        }

        setContent {
            ChimeraTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    WatfaqAppRoot(
                        onSwitchUi = {
                            DefaultAppUiRouter.openMetaCubeX(this@MainActivity)
                        },
                    )
                }
            }
        }
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
