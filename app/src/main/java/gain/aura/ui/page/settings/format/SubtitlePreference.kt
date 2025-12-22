package gain.aura.ui.page.settings.format

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import gain.aura.R
import gain.aura.ui.common.booleanState
import gain.aura.ui.component.BackButton
import gain.aura.ui.component.ConfirmButton
import gain.aura.ui.component.DismissButton
import gain.aura.ui.component.PreferenceItem
import gain.aura.ui.component.PreferenceSwitch
import gain.aura.ui.component.PreferenceSwitchWithContainer
import gain.aura.util.EMBED_SUBTITLE
import gain.aura.util.EXTRACT_AUDIO
import gain.aura.util.PreferenceUtil.getString
import gain.aura.util.PreferenceUtil.updateBoolean
import gain.aura.util.SUBTITLE
import gain.aura.util.SUBTITLE_LANGUAGE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitlePreference(onNavigateBack: () -> Unit) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )
    var downloadSubtitle by SUBTITLE.booleanState
    var embedSubtitle by EMBED_SUBTITLE.booleanState
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showEmbedSubtitleDialog by remember { mutableStateOf(false) }
    val subtitleLang by
        remember(showLanguageDialog) { mutableStateOf(SUBTITLE_LANGUAGE.getString()) }
    val downloadAudio by EXTRACT_AUDIO.booleanState

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(modifier = Modifier, text = stringResource(id = R.string.subtitle))
                },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            LazyColumn(modifier = Modifier, contentPadding = it) {
                item {
                    PreferenceSwitchWithContainer(
                        title = stringResource(id = R.string.download_subtitles),
                        isChecked = downloadSubtitle,
                        onClick = {
                            downloadSubtitle = !downloadSubtitle
                            SUBTITLE.updateBoolean(downloadSubtitle)
                        },
                        icon = null,
                    )
                }
                item {
                    PreferenceItem(
                        title = stringResource(id = R.string.subtitle_language),
                        icon = Icons.Outlined.Language,
                        description = subtitleLang,
                        onClick = { showLanguageDialog = true },
                    )
                }

                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.embed_subtitles),
                        description = stringResource(id = R.string.embed_subtitles_desc),
                        isChecked = embedSubtitle,
                        enabled = !downloadAudio,
                        onClick = {
                            if (embedSubtitle) {
                                embedSubtitle = false
                                EMBED_SUBTITLE.updateBoolean(false)
                            } else {
                                showEmbedSubtitleDialog = true
                            }
                        },
                        icon = Icons.Outlined.Subtitles,
                    )
                }
            }
        },
    )
    if (showLanguageDialog) SubtitleLanguageDialog { showLanguageDialog = false }
    if (showEmbedSubtitleDialog) {
        AlertDialog(
            onDismissRequest = { showEmbedSubtitleDialog = false },
            icon = { Icon(Icons.Outlined.Subtitles, null) },
            confirmButton = {
                ConfirmButton {
                    embedSubtitle = true
                    EMBED_SUBTITLE.updateBoolean(true)
                    showEmbedSubtitleDialog = false
                }
            },
            dismissButton = { DismissButton { showEmbedSubtitleDialog = false } },
            text = { Text(stringResource(id = R.string.embed_subtitles_mkv_msg)) },
            title = {
                Text(
                    stringResource(id = R.string.enable_experimental_feature),
                    textAlign = TextAlign.Center,
                )
            },
        )
    }
}
