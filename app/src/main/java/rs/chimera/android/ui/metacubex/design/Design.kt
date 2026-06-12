package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

abstract class Design<R>(val context: Context) :
    CoroutineScope by CoroutineScope(Dispatchers.Unconfined) {
    abstract val root: View

    val requests: Channel<R> = Channel(Channel.UNLIMITED)

    suspend fun showToast(message: CharSequence, duration: Int = Snackbar.LENGTH_SHORT) {
        withContext(Dispatchers.Main) {
            Snackbar.make(root, message, duration).show()
        }
    }
}
