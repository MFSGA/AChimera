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
import androidx.core.content.edit
import java.io.File

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPreferences = remember(context) {
        context.getSharedPreferences("file_prefs", Context.MODE_PRIVATE)
    }

    var savedFilePath by remember {
        mutableStateOf(sharedPreferences.getString("profile_path", null) ?: "")
    }
    var selectedFile by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        selectedFile = it
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Button(onClick = { launcher.launch(arrayOf("*/*")) }) {
            Text(text = "Choose File")
        }

        selectedFile?.let { file ->
            Text(text = "Path: $file")
            Button(
                onClick = {
                    val filePath = saveFileToAppDirectory(context, file)
                    sharedPreferences.edit { putString("profile_path", filePath) }
                    rs.chimera.android.Global.profilePath = filePath
                    savedFilePath = filePath
                },
            ) {
                Text(text = "Save File")
            }
        }

        if (savedFilePath.isNotEmpty()) {
            Text(text = "Saved file path: $savedFilePath")
        }
    }
}

private fun saveFileToAppDirectory(context: Context, uri: Uri): String {
    val fileName = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
    } ?: "profile.yaml"

    val outputFile = File(context.filesDir, fileName)
    context.contentResolver.openInputStream(uri)?.use { input ->
        outputFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    return outputFile.absolutePath
}
