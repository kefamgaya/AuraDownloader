package gain.aura.ui.page.videoplayer

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import gain.aura.util.FileUtil
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.io.File
import java.util.concurrent.TimeUnit
import android.os.Build
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.ui.platform.LocalView
import androidx.core.content.FileProvider

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUri: String,
    videoTitle: String,
    onBackPressed: () -> Unit,
    onPlayerCreated: ((ExoPlayer) -> Unit)? = null,
    onEnterPiP: (() -> Unit)? = null,
    onSaveVideo: (() -> Unit)? = null,
    isSaving: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var showSeekIndicator by remember { mutableStateOf<SeekDirection?>(null) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableIntStateOf(0) }
    var brightnessLevel by remember { mutableIntStateOf(0) }
    var isDraggingVolume by remember { mutableStateOf(false) }
    var isDraggingBrightness by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var showChaptersMenu by remember { mutableStateOf(false) }
    var availableSubtitles by remember { mutableStateOf<List<SubtitleInfo>>(emptyList()) }
    var selectedSubtitleIndex by remember { mutableIntStateOf(-1) }
    var chapters by remember { mutableStateOf<List<ChapterInfo>>(emptyList()) }
    
    val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    val view = LocalView.current
    val window = (context as? android.app.Activity)?.window
    val coroutineScope = rememberCoroutineScope()
    
    // Resolve content URI to file path if needed (do this first for consistent hashing)
    val resolvedVideoUri = remember(videoUri) {
        if (videoUri.startsWith("content://")) {
            // Try to resolve content URI to file path
            FileUtil.resolveContentUriToFilePath(Uri.parse(videoUri)) ?: videoUri
        } else if (videoUri.startsWith("file://")) {
            videoUri.removePrefix("file://")
        } else {
            videoUri
        }
    }
    
    // Resume playback position - use resolved path for consistent key
    val resumePositionKey = "video_resume_${resolvedVideoUri.hashCode()}"
    val kv = remember { MMKV.defaultMMKV() }
    val savedPosition = remember(resumePositionKey) { kv.decodeLong(resumePositionKey, 0L) }
    
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val currentVolume = remember { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }
    
    // Find subtitle files
    LaunchedEffect(resolvedVideoUri) {
        availableSubtitles = findSubtitleFiles(resolvedVideoUri, context)
        chapters = findChapters(resolvedVideoUri, context)
    }
    
    val exoPlayer = remember(resolvedVideoUri) {
        ExoPlayer.Builder(context).build().apply {
            val uri = when {
                resolvedVideoUri.startsWith("content://") -> {
                    // Use content URI directly - this is the preferred method for Android 10+
                    // ExoPlayer can handle content URIs natively
                    Uri.parse(resolvedVideoUri)
                }
                resolvedVideoUri.startsWith("file://") -> {
                    // Remove file:// prefix and try FileProvider
                    val filePath = resolvedVideoUri.removePrefix("file://")
                    val file = File(filePath)
                    try {
                        // Try FileProvider first (works if file is in allowed paths)
                        if (file.exists() || android.os.Build.VERSION.SDK_INT < 29) {
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                context.packageName + ".provider",
                                file
                            )
                        } else {
                            // On Android 10+, if file doesn't exist, try to find via MediaStore
                            val fileName = file.name
                            if (!fileName.isEmpty()) {
                                FileUtil.findFileUriInMediaStore(fileName) ?: Uri.parse(resolvedVideoUri)
                            } else {
                                Uri.parse(resolvedVideoUri)
                            }
                        }
                    } catch (e: Exception) {
                        // FileProvider failed, try direct URI (may work on older Android)
                        if (android.os.Build.VERSION.SDK_INT < 29) {
                            Uri.parse(resolvedVideoUri)
                        } else {
                            // On Android 10+, try MediaStore lookup
                            val fileName = file.name
                            if (!fileName.isEmpty()) {
                                FileUtil.findFileUriInMediaStore(fileName) ?: Uri.parse(resolvedVideoUri)
                            } else {
                                Uri.parse(resolvedVideoUri)
                            }
                        }
                    }
                }
                else -> {
                    // Treat as file path
                    val file = File(resolvedVideoUri)
                    try {
                        // On Android 10+, prefer MediaStore if file doesn't exist
                        if (file.exists() || android.os.Build.VERSION.SDK_INT < 29) {
                            // Try FileProvider
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                context.packageName + ".provider",
                                file
                            )
                        } else {
                            // File doesn't exist, try MediaStore lookup
                            val fileName = file.name
                            if (!fileName.isEmpty()) {
                                FileUtil.findFileUriInMediaStore(fileName) ?: Uri.parse("file://$resolvedVideoUri")
                            } else {
                                Uri.parse("file://$resolvedVideoUri")
                            }
                        }
                    } catch (e: Exception) {
                        // FileProvider failed
                        if (android.os.Build.VERSION.SDK_INT < 29) {
                            Uri.fromFile(file)
                        } else {
                            // On Android 10+, try MediaStore
                            val fileName = file.name
                            if (!fileName.isEmpty()) {
                                FileUtil.findFileUriInMediaStore(fileName) ?: Uri.parse("file://$resolvedVideoUri")
                            } else {
                                Uri.parse("file://$resolvedVideoUri")
                            }
                        }
                    }
                }
            }
            
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .build()
            
            setMediaItem(mediaItem)
            // Resume from saved position if available and > 5 seconds
            if (savedPosition > 5000) {
                seekTo(savedPosition)
            }
            prepare()
            playWhenReady = true
            setPlaybackSpeed(1.0f)
            repeatMode = Player.REPEAT_MODE_OFF
        }.also { player ->
            onPlayerCreated?.invoke(player)
        }
    }
    
    // Update repeat mode
    LaunchedEffect(repeatMode) {
        exoPlayer.repeatMode = repeatMode
    }
    
    // Save playback position periodically
    var lastSavedPosition by remember { mutableLongStateOf(0L) }
    LaunchedEffect(currentPosition) {
        if (currentPosition > 0 && duration > 0 && abs(currentPosition - lastSavedPosition) > 5000) {
            // Save position every 5 seconds
            kv.encode(resumePositionKey, currentPosition)
            lastSavedPosition = currentPosition
        }
    }
    
    // Save position on dispose
    DisposableEffect(Unit) {
        onDispose {
            kv.encode(resumePositionKey, exoPlayer.currentPosition)
        }
    }
    
    // Update playback speed when changed
    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }
    
    // Update player state
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }
            
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    
    // Update position periodically
    LaunchedEffect(isPlaying) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    
    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }
    
    // Handle back press
    BackHandler { onBackPressed() }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isDraggingVolume, isDraggingBrightness, isLocked) {
                // Volume/Brightness control - swipe up/down
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val screenWidth = size.width
                        if (offset.x < screenWidth / 2) {
                            // Left side - volume control
                            isDraggingVolume = true
                            showVolumeIndicator = true
                            volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        } else {
                            // Right side - brightness control
                            isDraggingBrightness = true
                            showBrightnessIndicator = true
                            try {
                                brightnessLevel = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                            } catch (e: Exception) {
                                brightnessLevel = 128
                            }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (isDraggingVolume) {
                            // Volume control - drag up to increase, down to decrease
                            val delta = (-dragAmount / size.height * maxVolume * 2).toInt()
                            val newVolume = (volumeLevel + delta).coerceIn(0, maxVolume)
                            if (newVolume != volumeLevel) {
                                volumeLevel = newVolume
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                            }
                        } else if (isDraggingBrightness && window != null) {
                            // Brightness control - drag up to increase, down to decrease
                            val delta = (-dragAmount / size.height * 255).toInt()
                            val newBrightness = (brightnessLevel + delta).coerceIn(0, 255)
                            if (newBrightness != brightnessLevel) {
                                brightnessLevel = newBrightness
                                val layoutParams = window.attributes
                                layoutParams.screenBrightness = newBrightness / 255f
                                window.attributes = layoutParams
                            }
                        }
                    },
                    onDragEnd = {
                        isDraggingVolume = false
                        isDraggingBrightness = false
                        // Hide indicators after delay
                        coroutineScope.launch {
                            delay(1000)
                            showVolumeIndicator = false
                            showBrightnessIndicator = false
                        }
                    }
                )
            }
            .pointerInput(isDraggingVolume, isDraggingBrightness) {
                detectTapGestures(
                    onTap = { 
                        if (!isDraggingVolume && !isDraggingBrightness && !isLocked) {
                            showControls = !showControls
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!isDraggingVolume && !isDraggingBrightness && !isLocked) {
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 2) {
                                // Double tap left - rewind 10 seconds
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                showSeekIndicator = SeekDirection.BACKWARD
                            } else {
                                // Double tap right - forward 10 seconds
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                                showSeekIndicator = SeekDirection.FORWARD
                            }
                        }
                    }
                )
            }
    ) {
        // Video Player
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    subtitleView?.visibility = android.view.View.VISIBLE
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Seek Indicator
        AnimatedVisibility(
            visible = showSeekIndicator != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            LaunchedEffect(showSeekIndicator) {
                delay(500)
                showSeekIndicator = null
            }
            
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSeekIndicator == SeekDirection.BACKWARD) {
                    SeekIndicator(isForward = false)
                } else if (showSeekIndicator == SeekDirection.FORWARD) {
                    SeekIndicator(isForward = true)
                }
            }
        }
        
        // Lock overlay
        AnimatedVisibility(
            visible = isLocked && !showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                isLocked = false
                                showControls = true
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Screen Locked",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Double tap to unlock",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Controls Overlay
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top gradient and title bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .statusBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = videoTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Lock button
                        IconButton(
                            onClick = { isLocked = !isLocked },
                            enabled = !isLocked || showControls
                        ) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (isLocked) "Unlock" else "Lock",
                                tint = if (isLocked) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                        
                        // PiP button (Android 8.0+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && onEnterPiP != null) {
                            IconButton(onClick = { onEnterPiP() }) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPicture,
                                    contentDescription = "Picture in Picture",
                                    tint = Color.White
                                )
                            }
                        }
                        
                        // Share button
                        IconButton(onClick = { showShareDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White
                            )
                        }
                    }
                }
                
                // Center play/pause button
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                // Bottom controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Column {
                        // Progress bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatDuration(currentPosition),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                            
                            Slider(
                                value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                                onValueChange = { progress ->
                                    exoPlayer.seekTo((progress * duration).toLong())
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                            
                            Text(
                                text = formatDuration(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Control buttons row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rewind 10s
                            IconButton(onClick = {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.Replay10,
                                    contentDescription = "Rewind 10 seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            // Play/Pause
                            IconButton(
                                onClick = {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            // Forward 10s
                            IconButton(onClick = {
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.Forward10,
                                    contentDescription = "Forward 10 seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            // Speed control
                            Box {
                                TextButton(
                                    onClick = { showSpeedMenu = true },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false }
                                ) {
                                    speedOptions.forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${speed}x",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            },
                                            onClick = {
                                                playbackSpeed = speed
                                                showSpeedMenu = false
                                            },
                                            colors = MenuDefaults.itemColors(
                                                textColor = if (playbackSpeed == speed) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        )
                                    }
                                }
                            }
                            
                            // Repeat/Loop button
                            IconButton(
                                onClick = {
                                    repeatMode = when (repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = when (repeatMode) {
                                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                        Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                                        else -> Icons.Default.Repeat
                                    },
                                    contentDescription = "Repeat",
                                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.White
                                    },
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            // Subtitle button
                            if (availableSubtitles.isNotEmpty()) {
                                Box {
                                    IconButton(onClick = { showSubtitleMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Subtitles,
                                            contentDescription = "Subtitles",
                                            tint = if (selectedSubtitleIndex >= 0) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color.White
                                            },
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = showSubtitleMenu,
                                        onDismissRequest = { showSubtitleMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Off") },
                                            onClick = {
                                                selectedSubtitleIndex = -1
                                                // Note: ExoPlayer handles subtitles automatically
                                                showSubtitleMenu = false
                                            },
                                            colors = MenuDefaults.itemColors(
                                                textColor = if (selectedSubtitleIndex == -1) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        )
                                        availableSubtitles.forEachIndexed { index, subtitle ->
                                            DropdownMenuItem(
                                                text = { Text(subtitle.name) },
                                                onClick = {
                                                    selectedSubtitleIndex = index
                                                    showSubtitleMenu = false
                                                },
                                                colors = MenuDefaults.itemColors(
                                                    textColor = if (selectedSubtitleIndex == index) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Chapters button
                            if (chapters.isNotEmpty()) {
                                Box {
                                    IconButton(onClick = { showChaptersMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.List,
                                            contentDescription = "Chapters",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = showChaptersMenu,
                                        onDismissRequest = { showChaptersMenu = false },
                                        modifier = Modifier.heightIn(max = 400.dp)
                                    ) {
                                        chapters.forEach { chapter ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            text = chapter.title,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                        Text(
                                                            text = formatDuration(chapter.startTime),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    exoPlayer.seekTo(chapter.startTime)
                                                    showChaptersMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Loading indicator
                if (playbackState == Player.STATE_BUFFERING) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        // Volume indicator
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            VolumeBrightnessIndicator(
                icon = Icons.Default.VolumeUp,
                label = "Volume",
                level = volumeLevel,
                maxLevel = maxVolume
            )
        }
        
        // Brightness indicator
        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            VolumeBrightnessIndicator(
                icon = Icons.Default.Brightness6,
                label = "Brightness",
                level = brightnessLevel,
                maxLevel = 255
            )
        }
        
        // Save FAB (if callback provided)
        if (onSaveVideo != null) {
            FloatingActionButton(
                onClick = {
                    if (!isSaving) {
                        onSaveVideo()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Save Video"
                    )
                }
            }
        }
        
        // Share dialog
        if (showShareDialog) {
            AlertDialog(
                onDismissRequest = { showShareDialog = false },
                title = { Text("Share Video") },
                text = { Text("Share this video with other apps?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            FileUtil.createIntentForSharingFile(videoUri)?.let { intent ->
                                context.startActivity(Intent.createChooser(intent, "Share Video"))
                            }
                            showShareDialog = false
                        }
                    ) {
                        Text("Share")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShareDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SeekIndicator(isForward: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.6f),
        modifier = Modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isForward) Icons.Outlined.Forward10 else Icons.Outlined.Replay10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "10s",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}

private enum class SeekDirection {
    FORWARD, BACKWARD
}

@Composable
private fun VolumeBrightnessIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    level: Int,
    maxLevel: Int
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.7f),
        modifier = Modifier.padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { level.toFloat() / maxLevel },
                modifier = Modifier
                    .width(200.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${(level * 100 / maxLevel)}%",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

data class SubtitleInfo(
    val name: String,
    val uri: Uri,
    val language: String = ""
)

data class ChapterInfo(
    val title: String,
    val startTime: Long
)

private fun findSubtitleFiles(videoUri: String, context: android.content.Context): List<SubtitleInfo> {
    val subtitles = mutableListOf<SubtitleInfo>()
    
    try {
        val videoFile = when {
            videoUri.startsWith("file://") -> File(videoUri.removePrefix("file://"))
            videoUri.startsWith("content://") -> {
                // Try to resolve content URI to file path
                val filePath = FileUtil.resolveContentUriToFilePath(Uri.parse(videoUri))
                filePath?.let { File(it) }
            }
            else -> {
                // Treat as direct file path
                val file = File(videoUri)
                if (file.exists()) file else null
            }
        }
        
        if (videoFile != null && videoFile.exists()) {
            val videoDir = videoFile.parentFile
            val videoNameWithoutExt = videoFile.nameWithoutExtension
            
            // Look for subtitle files in the same directory
            videoDir?.listFiles()?.forEach { file ->
                if (file.nameWithoutExtension.equals(videoNameWithoutExt, ignoreCase = true)) {
                    val ext = file.extension.lowercase()
                    if (ext in listOf("srt", "vtt", "ass", "ssa", "ttml", "dfxp")) {
                        val language = extractLanguageFromFileName(file.name)
                        subtitles.add(
                            SubtitleInfo(
                                name = language.ifEmpty { file.name },
                                uri = Uri.fromFile(file),
                                language = language
                            )
                        )
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    return subtitles
}

private fun extractLanguageFromFileName(fileName: String): String {
    // Try to extract language code from filename (e.g., video.en.srt -> en)
    val patterns = listOf(
        "\\.([a-z]{2})\\.(srt|vtt|ass|ssa|ttml|dfxp)$".toRegex(RegexOption.IGNORE_CASE),
        "\\.([a-z]{2})-([a-z]{2})\\.(srt|vtt|ass|ssa|ttml|dfxp)$".toRegex(RegexOption.IGNORE_CASE)
    )
    
    patterns.forEach { pattern ->
        pattern.find(fileName)?.let {
            return it.groupValues[1].uppercase()
        }
    }
    
    return ""
}

private fun findChapters(videoUri: String, context: android.content.Context): List<ChapterInfo> {
    val chapters = mutableListOf<ChapterInfo>()
    
    try {
        // Try to find chapters from video metadata or separate chapter file
        // For now, return empty list - chapters would need to be extracted from video metadata
        // or from a separate chapters file if available
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    return chapters
}


