package gain.aura

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import gain.aura.App.Companion.context
import gain.aura.ui.common.LocalDarkTheme
import gain.aura.ui.common.SettingsProvider
import gain.aura.ui.page.AppEntry
import gain.aura.ui.page.downloadv2.configure.DownloadDialogViewModel
import gain.aura.ui.theme.SealTheme
import gain.aura.update.InAppUpdateManager
import gain.aura.util.PreferenceUtil
import gain.aura.util.ToastUtil
import gain.aura.util.matchUrlFromSharedText
import gain.aura.util.setLanguage
import gain.aura.R
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.KoinContext

class MainActivity : ComponentActivity() {
    private val dialogViewModel: DownloadDialogViewModel by viewModel()
    private var inAppUpdateManager: InAppUpdateManager? = null

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }
        enableEdgeToEdge()

        context = this.baseContext
        
        // Initialize Google Play In-App Updates (only for Play Store builds)
        if (!App.isFDroidBuild()) {
            initializeInAppUpdates()
        }
        
        setContent {
            KoinContext {
                val windowSizeClass = calculateWindowSizeClass(this)
                SettingsProvider(windowWidthSizeClass = windowSizeClass.widthSizeClass) {
                    SealTheme(
                        darkTheme = LocalDarkTheme.current.isDarkTheme(),
                        isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                    ) {
                        AppEntry(dialogViewModel = dialogViewModel)
                    }
                }
            }
        }
    }
    
    private fun initializeInAppUpdates() {
        try {
            inAppUpdateManager = InAppUpdateManager(this)
            // Check for updates when app starts
            inAppUpdateManager?.checkForUpdate()
            Log.d(TAG, "In-App Update Manager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize In-App Updates", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val url = intent.getSharedURL()
        if (url != null) {
            if (isYouTubeUrl(url)) {
                ToastUtil.makeToast(getString(R.string.youtube_not_supported))
            } else {
                dialogViewModel.postAction(DownloadDialogViewModel.Action.ProceedWithURLs(listOf(url)))
            }
        }
    }
    
    private fun isYouTubeUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("youtube.com") || 
               lowerUrl.contains("youtu.be") ||
               lowerUrl.contains("m.youtube.com") ||
               lowerUrl.contains("www.youtube.com")
    }

    private fun Intent.getSharedURL(): String? {
        val intent = this

        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.dataString
            }

            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedContent ->
                    intent.removeExtra(Intent.EXTRA_TEXT)
                    matchUrlFromSharedText(sharedContent).also { matchedUrl ->
                        if (sharedUrlCached != matchedUrl) {
                            sharedUrlCached = matchedUrl
                        }
                    }
                }
            }

            else -> {
                null
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private var sharedUrlCached = ""
    }
}
