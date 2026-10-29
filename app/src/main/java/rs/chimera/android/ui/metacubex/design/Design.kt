package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import rs.chimera.android.R

abstract class Design<R>(val context: Context) {
    abstract val root: View

    val requests: Channel<R> = Channel(Channel.UNLIMITED)

    suspend fun showToast(message: CharSequence, duration: Int = Snackbar.LENGTH_SHORT) {
        withContext(Dispatchers.Main) {
            Snackbar.make(root, message, duration).show()
        }
    }
}

suspend fun Design<*>.showExceptionToast(message: CharSequence) {
    withContext(Dispatchers.Main) {
        Snackbar.make(root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.meta_error_details) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.meta_error_title)
                    .setMessage(message)
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            .show()
    }
}
