package gain.aura.ui.page

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import gain.aura.ui.common.LocalDarkTheme
import gain.aura.util.DarkThemePreference
import gain.aura.util.PreferenceUtil
import gain.aura.R
import gain.aura.download.DownloaderV2
import gain.aura.ui.common.Route
import gain.aura.ui.page.downloadv2.DownloadPageV2
import gain.aura.ui.page.downloadv2.configure.DownloadDialogViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueuePage(
    modifier: Modifier = Modifier,
    dialogViewModel: DownloadDialogViewModel,
    downloader: DownloaderV2 = koinInject(),
    onNavigateToHome: (() -> Unit) = {},
    onNavigateToSettings: (() -> Unit) = {},
    onNavigateToDownloadQueue: (() -> Unit) = {},
    currentRoute: String? = null,
) {
    val isHomeSelected = currentRoute == Route.HOME
    val isDownloadQueueSelected = currentRoute == Route.DOWNLOAD_QUEUE
    val darkThemePreference = LocalDarkTheme.current
    val isDarkTheme = darkThemePreference.isDarkTheme()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_history)) },
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
                            imageVector = if (isDownloadQueueSelected) Icons.Filled.FileDownload else Icons.Outlined.FileDownload,
                            contentDescription = stringResource(R.string.downloads_history),
                        )
                    },
                    label = { Text(stringResource(R.string.downloads_history)) },
                    selected = isDownloadQueueSelected,
                    onClick = {
                        if (!isDownloadQueueSelected) {
                            onNavigateToDownloadQueue()
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        DownloadPageV2(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            onMenuOpen = {},
            dialogViewModel = dialogViewModel,
            downloader = downloader,
        )
    }
}

