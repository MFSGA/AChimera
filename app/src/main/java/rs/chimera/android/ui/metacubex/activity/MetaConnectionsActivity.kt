package rs.chimera.android.ui.metacubex.activity

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.databinding.MetaDesignConnectionsBinding
import rs.chimera.android.formatSize
import rs.chimera.android.ui.metacubex.adapter.ConnectionsAdapter
import rs.chimera.android.viewmodel.ConnectionsViewModel

class MetaConnectionsActivity : AppCompatActivity() {
    private val viewModel: ConnectionsViewModel by viewModels()
    private lateinit var binding: MetaDesignConnectionsBinding
    private val adapter = ConnectionsAdapter { viewModel.closeConnection(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MetaDesignConnectionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.connectionList.layoutManager = LinearLayoutManager(this)
        binding.connectionList.adapter = adapter
        binding.closeAll.setOnClickListener { confirmCloseAll() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.startPolling()
                try {
                    launch {
                        snapshotFlow {
                            NativeState(
                                connections = viewModel.connections,
                                downloadTotal = viewModel.downloadTotal,
                                uploadTotal = viewModel.uploadTotal,
                                errorMessage = viewModel.errorMessage,
                                closingIds = viewModel.closingConnectionIds,
                                closeAllInProgress = viewModel.closeAllInProgress,
                            )
                        }.distinctUntilChanged().collect(::render)
                    }
                    awaitCancellation()
                } finally {
                    viewModel.stopPolling()
                }
            }
        }
    }

    private fun render(state: NativeState) {
        adapter.submitConnections(state.connections, state.closingIds)
        binding.connectionCount.text = getString(R.string.connections_count) + ": ${state.connections.size}"
        binding.downloadTotal.text = getString(R.string.connections_download, formatSize(state.downloadTotal))
        binding.uploadTotal.text = getString(R.string.connections_upload, formatSize(state.uploadTotal))
        binding.errorMessage.apply {
            text = state.errorMessage.orEmpty()
            visibility = if (state.errorMessage == null) View.GONE else View.VISIBLE
        }
        binding.emptyView.visibility = if (state.connections.isEmpty() && state.errorMessage == null) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.closeAll.isEnabled =
            state.connections.isNotEmpty() &&
                !state.closeAllInProgress &&
                state.closingIds.isEmpty()
    }

    private fun confirmCloseAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.connections_close_all_title)
            .setMessage(R.string.connections_close_all_message)
            .setPositiveButton(R.string.connections_close_all) { _, _ -> viewModel.closeAllConnections() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private data class NativeState(
        val connections: List<rs.chimera.android.backend.model.ConnectionSnapshot>,
        val downloadTotal: Long,
        val uploadTotal: Long,
        val errorMessage: String?,
        val closingIds: Set<String>,
        val closeAllInProgress: Boolean,
    )
}
