package rs.chimera.android.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import rs.chimera.android.R

@Composable
internal fun ProfileDeleteDialog(
    mutationEnabled: Boolean,
    submitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!submitting) onDismiss()
        },
        title = { Text(text = stringResource(id = R.string.profile_delete)) },
        text = { Text(text = stringResource(id = R.string.profile_delete_confirm)) },
        confirmButton = {
            TextButton(
                enabled = mutationEnabled && !submitting,
                onClick = onConfirm,
            ) {
                Text(text = stringResource(id = R.string.profile_delete))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !submitting,
                onClick = onDismiss,
            ) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
    )
}
