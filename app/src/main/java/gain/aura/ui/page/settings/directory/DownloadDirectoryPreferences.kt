package gain.aura.ui.page.settings.directory

import android.os.Build
import android.os.Environment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.SdCardAlert
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderDelete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.SnippetFolder
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.TabUnselected
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import gain.aura.App
import gain.aura.R
import gain.aura.ui.common.booleanState
import gain.aura.ui.common.stringState
import gain.aura.ui.component.BackButton
import gain.aura.ui.component.ConfirmButton
import gain.aura.ui.component.DialogSingleChoiceItem
import gain.aura.ui.component.DismissButton
import gain.aura.ui.component.LinkButton
import gain.aura.ui.component.OutlinedButtonChip
import gain.aura.ui.component.PreferenceInfo
import gain.aura.ui.component.PreferenceItem
import gain.aura.ui.component.PreferenceSubtitle
import gain.aura.ui.component.PreferenceSwitch
import gain.aura.ui.component.PreferenceSwitchWithDivider
import gain.aura.ui.component.PreferencesHintCard
import gain.aura.ui.component.SealDialog
import gain.aura.util.CUSTOM_COMMAND
import gain.aura.util.CUSTOM_OUTPUT_TEMPLATE
import gain.aura.util.DownloadUtil
import gain.aura.util.FileUtil
import gain.aura.util.FileUtil.getConfigDirectory
import gain.aura.util.FileUtil.getExternalTempDir
import gain.aura.util.OUTPUT_TEMPLATE
import gain.aura.util.PRIVATE_DIRECTORY
import gain.aura.util.PreferenceUtil
import gain.aura.util.PreferenceUtil.getBoolean
import gain.aura.util.PreferenceUtil.getString
import gain.aura.util.PreferenceUtil.updateBoolean
import gain.aura.util.PreferenceUtil.updateString
import gain.aura.util.RESTRICT_FILENAMES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ytdlpOutputTemplateReference = "https://github.com/yt-dlp/yt-dlp#output-template"
private val PublicDownloadsDirectory =
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
private val PublicDocumentDirectory =
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).path
private const val ytdlpFilesystemReference = "https://github.com/yt-dlp/yt-dlp#filesystem-options"

private fun String.isValidDirectory(): Boolean {
    return isEmpty() || contains(PublicDownloadsDirectory) || contains(PublicDocumentDirectory)
}

enum class Directory {
    AUDIO,
    VIDEO,
    SDCARD,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDirectoryPreferences(onNavigateBack: () -> Unit) {

    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showClearTempDialog by remember { mutableStateOf(false) }

    val isCustomCommandEnabled by remember { mutableStateOf(CUSTOM_COMMAND.getBoolean()) }

    var showOutputTemplateDialog by remember { mutableStateOf(false) }


    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = {
            SnackbarHost(modifier = Modifier.systemBarsPadding(), hostState = snackbarHostState)
        },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        modifier = Modifier,
                        text = stringResource(id = R.string.download_directory),
                    )
                },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
    ) {
        LazyColumn(modifier = Modifier, contentPadding = it) {
            if (isCustomCommandEnabled)
                item {
                    PreferenceInfo(text = stringResource(id = R.string.custom_command_enabled_hint))
                }

            // NOTE: We intentionally do NOT request "All files access" (MANAGE_EXTERNAL_STORAGE).
            // Scoped storage compliant flows should use SAF/MediaStore.
            item { PreferenceSubtitle(text = stringResource(R.string.general_settings)) }
            item {
                PreferenceItem(
                    title = stringResource(id = R.string.video_directory),
                    description = "/storage/emulated/0/Download/Aura",
                    enabled = false,
                    icon = Icons.Outlined.VideoLibrary,
                ) {
                    // Directory is fixed and cannot be modified
                }
            }
            item {
                PreferenceItem(
                    title = stringResource(id = R.string.audio_directory),
                    description = "/storage/emulated/0/Download/Aura/Audio",
                    enabled = false,
                    icon = Icons.Outlined.LibraryMusic,
                ) {
                    // Directory is fixed and cannot be modified
                }
            }
            item { PreferenceSubtitle(text = stringResource(R.string.advanced_settings)) }
            item {
                PreferenceItem(
                    title = stringResource(R.string.output_template),
                    description = stringResource(id = R.string.output_template_desc),
                    icon = Icons.Outlined.FolderSpecial,
                    enabled = !isCustomCommandEnabled,
                    onClick = { showOutputTemplateDialog = true },
                )
            }
            item {
                var restrictFilenames by RESTRICT_FILENAMES.booleanState
                PreferenceSwitch(
                    title = stringResource(id = R.string.restrict_filenames),
                    icon = Icons.Outlined.Spellcheck,
                    description = stringResource(id = R.string.restrict_filenames_desc),
                    isChecked = restrictFilenames,
                ) {
                    restrictFilenames = !restrictFilenames
                    RESTRICT_FILENAMES.updateBoolean(restrictFilenames)
                }
            }
            item {
                PreferenceItem(
                    title = stringResource(R.string.clear_temp_files),
                    description = stringResource(R.string.clear_temp_files_desc),
                    icon = Icons.Outlined.FolderDelete,
                    onClick = { showClearTempDialog = true },
                )
            }
        }
    }

    if (showClearTempDialog) {
        AlertDialog(
            onDismissRequest = { showClearTempDialog = false },
            icon = { Icon(Icons.Outlined.FolderDelete, null) },
            title = { Text(stringResource(id = R.string.clear_temp_files)) },
            dismissButton = { DismissButton { showClearTempDialog = false } },
            text = {
                Text(
                    stringResource(
                        R.string.clear_temp_files_info,
                        getExternalTempDir().absolutePath,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                ConfirmButton {
                    showClearTempDialog = false
                    scope.launch(Dispatchers.IO) {
                        FileUtil.clearTempFiles(context.getConfigDirectory())
                        val count =
                            FileUtil.run {
                                clearTempFiles(getExternalTempDir()) +
                                    clearTempFiles(context.getSdcardTempDir(null)) +
                                    clearTempFiles(context.getInternalTempDir())
                            }

                        withContext(Dispatchers.Main) {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.clear_temp_files_count).format(count)
                            )
                        }
                    }
                }
            },
        )
    }
    val outputTemplate by
        remember(showOutputTemplateDialog) { mutableStateOf(OUTPUT_TEMPLATE.getString()) }
    val customTemplate by
        remember(showOutputTemplateDialog) { mutableStateOf(CUSTOM_OUTPUT_TEMPLATE.getString()) }
    if (showOutputTemplateDialog) {
        OutputTemplateDialog(
            selectedTemplate = outputTemplate,
            customTemplate = customTemplate,
            onDismissRequest = { showOutputTemplateDialog = false },
            onConfirm = { selected, custom ->
                OUTPUT_TEMPLATE.updateString(selected)
                CUSTOM_OUTPUT_TEMPLATE.updateString(custom)
                showOutputTemplateDialog = false
            },
        )
    }
}

