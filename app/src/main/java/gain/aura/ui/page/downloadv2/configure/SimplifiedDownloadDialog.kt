package gain.aura.ui.page.downloadv2.configure

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import gain.aura.R
import gain.aura.download.DownloaderV2
import gain.aura.download.Task
import gain.aura.download.TaskFactory
import gain.aura.ui.component.FormatItem
import gain.aura.ui.component.FormatSubtitle
import gain.aura.ui.component.FormatVideoPreview
import gain.aura.ui.component.SealModalBottomSheet
import gain.aura.ui.common.booleanState
import gain.aura.ui.common.intState
import gain.aura.util.DownloadUtil
import gain.aura.util.EXTRACT_AUDIO
import gain.aura.util.Format
import gain.aura.util.RES_1080P
import gain.aura.util.RES_1440P
import gain.aura.util.RES_2160P
import gain.aura.util.RES_360P
import gain.aura.util.RES_720P
import gain.aura.util.RES_HIGHEST
import gain.aura.util.VIDEO_QUALITY
import gain.aura.util.VideoInfo
import gain.aura.util.toHttpsUrl
import gain.aura.ads.AdManager
import gain.aura.App.Companion.isFDroidBuild
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

sealed class DownloadOption {
    data class AudioFast(val format: Format? = null) : DownloadOption()
    data class AudioClassic(val format: Format? = null) : DownloadOption()
    data class VideoFast(val format: Format? = null) : DownloadOption()
    data class VideoHD720(val format: Format? = null) : DownloadOption()
    data class VideoHD1080(val format: Format? = null) : DownloadOption()
    data class Video2K(val format: Format? = null) : DownloadOption()
    data class Video4K(val format: Format? = null) : DownloadOption()
    data class CustomFormat(val format: Format) : DownloadOption()
}

