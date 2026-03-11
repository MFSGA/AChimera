package rs.chimera.android.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import rs.chimera.android.R
import rs.chimera.android.Global
import java.io.File

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var savedFilePath by remember {
        mutableStateOf(Global.profilePath)
    }
    var selectedFile by remember { mutableStateOf<Uri?>(null) }
    var statusMessage by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        selectedFile = it
        statusMessage = it?.let { uri ->
            context.getString(R.string.profile_import_ready, queryDisplayName(context, uri))
        } ?: ""
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Button(onClick = { launcher.launch(arrayOf("*/*")) }) {
            Text(text = stringResource(id = R.string.profile_choose_file))
        }

        selectedFile?.let { file ->
            Text(
                text = stringResource(
                    id = R.string.profile_selected_path,
                    file.toString(),
                ),
            )
            Button(
                onClick = {
                    runCatching {
                        saveFileToAppDirectory(context, file)
                    }.onSuccess { filePath ->
                        Global.updateProfilePath(filePath)
                        savedFilePath = filePath
                        statusMessage = context.getString(
                            R.string.profile_import_success,
                            queryDisplayName(context, file),
                        )
                    }.onFailure { error ->
                        val detail = error.message ?: context.getString(R.string.profile_unknown_error)
                        statusMessage = context.getString(
                            R.string.profile_import_error,
                            detail,
                        )
                    }
                },
            ) {
                Text(text = stringResource(id = R.string.profile_save_file))
            }
        }

        if (savedFilePath.isNotEmpty()) {
            Text(
                text = stringResource(
                    id = R.string.profile_saved_path,
                    savedFilePath,
                ),
            )
        } else {
            Text(text = stringResource(id = R.string.profile_no_saved_path))
        }

        if (statusMessage.isNotBlank()) {
            Text(text = statusMessage)
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    return context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
    } ?: "profile.yaml"
}

private fun saveFileToAppDirectory(context: Context, uri: Uri): String {
    val fileName = queryDisplayName(context, uri)
    val outputFile = File(context.filesDir, fileName)
    context.contentResolver.openInputStream(uri)?.use { input ->
        outputFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    return outputFile.absolutePath
}
