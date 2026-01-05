package gain.aura.ui.page.videoplayer

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import gain.aura.ui.theme.SealTheme
import java.io.File

class VideoPlayerActivity : ComponentActivity() {
    
    private var exoPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    
    private fun getVideoInfoFromIntent(intent: Intent): Pair<String, String>? {
        return when {
            // Intent from our app (with extras)
            intent.hasExtra(EXTRA_VIDEO_URI) -> {
                val uri = intent.getStringExtra(EXTRA_VIDEO_URI) ?: return null
                val title = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Video"
                Pair(uri, title)
            }
            // Intent from system (opening video file directly)
            intent.data != null -> {
                val uri = intent.data!!
                val videoUri = uri.toString()
                val videoTitle = when {
                    uri.scheme == "file" -> {
                        File(uri.path ?: "").nameWithoutExtension
                    }
                    uri.scheme == "content" -> {
                        // Try to get display name from content resolver
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0 && cursor.moveToFirst()) {
                                cursor.getString(nameIndex)?.replaceAfterLast(".", "")?.dropLast(1) ?: "Video"
                            } else "Video"
                        } ?: "Video"
                    }
                    else -> {
                        uri.lastPathSegment?.replaceAfterLast(".", "")?.dropLast(1) ?: "Video"
                    }
                }
                Pair(videoUri, videoTitle)
            }
            else -> null
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge and hide system bars for immersive playback
        enableEdgeToEdge()
        hideSystemBars()
        
        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Handle video file intents (for default video player)
        val videoInfo = getVideoInfoFromIntent(intent) ?: run {
            finish()
            return
        }
        
        val (videoUri, videoTitle) = videoInfo
        
        // Detect video aspect ratio and set orientation
        detectAndSetOrientation(videoUri)
        
        setContent {
            SealTheme(darkTheme = true) {
                VideoPlayerScreen(
                    videoUri = videoUri,
                    videoTitle = videoTitle,
                    onBackPressed = { finish() },
                    onPlayerCreated = { player -> 
                        exoPlayer = player
                        // Update orientation when player is ready with video dimensions
                        player.addListener(object : androidx.media3.common.Player.Listener {
                            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                                val aspectRatio = if (videoSize.height > 0) {
                                    videoSize.width.toFloat() / videoSize.height.toFloat()
                                } else 1f
                                setOrientationBasedOnAspectRatio(aspectRatio)
                            }
                        })
                    },
                    onEnterPiP = { 
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            enterPictureInPictureMode()
                        }
                    },
                    onSaveVideo = null,
                    isSaving = false
                )
            }
        }
    }
    
    private fun detectAndSetOrientation(videoUri: String) {
        try {
            val retriever = MediaMetadataRetriever()
            val uri = Uri.parse(videoUri)
            
            try {
                when {
                    uri.scheme == "file" -> {
                        retriever.setDataSource(uri.path)
                    }
                    uri.scheme == "content" -> {
                        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            retriever.setDataSource(pfd.fileDescriptor)
                        } ?: return
                    }
                    else -> {
                        retriever.setDataSource(this, uri)
                    }
                }
                
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                
                if (width > 0 && height > 0) {
                    val aspectRatio = width.toFloat() / height.toFloat()
                    setOrientationBasedOnAspectRatio(aspectRatio)
                }
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Default to landscape if detection fails - will be updated when player loads video
        }
    }
    
    private fun setOrientationBasedOnAspectRatio(aspectRatio: Float) {
        // If aspect ratio is less than 1, it's portrait (height > width)
        // If aspect ratio is greater than or equal to 1, it's landscape (width >= height)
        requestedOrientation = if (aspectRatio < 1.0f) {
            Configuration.ORIENTATION_PORTRAIT
        } else {
            Configuration.ORIENTATION_LANDSCAPE
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Handle new video when activity is already running
        val videoInfo = getVideoInfoFromIntent(intent)
        if (videoInfo != null) {
            val (videoUri, videoTitle) = videoInfo
            // Restart activity with new video
            finish()
            startActivity(createIntent(this, videoUri, videoTitle))
        }
    }
    
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    override fun enterPictureInPictureMode() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            val aspectRatio = Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            super.enterPictureInPictureMode(params)
        }
    }
    
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto-enter PiP when user presses home
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode()
        }
    }
    
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            // Hide system UI in PiP
            hideSystemBars()
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (!isInPictureInPictureMode) {
            hideSystemBars()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Keep playing in PiP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            // Continue playback in PiP
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Don't release player if in PiP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInPictureInPictureMode) {
            exoPlayer?.release()
            exoPlayer = null
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
    
    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        
        fun createIntent(context: Context, videoUri: String, videoTitle: String): Intent {
            return Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URI, videoUri)
                putExtra(EXTRA_VIDEO_TITLE, videoTitle)
                val uri = Uri.parse(videoUri)
                data = uri
                // Grant read permission for content URIs
                if (uri.scheme == "content") {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                // Required when starting activity from non-Activity context
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}

