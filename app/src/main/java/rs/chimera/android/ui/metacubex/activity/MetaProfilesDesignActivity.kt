package rs.chimera.android.ui.metacubex.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.ui.metacubex.design.ProfilesDesign

class MetaProfilesDesignActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: ProfilesDesign

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        design = ProfilesDesign(this)
        setContentView(design.root)

        lifecycleScope.launch(Dispatchers.Default) {
            for (request in design.requests) {
                handleRequest(request)
            }
        }

        observeProfiles()
    }

    private fun observeProfiles() {
        lifecycleScope.launch(Dispatchers.Default) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val profiles = backend.listProfiles()
                    withContext(Dispatchers.Main) {
                        design.submitList(profiles)
                    }
                    kotlinx.coroutines.delay(3000)
                }
            }
        }
    }

    private suspend fun handleRequest(request: ProfilesDesign.Request) {
        when (request) {
            is ProfilesDesign.Request.SelectProfile -> {
                runCatching { backend.activateProfile(request.profileId) }
            }
            is ProfilesDesign.Request.ProfileMenu -> {
                withContext(Dispatchers.Main) {
                    design.showToast("Menu for ${request.profileId}")
                }
            }
            ProfilesDesign.Request.AddProfile -> {
                withContext(Dispatchers.Main) {
                    design.showToast("Add profile not yet implemented")
                }
            }
            ProfilesDesign.Request.RefreshProfile -> {
                withContext(Dispatchers.Main) {
                    design.showToast("Refresh not yet implemented")
                }
            }
        }
    }
}
