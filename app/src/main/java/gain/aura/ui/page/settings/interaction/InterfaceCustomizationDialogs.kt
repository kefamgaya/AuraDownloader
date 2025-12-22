package gain.aura.ui.page.settings.interaction

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import gain.aura.R
import gain.aura.ui.component.DialogSingleChoiceItem
import gain.aura.ui.component.SealDialog
import gain.aura.util.NONE
import gain.aura.util.USE_PREVIOUS_SELECTION

@Composable
fun DownloadTypeCustomizationDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    selectedItem: Int,
    onSelect: (Int) -> Unit,
) {
    SealDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        confirmButton = null,
        title = { Text(text = stringResource(id = R.string.download_type)) },
        text = {
            LazyColumn(modifier = Modifier.padding()) {
                item {
                    DialogSingleChoiceItem(
                        text = stringResource(id = R.string.use_previous_selection),
                        selected = selectedItem == USE_PREVIOUS_SELECTION,
                    ) {
                        onSelect(USE_PREVIOUS_SELECTION)
                    }
                }

                item {
                    DialogSingleChoiceItem(
                        text = stringResource(id = R.string.none),
                        selected = selectedItem == NONE,
                    ) {
                        onSelect(NONE)
                    }
                }
            }
        },
    )
}

@Preview
@Composable
private fun Preview() {
    DownloadTypeCustomizationDialog(onDismissRequest = {}, selectedItem = NONE) {}
}
