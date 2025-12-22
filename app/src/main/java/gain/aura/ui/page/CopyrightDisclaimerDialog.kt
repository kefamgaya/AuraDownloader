package gain.aura.ui.page

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import gain.aura.R

@Composable
fun CopyrightDisclaimerDialog(
    onDismissRequest: () -> Unit,
    onUnderstand: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.copyright_disclaimer_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.copyright_disclaimer_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
            )
        },
        confirmButton = {
            TextButton(onClick = onUnderstand) {
                Text(stringResource(R.string.i_understand))
            }
        },
    )
}

