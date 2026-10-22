package rs.chimera.android.ui.metacubex.design.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import rs.chimera.android.R

class MetaBottomSheetDialog(context: Context) : BottomSheetDialog(context) {
    private var navigationInsetScrim: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dialogWindow = window ?: return
        WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
        dialogWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogWindow.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            dialogWindow.navigationBarColor = Color.TRANSPARENT
            dialogWindow.isStatusBarContrastEnforced = false
            dialogWindow.isNavigationBarContrastEnforced = false
            dialogWindow.decorView.isForceDarkAllowed = false
        } else {
            dialogWindow.navigationBarColor = context.getColor(R.color.metacubex_navigation_overlay)
        }

        findViewById<ViewGroup>(com.google.android.material.R.id.container)?.fitsSystemWindows = false
        val sheet = findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        ViewCompat.setBackgroundTintList(sheet, null)
        sheet.background = ContextCompat.getDrawable(
            sheet.context,
            R.drawable.meta_profile_bottom_sheet_background,
        )
        val surfaceColor = MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorSurface)
        val isLightSurface = ColorUtils.calculateLuminance(surfaceColor) > 0.5
        WindowInsetsControllerCompat(dialogWindow, dialogWindow.decorView).apply {
            isAppearanceLightStatusBars = isLightSurface
            isAppearanceLightNavigationBars = false
        }

        ViewCompat.setOnApplyWindowInsetsListener(sheet) { view, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val bottomInset = safeInsets.bottom
            val params = view.layoutParams as CoordinatorLayout.LayoutParams
            params.setMarginStart(safeInsets.left)
            params.setMarginEnd(safeInsets.right)
            params.topMargin = 0
            params.bottomMargin = 0
            view.layoutParams = params
            view.background = InsetDrawable(
                ContextCompat.getDrawable(
                    view.context,
                    R.drawable.meta_profile_bottom_sheet_background,
                ),
                0,
                0,
                0,
                bottomInset,
            )
            view.setPaddingRelative(
                0,
                2 * context.resources.getDimensionPixelSize(R.dimen.metacubex_bottom_sheet_top_padding) +
                    context.resources.getDimensionPixelSize(R.dimen.metacubex_bottom_sheet_handle_height),
                0,
                bottomInset,
            )
            updateNavigationInsetScrim(view, bottomInset)
            insets
        }
        ViewCompat.requestApplyInsets(sheet)

        setOnShowListener {
            behavior.halfExpandedRatio = 0.99f
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun updateNavigationInsetScrim(sheet: View, bottomInset: Int) {
        val parent = sheet.parent as? CoordinatorLayout ?: return
        val scrim = navigationInsetScrim ?: View(context).apply {
            background = ColorDrawable(Color.argb(0x99, 0, 0, 0))
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            elevation = ViewCompat.getElevation(sheet) + 1f
        }.also { navigationInsetScrim = it }

        if (scrim.parent == null) {
            parent.addView(
                scrim,
                CoordinatorLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    bottomInset,
                ).apply { gravity = Gravity.BOTTOM },
            )
        } else {
            scrim.layoutParams = (scrim.layoutParams as CoordinatorLayout.LayoutParams).apply {
                height = bottomInset
                gravity = Gravity.BOTTOM
            }
        }
        scrim.visibility = if (bottomInset > 0) View.VISIBLE else View.GONE
        scrim.bringToFront()
    }
}