private const val NOT_SELECTED = -1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplifiedDownloadDialog(
    videoInfo: VideoInfo,
    sheetState: androidx.compose.material3.SheetState,
    onDismissRequest: () -> Unit,
    onDownloadStarted: () -> Unit = {},
    downloader: DownloaderV2 = koinInject(),
) {
    var selectedOption by remember { mutableStateOf<DownloadOption?>(null) }
    var showMoreFormats by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var isStartingDownload by remember { mutableStateOf(false) }
    
    // Track ad loading state - allow download after 10 seconds timeout even if ad fails (skip for F-Droid builds)
    var isAdReady by remember { mutableStateOf(isFDroidBuild()) }
    var adLoadTimeout by remember { mutableStateOf(false) }
    
    // Load ad when dialog opens (skip for F-Droid builds)
    LaunchedEffect(Unit) {
        if (isFDroidBuild()) {
            isAdReady = true
            return@LaunchedEffect
        }
        
        // Check if ad is already loaded
        if (AdManager.isInterstitialAdLoaded()) {
            isAdReady = true
        } else {
            // Load ad if not already loading
            if (!AdManager.isInterstitialAdLoading()) {
                AdManager.loadInterstitialAd(context)
            }
            // Poll until ad is loaded (with timeout - max 10 seconds)
            var attempts = 0
            val maxAttempts = 50 // 50 * 200ms = 10 seconds
            while (attempts < maxAttempts && !AdManager.isInterstitialAdLoaded()) {
                kotlinx.coroutines.delay(200) // Check every 200ms
                attempts++
            }
            isAdReady = AdManager.isInterstitialAdLoaded()
            // If timeout reached, allow download anyway
            if (!isAdReady && attempts >= maxAttempts) {
                adLoadTimeout = true
                isAdReady = true // Allow download even if ad failed to load
            }
        }
    }

    val allFormats = videoInfo.formats ?: emptyList()
    val audioFormats = allFormats.filter { it.isAudioOnly() }
    val videoFormats = allFormats.filter { it.containsVideo() && it.containsAudio() }
    val videoOnlyFormats = allFormats.filter { it.isVideoOnly() }
    
    // Get preferences from settings
    val extractAudio by EXTRACT_AUDIO.booleanState
    val videoQuality by VIDEO_QUALITY.intState

    val fastAudioFormat = audioFormats.firstOrNull()
    val classicAudioFormat = audioFormats.find { it.ext == "mp3" } ?: audioFormats.firstOrNull()
    
    val fastVideoFormat = videoFormats.find { it.height?.let { it <= 360 } == true } 
        ?: videoOnlyFormats.find { it.height?.let { it <= 360 } == true }
        ?: videoFormats.firstOrNull()
        ?: videoOnlyFormats.firstOrNull()
    
    val hd720VideoFormat = videoFormats.find { it.height?.let { it >= 720 && it < 1080 } == true }
        ?: videoOnlyFormats.find { it.height?.let { it >= 720 && it < 1080 } == true }
        ?: videoFormats.find { it.height?.let { it >= 720 } == true }
        ?: videoOnlyFormats.find { it.height?.let { it >= 720 } == true }
    
    val hd1080VideoFormat = videoFormats.find { it.height?.let { it >= 1080 && it < 1440 } == true }
        ?: videoOnlyFormats.find { it.height?.let { it >= 1080 && it < 1440 } == true }
        ?: videoFormats.find { it.height?.let { it >= 1080 } == true }
        ?: videoOnlyFormats.find { it.height?.let { it >= 1080 } == true }
    
    val qhd2KVideoFormat = videoFormats.find { it.height?.let { it >= 1440 && it < 2160 } == true }
        ?: videoOnlyFormats.find { it.height?.let { it >= 1440 && it < 2160 } == true }
        ?: videoFormats.find { it.height?.let { it >= 1440 } == true }
        ?: videoOnlyFormats.find { it.height?.let { it >= 1440 } == true }
    
    val uhd4KVideoFormat = videoFormats.find { it.height?.let { it >= 2160 } == true }
        ?: videoOnlyFormats.find { it.height?.let { it >= 2160 } == true }
        ?: videoFormats.lastOrNull()
        ?: videoOnlyFormats.lastOrNull()

    // Format selection state for "More formats" section
    var selectedVideoAudioFormat by remember { mutableIntStateOf(NOT_SELECTED) }
    var selectedVideoOnlyFormat by remember { mutableIntStateOf(NOT_SELECTED) }
    var selectedAudioOnlyFormat by remember { mutableIntStateOf(NOT_SELECTED) }
    
    // Helper functions for format validation (must be defined before LaunchedEffect)
    fun getFileSize(format: Format?): String? {
        if (format == null) return null
        val duration = videoInfo.duration ?: 0.0
        // Calculate file size: fileSize > fileSizeApprox > (tbr * duration * 125) where 125 = 1000/8 (kbps to bytes)
        val sizeBytes = format.fileSize 
            ?: format.fileSizeApprox 
            ?: (duration * (format.tbr ?: 0.0) * 125.0)
        
        // Return null if size is 0 or invalid
        if (sizeBytes <= 0) return null
        
        val sizeMB = sizeBytes / (1024.0 * 1024.0)
        return when {
            sizeMB >= 1024 -> String.format("%.2f GB", sizeMB / 1024.0)
            sizeMB >= 1 -> String.format("%.1f MB", sizeMB)
            else -> String.format("%.0f KB", sizeMB * 1024.0)
        }
    }
    
    fun isFormatValid(format: Format?): Boolean {
        if (format == null) return false
        val fileSize = getFileSize(format)
        return fileSize != null && fileSize != "0 KB" && fileSize != "0 MB" && fileSize != "0 GB"
    }
    
    // Auto-select format based on settings preferences
    LaunchedEffect(videoInfo, extractAudio, videoQuality, audioFormats, videoFormats, videoOnlyFormats) {
        if (selectedOption == null) {
            val autoSelectedOption: DownloadOption? = when {
                // If audio extraction is enabled, select audio format
                extractAudio && audioFormats.isNotEmpty() -> {
                    // Prefer classic MP3 if available and valid, otherwise fast audio
                    val classicFormat = audioFormats.find { it.ext == "mp3" && isFormatValid(it) }
                    if (classicFormat != null) {
                        DownloadOption.AudioClassic(classicFormat)
                    } else {
                        audioFormats.firstOrNull { isFormatValid(it) }?.let { DownloadOption.AudioFast(it) }
                    }
                }
                // If video, select based on quality preference
                !extractAudio && (videoFormats.isNotEmpty() || videoOnlyFormats.isNotEmpty()) -> {
                    when (videoQuality) {
                        RES_2160P -> uhd4KVideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.Video4K(it) }
                            ?: qhd2KVideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.Video2K(it) }
                            ?: hd1080VideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoHD1080(it) }
                            ?: hd720VideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoHD720(it) }
                            ?: fastVideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoFast(it) }
                        RES_1440P -> qhd2KVideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.Video2K(it) }
                            ?: hd1080VideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoHD1080(it) }
                            ?: hd720VideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoHD720(it) }
                            ?: fastVideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoFast(it) }
                        RES_1080P -> hd1080VideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoHD1080(it) }
                            ?: hd720VideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoHD720(it) }
                            ?: fastVideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoFast(it) }
                        RES_720P -> hd720VideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoHD720(it) }
                            ?: fastVideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoFast(it) }
                        RES_360P -> fastVideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoFast(it) }
                        RES_HIGHEST, 0 -> {
                            // Best quality - select highest available valid format
                            uhd4KVideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.Video4K(it) }
                                ?: qhd2KVideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.Video2K(it) }
                                ?: hd1080VideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoHD1080(it) }
                                ?: hd720VideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoHD720(it) }
                                ?: fastVideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoFast(it) }
                        }
                        else -> {
                            // Default to best available valid quality
                            uhd4KVideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.Video4K(it) }
                                ?: qhd2KVideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.Video2K(it) }
                                ?: hd1080VideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoHD1080(it) }
                                ?: hd720VideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoHD720(it) }
                                ?: fastVideoFormat?.takeIf { isFormatValid(it) }?.let { DownloadOption.VideoFast(it) }
                        }
                    }
                }
                else -> null
            }
            if (autoSelectedOption != null) {
                selectedOption = autoSelectedOption
            }
        }
    }

    fun getSelectedFormat(): Format? {
        return when (selectedOption) {
            is DownloadOption.AudioFast -> (selectedOption as DownloadOption.AudioFast).format
            is DownloadOption.AudioClassic -> (selectedOption as DownloadOption.AudioClassic).format
            is DownloadOption.VideoFast -> (selectedOption as DownloadOption.VideoFast).format
            is DownloadOption.VideoHD720 -> (selectedOption as DownloadOption.VideoHD720).format
            is DownloadOption.VideoHD1080 -> (selectedOption as DownloadOption.VideoHD1080).format
            is DownloadOption.Video2K -> (selectedOption as DownloadOption.Video2K).format
            is DownloadOption.Video4K -> (selectedOption as DownloadOption.Video4K).format
            is DownloadOption.CustomFormat -> (selectedOption as DownloadOption.CustomFormat).format
            else -> null
        }
    }

    SealModalBottomSheet(
        sheetState = sheetState,
        contentPadding = PaddingValues(0.dp),
        onDismissRequest = onDismissRequest,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Video Info Preview - Compact
            item {
                FormatVideoPreview(
                    modifier = Modifier.fillMaxWidth(),
                    title = videoInfo.title ?: "",
                    author = videoInfo.uploader ?: videoInfo.channel ?: "",
                    thumbnailUrl = videoInfo.thumbnail?.toHttpsUrl() ?: "",
                    duration = (videoInfo.duration ?: 0.0).roundToInt(),
                    isSplittingVideo = false,
                    isClippingVideo = false,
                    isClippingAvailable = false,
                    isSplitByChapterAvailable = false,
                    onOpenThumbnail = {
                        videoInfo.thumbnail?.toHttpsUrl()?.let { uriHandler.openUri(it) }
                    },
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            // Quick Options
            item {
                Text(
                    text = stringResource(R.string.download_video_as),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (audioFormats.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.audio),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }

                if (isFormatValid(fastAudioFormat)) {
                    item {
                        OptionRow(
                            label = stringResource(R.string.fast),
                            fileSize = getFileSize(fastAudioFormat) ?: "",
                            selected = selectedOption is DownloadOption.AudioFast,
                            onClick = { selectedOption = DownloadOption.AudioFast(fastAudioFormat) },
                        )
                    }
                }

                if (isFormatValid(classicAudioFormat)) {
                    item {
                        OptionRow(
                            label = stringResource(R.string.classic_mp3),
                            fileSize = getFileSize(classicAudioFormat) ?: "",
                            selected = selectedOption is DownloadOption.AudioClassic,
                            onClick = { selectedOption = DownloadOption.AudioClassic(classicAudioFormat) },
                        )
                    }
                }
            }

            if (videoFormats.isNotEmpty() || videoOnlyFormats.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                item {
                    Text(
                        text = stringResource(R.string.video),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }

                if (isFormatValid(fastVideoFormat)) {
                    item {
                        OptionRow(
                            label = stringResource(R.string.fast_360p),
                            fileSize = getFileSize(fastVideoFormat) ?: "",
                            selected = selectedOption is DownloadOption.VideoFast,
                            onClick = { selectedOption = DownloadOption.VideoFast(fastVideoFormat) },
                        )
                    }
                }

                if (isFormatValid(hd720VideoFormat)) {
                    item {
                        OptionRow(
                            label = stringResource(R.string.high_quality_720p),
                            fileSize = getFileSize(hd720VideoFormat) ?: "",
                            selected = selectedOption is DownloadOption.VideoHD720,
                            onClick = { selectedOption = DownloadOption.VideoHD720(hd720VideoFormat) },
                        )
                    }
                }

                if (isFormatValid(hd1080VideoFormat)) {
                    item {
                        OptionRow(
                            label = stringResource(R.string.hd_1080p),
                            fileSize = getFileSize(hd1080VideoFormat) ?: "",
                            selected = selectedOption is DownloadOption.VideoHD1080,
                            onClick = { selectedOption = DownloadOption.VideoHD1080(hd1080VideoFormat) },
                        )
                    }
                }

                if (isFormatValid(qhd2KVideoFormat)) {
                    item {
                        OptionRow(
                            label = stringResource(R.string.qhd_2k),
                            fileSize = getFileSize(qhd2KVideoFormat) ?: "",
                            selected = selectedOption is DownloadOption.Video2K,
                            onClick = { selectedOption = DownloadOption.Video2K(qhd2KVideoFormat) },
                        )
                    }
                }

                if (isFormatValid(uhd4KVideoFormat)) {
                    item {
                        OptionRow(
                            label = stringResource(R.string.uhd_4k),
                            fileSize = getFileSize(uhd4KVideoFormat) ?: "",
                            selected = selectedOption is DownloadOption.Video4K,
                            onClick = { selectedOption = DownloadOption.Video4K(uhd4KVideoFormat) },
                        )
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            // More Formats Toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = showMoreFormats,
                        onClick = { showMoreFormats = !showMoreFormats },
                        role = Role.Button,
                    ).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.more_formats),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Icon(
                        imageVector = if (showMoreFormats) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // More Formats Section
            item {
                AnimatedVisibility(
                    visible = showMoreFormats,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Suggested Format
                        if (!videoInfo.requestedFormats.isNullOrEmpty() || !videoInfo.requestedDownloads.isNullOrEmpty()) {
                            FormatSubtitle(
                                text = stringResource(R.string.suggested),
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                            val requestedFormats = videoInfo.requestedFormats
                                ?: videoInfo.requestedDownloads?.map { it.toFormat() }
                                ?: emptyList()
                            requestedFormats.filter { isFormatValid(it) }.forEach { format ->
                                val isSelected = selectedOption is DownloadOption.CustomFormat &&
                                    (selectedOption as DownloadOption.CustomFormat).format == format
                                FormatItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    formatInfo = format,
                                    duration = videoInfo.duration ?: 0.0,
                                    selected = isSelected,
                                    onClick = {
                                        selectedOption = DownloadOption.CustomFormat(format)
                                    },
                                )
                            }
                        }

                        // Audio Formats
                        val validAudioFormats = audioFormats.filter { isFormatValid(it) }
                        if (validAudioFormats.isNotEmpty()) {
                            FormatSubtitle(
                                text = stringResource(R.string.audio),
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                            validAudioFormats.take(6).forEach { format ->
                                val isSelected = selectedOption is DownloadOption.CustomFormat &&
                                    (selectedOption as DownloadOption.CustomFormat).format == format
                                FormatItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    formatInfo = format,
                                    duration = videoInfo.duration ?: 0.0,
                                    selected = isSelected,
                                    onClick = {
                                        selectedOption = DownloadOption.CustomFormat(format)
                                    },
                                )
                            }
                        }

                        // Video Only Formats
                        val validVideoOnlyFormats = videoOnlyFormats.filter { isFormatValid(it) }
                        if (validVideoOnlyFormats.isNotEmpty()) {
                            FormatSubtitle(
                                text = stringResource(R.string.video_only),
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                            validVideoOnlyFormats.take(6).forEach { format ->
                                val isSelected = selectedOption is DownloadOption.CustomFormat &&
                                    (selectedOption as DownloadOption.CustomFormat).format == format
                                FormatItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    formatInfo = format,
                                    duration = videoInfo.duration ?: 0.0,
                                    selected = isSelected,
                                    onClick = {
                                        selectedOption = DownloadOption.CustomFormat(format)
                                    },
                                )
                            }
                        }

                        // Video + Audio Formats
                        val validVideoFormats = videoFormats.filter { isFormatValid(it) }
                        if (validVideoFormats.isNotEmpty()) {
                            FormatSubtitle(
                                text = stringResource(R.string.video),
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                            validVideoFormats.take(6).forEach { format ->
                                val isSelected = selectedOption is DownloadOption.CustomFormat &&
                                    (selectedOption as DownloadOption.CustomFormat).format == format
                                FormatItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    formatInfo = format,
                                    duration = videoInfo.duration ?: 0.0,
                                    selected = isSelected,
                                    onClick = {
                                        selectedOption = DownloadOption.CustomFormat(format)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Download Button
            item {
                Button(
                    onClick = {
                        if (isStartingDownload) return@Button
                        isStartingDownload = true

                        val format = getSelectedFormat()
                        val extractAudio = selectedOption is DownloadOption.AudioFast || 
                                          selectedOption is DownloadOption.AudioClassic ||
                                          (selectedOption is DownloadOption.CustomFormat && 
                                           (selectedOption as DownloadOption.CustomFormat).format.isAudioOnly())
                        
                        fun startDownloadAndClose() {
                            downloadWithFormat(videoInfo, format, downloader, extractAudio)
                            onDismissRequest()
                            onDownloadStarted()
                        }

                        // Show interstitial after user clicks download.
                        // If ad isn't available (or timeout path), proceed immediately.
                        if (!isFDroidBuild() && !adLoadTimeout && AdManager.isInterstitialAdLoaded()) {
                            val activity = context as? Activity
                            if (activity != null) {
                                val shown =
                                    AdManager.showInterstitialAd(activity) {
                                        startDownloadAndClose()
                                    }
                                if (!shown) {
                                    startDownloadAndClose()
                                }
                            } else {
                                startDownloadAndClose()
                            }
                        } else {
                            startDownloadAndClose()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedOption != null && isAdReady && !isStartingDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    if (isStartingDownload) {
                        Text(stringResource(R.string.loading))
                    } else if (!isAdReady) {
                        Text(stringResource(R.string.loading))
                    } else {
                        Text(stringResource(R.string.download))
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    fileSize: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(
            selected = selected,
            onClick = onClick,
            role = Role.RadioButton,
        ).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = fileSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun downloadWithFormat(
    videoInfo: VideoInfo,
    format: Format?,
    downloader: DownloaderV2,
    extractAudio: Boolean,
) {
    val preferences = DownloadUtil.DownloadPreferences.createFromPreferences().copy(
        extractAudio = extractAudio,
    )
    
    if (format != null) {
        val formatList = listOf(format)
        val taskWithState = TaskFactory.createWithConfigurations(
            videoInfo = videoInfo,
            formatList = formatList,
            videoClips = emptyList(),
            splitByChapter = false,
            newTitle = "",
            selectedSubtitles = emptyList(),
            selectedAutoCaptions = emptyList(),
        )
        downloader.enqueue(taskWithState.task, taskWithState.state)
    } else {
        val task = Task(
            url = videoInfo.originalUrl.toString(),
            preferences = preferences,
        )
        downloader.enqueue(task)
    }
}
