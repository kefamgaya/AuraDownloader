package gain.aura

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalContext
import gain.aura.util.ToastUtil
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gain.aura.R
import gain.aura.download.DownloaderV2
import gain.aura.ui.common.LocalDarkTheme
import gain.aura.ui.common.SettingsProvider
import gain.aura.ui.page.downloadv2.configure.DownloadDialogViewModel
import gain.aura.ui.page.downloadv2.configure.DownloadDialogViewModel.Action
import gain.aura.ui.page.downloadv2.configure.DownloadDialogViewModel.SelectionState
import gain.aura.ui.page.downloadv2.configure.PlaylistSelectionPage
import gain.aura.ui.page.downloadv2.configure.SimplifiedDownloadDialog
import gain.aura.ui.theme.SealTheme
import gain.aura.util.PreferenceUtil
import gain.aura.util.matchUrlFromSharedText
import gain.aura.util.setLanguage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.compose.koinInject

private const val TAG = "QuickDownloadActivity"

class QuickDownloadActivity : ComponentActivity() {
    private var sharedUrlCached: String = ""

    private fun Intent.getSharedURL(): String? {
        val intent = this

        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.dataString
            }

            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedContent ->
                    intent.removeExtra(Intent.EXTRA_TEXT)
                    matchUrlFromSharedText(sharedContent)
                }
            }

            else -> {
                null
            }
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getSharedURL()?.let { sharedUrlCached = it }

        if (sharedUrlCached.isEmpty()) {
            finish()
        }

        App.startService()

        enableEdgeToEdge()

        window.run {
            setBackgroundDrawable(ColorDrawable(0))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
            }
        }

        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }

        val viewModel: DownloadDialogViewModel = getViewModel()
        viewModel.postAction(Action.ProceedWithURLs(listOf(sharedUrlCached)))

        setContent {
            SettingsProvider(calculateWindowSizeClass(this).widthSizeClass) {
                SealTheme(
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                    isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                ) {
                    val downloader: DownloaderV2 = koinInject()
                    val scope = rememberCoroutineScope()
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    val selectionState =
                        viewModel.selectionStateFlow.collectAsStateWithLifecycle().value
                    val loadingState = viewModel.sheetStateFlow.collectAsStateWithLifecycle().value

                    when (selectionState) {
                        is SelectionState.FormatSelection -> {
                            LaunchedEffect(selectionState) {
                                sheetState.show()
                            }
                            SimplifiedDownloadDialog(
                                videoInfo = selectionState.info,
                                sheetState = sheetState,
                                onDismissRequest = {
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        viewModel.postAction(Action.Reset)
                                        this@QuickDownloadActivity.finish()
                                    }
                                },
                                onDownloadStarted = {
                                    this@QuickDownloadActivity.finish()
                                },
                                downloader = downloader,
                            )
                        }
                        SelectionState.Idle -> {}
                        is SelectionState.PlaylistSelection -> {
                            PlaylistSelectionPage(
                                state = selectionState,
                                onDismissRequest = {
                                    viewModel.postAction(Action.Reset)
                                    this.finish()
                                },
                            )
                        }
                    }

                    val context = LocalContext.current
                    when (val state = loadingState) {
                        is DownloadDialogViewModel.SheetState.Loading -> {
                            AlertDialog(
                                onDismissRequest = { },
                                title = { 
                                    Text(stringResource(R.string.status_fetching_video_info))
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(stringResource(R.string.fetching_info))
                                    }
                                },
                                confirmButton = {},
                            )
                        }
                        is DownloadDialogViewModel.SheetState.Error -> {
                            LaunchedEffect(state) {
                                val errorMessage = if (state.throwable.message?.contains("YouTube") == true) {
                                    context.getString(R.string.youtube_not_supported)
                                } else {
                                    context.getString(R.string.fetch_info_error_msg)
                                }
                                ToastUtil.makeToast(errorMessage)
                                viewModel.postAction(Action.Reset)
                                this@QuickDownloadActivity.finish()
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
