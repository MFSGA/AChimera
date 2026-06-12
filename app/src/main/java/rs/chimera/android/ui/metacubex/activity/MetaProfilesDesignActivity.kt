package rs.chimera.android.ui.metacubex.activity

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.ui.metacubex.design.ProfilesDesign

class MetaProfilesDesignActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: ProfilesDesign

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch { importLocalProfile(it) }
        }
    }

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
                showProfileMenu(request.profileId)
            }
            ProfilesDesign.Request.AddProfile -> {
                showAddDialog()
            }
            ProfilesDesign.Request.RefreshProfile -> {
                refreshProfiles()
            }
        }
    }

    private suspend fun showProfileMenu(profileId: String) {
        val profiles = backend.listProfiles()
        val profile = profiles.firstOrNull { it.id == profileId } ?: return
        val items = mutableListOf<String>().apply {
            add(getString(R.string.profile_update))
            add(getString(R.string.profile_rename))
            add(getString(R.string.profile_delete))
        }

        withContext(Dispatchers.Main) {
            AlertDialog.Builder(this@MetaProfilesDesignActivity)
                .setTitle(profile.name)
                .setItems(items.toTypedArray()) { _, which ->
                    lifecycleScope.launch {
                        when (which) {
                            0 -> runCatching { backend.updateRemoteProfile(profile.id) }
                                .onSuccess { design.showToast("Updated") }
                                .onFailure { design.showToast(it.message ?: "Update failed") }
                            1 -> showRenameDialog(profile.id, profile.name)
                            2 -> confirmDelete(profile.id, profile.name)
                        }
                    }
                }
                .show()
        }
    }

    private suspend fun showRenameDialog(profileId: String, currentName: String) {
        withContext(Dispatchers.Main) {
            val input = EditText(this@MetaProfilesDesignActivity).apply { setText(currentName) }
            AlertDialog.Builder(this@MetaProfilesDesignActivity)
                .setTitle(R.string.profile_rename_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotBlank()) {
                        lifecycleScope.launch {
                            runCatching { backend.renameProfile(profileId, newName) }
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private suspend fun confirmDelete(profileId: String, name: String) {
        withContext(Dispatchers.Main) {
            AlertDialog.Builder(this@MetaProfilesDesignActivity)
                .setTitle("$name")
                .setMessage(getString(R.string.profile_delete_confirm))
                .setPositiveButton(R.string.profile_delete) { _, _ ->
                    lifecycleScope.launch {
                        runCatching { backend.deleteProfile(profileId) }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showAddDialog() {
        val options = arrayOf(
            getString(R.string.profile_import_url),
            getString(R.string.profile_import_file),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_import)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showUrlImportDialog()
                    1 -> filePickerLauncher.launch("*/*")
                }
            }
            .show()
    }

    private fun showUrlImportDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_import_url)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotBlank()) {
                    lifecycleScope.launch {
                        runCatching {
                            backend.importRemoteProfile(RemoteProfileRequest(null, url))
                        }.onSuccess {
                            design.showToast("Import queued")
                        }.onFailure {
                            design.showToast(it.message ?: "Import failed")
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private suspend fun importLocalProfile(uri: Uri) {
        runCatching { backend.importLocalProfile(uri, null) }
            .onSuccess { design.showToast("Imported") }
            .onFailure { design.showToast(it.message ?: "Import failed") }
    }

    private suspend fun refreshProfiles() {
        val profiles = backend.listProfiles()
        for (p in profiles.filter { it.isRemote }) {
            runCatching { backend.updateRemoteProfile(p.id) }
        }
    }
}
