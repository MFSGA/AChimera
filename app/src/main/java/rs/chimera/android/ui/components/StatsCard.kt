package rs.chimera.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

@Composable
fun StatsCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    containerColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .semantics {
            contentDescription = "$title: $value${if (subtitle.isNotEmpty()) ", $subtitle" else ""}"
        }
    val cardColors = CardDefaults.cardColors(
        containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    val cardShape = RoundedCornerShape(26.dp)
    val cardBorder = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
    )

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.88f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }

    if (onClick != null) {
        Card(
            modifier = cardModifier,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 1.dp),
            colors = cardColors,
            border = cardBorder,
            shape = cardShape,
            onClick = onClick,
        ) {
            content()
        }
    } else {
        Card(
            modifier = cardModifier,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = cardColors,
            border = cardBorder,
            shape = cardShape,
        ) {
            content()
        }
    }
}
