package rs.chimera.android.backend

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object DiagnosticsShareCoordinator {
    suspend fun prepare(
        context: Context,
        backend: ChimeraBackend,
    ): Intent = withContext(Dispatchers.IO) {
        val application = context.applicationContext
        val generatedAt = System.currentTimeMillis()
        val bundle = backend.buildDiagnosticsBundle()
        val file = DiagnosticsBundleStore.write(
            cacheDir = application.cacheDir,
            content = bundle,
            generatedAtEpochMillis = generatedAt,
        )
        DiagnosticsShareIntentFactory.create(application, file)
    }
}
