package gain.aura.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import gain.aura.R
import gain.aura.ui.component.ClearButton
import gain.aura.ui.page.downloadv2.configure.DownloadDialogViewModel
import gain.aura.ui.page.downloadv2.configure.DownloadDialogViewModel.Action
import gain.aura.util.findURLsFromString

@Composable
fun UrlInputPage(
    modifier: Modifier = Modifier,
    dialogViewModel: DownloadDialogViewModel,
) {
    val clipboardManager = LocalClipboardManager.current
    var url by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.video_url)) },
            placeholder = { Text(stringResource(R.string.video_url)) },
            maxLines = 3,
            trailingIcon = {
                if (url.isNotEmpty()) {
                    ClearButton { url = "" }
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    clipboardManager.getText()?.let {
                        val urls = findURLsFromString(it.toString())
                        if (urls.isNotEmpty()) {
                            url = urls.first()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.paste))
            }

            Button(
                onClick = {
                    if (url.isNotEmpty()) {
                        dialogViewModel.postAction(Action.ProceedWithURLs(listOf(url)))
                    }
                },
                enabled = url.isNotEmpty(),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.search))
            }
        }
    }
}