@Composable
@Preview
fun OutputTemplateDialog(
    selectedTemplate: String = DownloadUtil.OUTPUT_TEMPLATE_DEFAULT,
    customTemplate: String = DownloadUtil.OUTPUT_TEMPLATE_ID,
    onDismissRequest: () -> Unit = {},
    onConfirm: (String, String) -> Unit = { s, s1 -> },
) {
    var editingTemplate by remember { mutableStateOf(customTemplate) }

    var selectedItem by remember {
        mutableIntStateOf(
            when (selectedTemplate) {
                DownloadUtil.OUTPUT_TEMPLATE_DEFAULT -> 1
                DownloadUtil.OUTPUT_TEMPLATE_ID -> 2
                else -> 3
            }
        )
    }

    var error by remember { mutableIntStateOf(0) }

    SealDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            ConfirmButton(enabled = error == 0) {
                onConfirm(
                    when (selectedItem) {
                        1 -> DownloadUtil.OUTPUT_TEMPLATE_DEFAULT
                        2 -> DownloadUtil.OUTPUT_TEMPLATE_ID
                        else -> editingTemplate
                    },
                    editingTemplate,
                )
            }
        },
        dismissButton = { DismissButton { onDismissRequest() } },
        title = { Text(text = stringResource(id = R.string.output_template)) },
        icon = { Icon(imageVector = Icons.Outlined.FolderSpecial, contentDescription = null) },
        text = {
            Column {
                Text(
                    text = stringResource(id = R.string.output_template_desc),
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )

                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CompositionLocalProvider(
                        LocalTextStyle provides
                            LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                    ) {
                        DialogSingleChoiceItem(
                            text = DownloadUtil.OUTPUT_TEMPLATE_DEFAULT,
                            selected = selectedItem == 1,
                        ) {
                            selectedItem = 1
                        }
                        DialogSingleChoiceItem(
                            text = DownloadUtil.OUTPUT_TEMPLATE_ID,
                            selected = selectedItem == 2,
                        ) {
                            selectedItem = 2
                        }
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            RadioButton(
                                modifier = Modifier.clearAndSetSemantics {},
                                selected = selectedItem == 3,
                                onClick = { selectedItem = 3 },
                            )
                            OutlinedTextField(
                                value = editingTemplate,
                                onValueChange = {
                                    error =
                                        if (!it.contains(DownloadUtil.BASENAME)) {
                                            1
                                        } else if (!it.endsWith(DownloadUtil.EXTENSION)) {
                                            2
                                        } else {
                                            0
                                        }
                                    editingTemplate = it
                                },
                                isError = error != 0,
                                supportingText = {
                                    Text(
                                        "Required: ${DownloadUtil.BASENAME}, ${DownloadUtil.EXTENSION}",
                                        fontFamily = FontFamily.Monospace,
                                    )
                                },
                                label = { Text(text = stringResource(id = R.string.custom)) },
                            )
                        }
                    }
                }

                LinkButton(
                    link = ytdlpOutputTemplateReference,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        },
    )
}
