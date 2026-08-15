package rs.chimera.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import rs.chimera.android.R
import rs.chimera.android.ui.preferences.AppearancePreference
import rs.chimera.android.ui.preferences.LanguagePreference
import rs.chimera.android.ui.preferences.UiVariant

@Composable
internal fun UiDialog(
    currentVariant: UiVariant,
    onDismiss: () -> Unit,
    onConfirm: (UiVariant) -> Unit,
) {
    var selectedVariant by remember { mutableStateOf(currentVariant) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_ui_style)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                LanguageOption(
                    text = stringResource(R.string.ui_style_watfaq),
                    selected = selectedVariant == UiVariant.WATFAQ,
                    onClick = { selectedVariant = UiVariant.WATFAQ },
                )
                LanguageOption(
                    text = stringResource(R.string.ui_style_metacubex),
                    selected = selectedVariant == UiVariant.METACUBEX,
                    onClick = { selectedVariant = UiVariant.METACUBEX },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedVariant) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun AppearanceDialog(
    currentPreference: AppearancePreference,
    onDismiss: () -> Unit,
    onConfirm: (AppearancePreference) -> Unit,
) {
    var selectedPreference by remember { mutableStateOf(currentPreference) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dark_mode)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                LanguageOption(
                    text = stringResource(R.string.dark_mode_system),
                    selected = selectedPreference == AppearancePreference.SYSTEM,
                    onClick = { selectedPreference = AppearancePreference.SYSTEM },
                )
                LanguageOption(
                    text = stringResource(R.string.dark_mode_light),
                    selected = selectedPreference == AppearancePreference.LIGHT,
                    onClick = { selectedPreference = AppearancePreference.LIGHT },
                )
                LanguageOption(
                    text = stringResource(R.string.dark_mode_dark),
                    selected = selectedPreference == AppearancePreference.DARK,
                    onClick = { selectedPreference = AppearancePreference.DARK },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedPreference) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun appearanceLabel(preference: AppearancePreference): String =
    stringResource(
        when (preference) {
            AppearancePreference.SYSTEM -> R.string.dark_mode_system
            AppearancePreference.LIGHT -> R.string.dark_mode_light
            AppearancePreference.DARK -> R.string.dark_mode_dark
        },
    )

@Composable
internal fun uiVariantLabel(variant: UiVariant): String =
    stringResource(
        when (variant) {
            UiVariant.WATFAQ -> R.string.ui_style_watfaq
            UiVariant.METACUBEX -> R.string.ui_style_metacubex
        },
    )

@Composable
internal fun LanguageDialog(
    currentPreference: LanguagePreference,
    onDismiss: () -> Unit,
    onConfirm: (LanguagePreference) -> Unit,
) {
    var selectedPreference by remember { mutableStateOf(currentPreference) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                LanguageOption(
                    text = stringResource(R.string.language_system),
                    selected = selectedPreference == LanguagePreference.SYSTEM,
                    onClick = { selectedPreference = LanguagePreference.SYSTEM },
                )
                LanguageOption(
                    text = stringResource(R.string.language_simplified_chinese),
                    selected = selectedPreference == LanguagePreference.SIMPLIFIED_CHINESE,
                    onClick = { selectedPreference = LanguagePreference.SIMPLIFIED_CHINESE },
                )
                LanguageOption(
                    text = stringResource(R.string.language_english),
                    selected = selectedPreference == LanguagePreference.ENGLISH,
                    onClick = { selectedPreference = LanguagePreference.ENGLISH },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedPreference) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun LanguageOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
