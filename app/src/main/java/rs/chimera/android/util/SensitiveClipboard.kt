package rs.chimera.android.util

import android.content.ClipData
import android.os.Build
import android.os.PersistableBundle

internal object SensitiveClipboard {
    fun plainText(label: CharSequence, text: CharSequence): ClipData =
        ClipData.newPlainText(label, text).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                description.extras = PersistableBundle().apply {
                    putBoolean(SENSITIVE_CLIP_EXTRA, true)
                }
            }
        }

    private const val SENSITIVE_CLIP_EXTRA = "android.content.extra.IS_SENSITIVE"
}
