package rs.chimera.android.ui.metacubex.design

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProfileFileMetadata
import rs.chimera.android.backend.model.ProfileProviderFileMetadata
import rs.chimera.android.ui.metacubex.adapter.MetaProfileFileAdapter
import rs.chimera.android.ui.metacubex.adapter.MetaProfileFileEntry
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

internal class MetaProfileFilesDesign(
    context: Context,
    metadata: ProfileFileMetadata,
) : Design<MetaProfileFilesDesign.Request>(context) {
    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_profile_files,
        context.root,
        false,
    )

    private val activityBar = root.findViewById<View>(R.id.profile_files_activity_bar)
    private val recycler = root.findViewById<RecyclerView>(R.id.profile_files_recycler)
    private var activityBarElevated = false
    private var activityBarElevationAnimator: ValueAnimator? = null
    private val adapter = MetaProfileFileAdapter { entry ->
        when (entry.action) {
            MetaProfileFileEntry.Action.OPEN_CONFIGURATION -> requests.trySend(Request.OpenConfiguration)
            MetaProfileFileEntry.Action.OPEN_DIRECTORY ->
                requests.trySend(Request.OpenProviderDirectory(entry.relativePath))
            MetaProfileFileEntry.Action.NONE -> Unit
        }
    }

    init {
        root.findViewById<View>(R.id.profile_files_back).setOnClickListener {
            requests.trySend(Request.NavigateBack)
        }
        showProfileRoot(metadata)
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateActivityBarElevation()
                }
            },
        )
        recycler.post { updateActivityBarElevation() }
        applySystemBarInsets()
    }

    fun showProfileRoot(metadata: ProfileFileMetadata) {
        adapter.submitList(
            listOf(
                MetaProfileFileEntry(
                    name = context.getString(R.string.profile_files_configuration) +
                        metadata.extension?.let { ".$it" }.orEmpty(),
                    size = metadata.size,
                    modifiedAt = metadata.lastModified,
                    action = MetaProfileFileEntry.Action.OPEN_CONFIGURATION,
                ),
                MetaProfileFileEntry(
                    name = context.getString(R.string.profile_files_providers),
                    size = 0L,
                    modifiedAt = null,
                    isDirectory = true,
                    action = MetaProfileFileEntry.Action.OPEN_DIRECTORY,
                ),
            ),
        )
        recycler.post { updateActivityBarElevation() }
    }

    fun showProviderDirectory(files: List<ProfileProviderFileMetadata>) {
        adapter.submitList(
            files.map { file ->
                MetaProfileFileEntry(
                    name = file.name,
                    size = file.size,
                    modifiedAt = file.lastModified,
                    isDirectory = file.isDirectory,
                    relativePath = file.relativePath,
                    action = if (file.isDirectory) {
                        MetaProfileFileEntry.Action.OPEN_DIRECTORY
                    } else {
                        MetaProfileFileEntry.Action.NONE
                    },
                )
            },
        )
        recycler.post { updateActivityBarElevation() }
    }

    private fun updateActivityBarElevation() {
        val elevated = recycler.canScrollVertically(-1)
        if (activityBarElevated == elevated) return

        activityBarElevated = elevated
        activityBarElevationAnimator?.cancel()
        val targetElevation = if (elevated) {
            context.resources.getDimension(R.dimen.metacubex_tab_layout_elevation)
        } else {
            0f
        }
        activityBarElevationAnimator = ValueAnimator.ofFloat(activityBar.elevation, targetElevation).apply {
            duration = 300L
            addUpdateListener { activityBar.elevation = it.animatedValue as Float }
            start()
        }
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val horizontalInsets = view.landscapeSystemBarInsets(bars)
            view.setPaddingRelative(horizontalInsets.start, 0, horizontalInsets.end, 0)
            activityBar.setPadding(0, bars.top, 0, 0)
            recycler.setPaddingRelative(
                0,
                bars.top + context.resources.getDimensionPixelSize(R.dimen.metacubex_toolbar_height),
                0,
                bars.bottom,
            )
            insets
        }
        root.doOnAttach { ViewCompat.requestApplyInsets(it) }
    }

    sealed class Request {
        data object OpenConfiguration : Request()
        data class OpenProviderDirectory(val path: List<String>) : Request()
        data object NavigateBack : Request()
    }
}
