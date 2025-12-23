package gain.aura.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import gain.aura.ui.common.LocalDarkTheme
import gain.aura.util.DarkThemePreference
import gain.aura.util.PreferenceUtil
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gain.aura.R
import gain.aura.download.DownloaderV2
import gain.aura.ui.common.Route
import gain.aura.ui.page.downloadv2.configure.DownloadDialogViewModel
import gain.aura.ui.page.downloadv2.configure.DownloadDialogViewModel.Action
import gain.aura.ui.page.downloadv2.configure.SimplifiedDownloadDialog
import gain.aura.util.ToastUtil
import gain.aura.ads.BannerAdView
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedHomePage(
    modifier: Modifier = Modifier,
    dialogViewModel: DownloadDialogViewModel,
    downloader: DownloaderV2 = koinInject(),
    onNavigateToHome: (() -> Unit) = {},
    onNavigateToSettings: (() -> Unit) = {},
    onNavigateToDownloadQueue: (() -> Unit) = {},
    currentRoute: String? = null,
) {
    val selectionState by dialogViewModel.selectionStateFlow.collectAsStateWithLifecycle()
    val simplifiedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val isHomeSelected = currentRoute == Route.HOME
    val darkThemePreference = LocalDarkTheme.current
    val isDarkTheme = darkThemePreference.isDarkTheme()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Aura") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(
                        onClick = {
                            val newTheme = if (isDarkTheme) {
                                DarkThemePreference.OFF
                            } else {
                                DarkThemePreference.ON
                            }
                            PreferenceUtil.modifyDarkThemePreference(darkThemeValue = newTheme)
                        },
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = stringResource(R.string.dark_theme),
                        )
                    }
                    IconButton(
                        onClick = {
                            onNavigateToSettings()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (isHomeSelected) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = stringResource(R.string.download),
                        )
                    },
                    label = { Text(stringResource(R.string.download)) },
                    selected = isHomeSelected,
                    onClick = {
                        if (!isHomeSelected) {
                            onNavigateToHome()
                        }
                    },
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (currentRoute == Route.DOWNLOAD_QUEUE) Icons.Filled.FileDownload else Icons.Outlined.FileDownload,
                            contentDescription = stringResource(R.string.downloads_history),
                        )
                    },
                    label = { Text(stringResource(R.string.downloads_history)) },
                    selected = currentRoute == Route.DOWNLOAD_QUEUE,
                    onClick = {
                        if (currentRoute != Route.DOWNLOAD_QUEUE) {
                            onNavigateToDownloadQueue()
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Box(modifier = Modifier.weight(1f)) {
                UrlInputPage(
                    modifier = Modifier.fillMaxSize(),
                    dialogViewModel = dialogViewModel,
                )
            }
            BannerAdView()
        }

        val sheetState by dialogViewModel.sheetStateFlow.collectAsStateWithLifecycle()
        
        when (val state = selectionState) {
            is DownloadDialogViewModel.SelectionState.FormatSelection -> {
                LaunchedEffect(state) {
                    simplifiedSheetState.show()
                }
                SimplifiedDownloadDialog(
                    videoInfo = state.info,
                    sheetState = simplifiedSheetState,
                    onDismissRequest = {
                        scope.launch { simplifiedSheetState.hide() }.invokeOnCompletion {
                            dialogViewModel.postAction(Action.Reset)
                        }
                    },
                    onDownloadStarted = {
                        dialogViewModel.postAction(Action.Reset)
                        onNavigateToDownloadQueue()
                    },
                    downloader = downloader,
                )
            }
            else -> {}
        }
        
        when (val state = sheetState) {
            is DownloadDialogViewModel.SheetState.Loading -> {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { },
                    title = { Text(stringResource(R.string.status_fetching_video_info)) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.fetching_info))
                        }
                    },
                    confirmButton = {},
                )
            }
            is DownloadDialogViewModel.SheetState.Error -> {
                val context = LocalContext.current
                LaunchedEffect(state) {
                    val errorMessage = if (state.throwable.message?.contains("YouTube") == true) {
                        context.getString(R.string.youtube_not_supported)
                    } else {
                        context.getString(R.string.fetch_info_error_msg)
                    }
                    ToastUtil.makeToast(errorMessage)
                    dialogViewModel.postAction(Action.Reset)
                }
            }
            else -> {}
        }
    }
}

