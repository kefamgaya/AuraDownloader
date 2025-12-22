package gain.aura.ui.page.settings.format

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
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
import gain.aura.R
import gain.aura.ui.common.intState
import gain.aura.ui.component.BackButton
import gain.aura.ui.component.PreferenceItem
import gain.aura.ui.component.PreferenceSubtitle
import gain.aura.ui.component.PreferenceSwitch
import gain.aura.ui.common.booleanState
import gain.aura.ui.common.intState
import gain.aura.util.EXTRACT_AUDIO
import gain.aura.util.PreferenceStrings
import gain.aura.util.PreferenceUtil
import gain.aura.util.PreferenceUtil.getBoolean
import gain.aura.util.PreferenceUtil.updateInt
import gain.aura.util.PreferenceUtil.updateValue
import gain.aura.util.VIDEO_FORMAT
import gain.aura.util.VIDEO_QUALITY

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadFormatPreferences(onNavigateBack: () -> Unit, navigateToSubtitlePage: () -> Unit) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )

    var audioSwitch by EXTRACT_AUDIO.booleanState
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showVideoFormatDialog by remember { mutableStateOf(false) }
    var videoFormat by VIDEO_FORMAT.intState
    var videoQuality by VIDEO_QUALITY.intState

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(modifier = Modifier, text = stringResource(id = R.string.format)) },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            LazyColumn(contentPadding = it) {
                item { PreferenceSubtitle(text = stringResource(id = R.string.audio)) }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.extract_audio),
                        description = stringResource(id = R.string.extract_audio_summary),
                        icon = Icons.Outlined.MusicNote,
                        isChecked = audioSwitch,
                        onClick = {
                            audioSwitch = !audioSwitch
                            PreferenceUtil.updateValue(EXTRACT_AUDIO, audioSwitch)
                        },
                    )
                }
                item { PreferenceSubtitle(text = stringResource(id = R.string.video)) }
                item {
                    PreferenceItem(
                        title = stringResource(R.string.video_format_preference),
                        description = PreferenceStrings.getVideoFormatLabel(videoFormat),
                        icon = Icons.Outlined.VideoFile,
                        enabled = !audioSwitch,
                    ) {
                        showVideoFormatDialog = true
                    }
                }
                item {
                    PreferenceItem(
                        title = stringResource(id = R.string.video_quality),
                        description = PreferenceStrings.getVideoResolutionDesc(videoQuality),
                        icon = Icons.Outlined.HighQuality,
                        enabled = !audioSwitch,
                    ) {
                        showVideoQualityDialog = true
                    }
                }
                item {
                    PreferenceItem(
                        title = stringResource(id = R.string.subtitle),
                        icon = Icons.Outlined.Subtitles,
                        description = stringResource(id = R.string.subtitle_desc),
                    ) {
                        navigateToSubtitlePage()
                    }
                }
            }
        },
    )
    if (showVideoQualityDialog) {
        VideoQualityDialog(
            videoQuality = videoQuality,
            onDismissRequest = { showVideoQualityDialog = false },
        ) {
            videoQuality = it
            VIDEO_QUALITY.updateInt(it)
        }
    }
    if (showVideoFormatDialog) {
        VideoFormatDialog(
            videoFormatPreference = videoFormat,
            onDismissRequest = { showVideoFormatDialog = false },
        ) {
            PreferenceUtil.encodeInt(VIDEO_FORMAT, it)
            videoFormat = it
        }
    }
}
