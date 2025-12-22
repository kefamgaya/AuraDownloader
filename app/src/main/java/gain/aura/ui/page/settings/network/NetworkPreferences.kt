package gain.aura.ui.page.settings.network

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.SignalCellular4Bar
import androidx.compose.material.icons.outlined.SignalCellularConnectedNoInternet4Bar
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
import gain.aura.ui.common.booleanState
import gain.aura.ui.component.BackButton
import gain.aura.ui.component.PreferenceItem
import gain.aura.ui.component.PreferenceSwitch
import gain.aura.util.CELLULAR_DOWNLOAD
import gain.aura.util.PreferenceUtil.updateValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkPreferences(navigateToCookieProfilePage: () -> Unit = {}, onNavigateBack: () -> Unit) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )


    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(modifier = Modifier, text = stringResource(id = R.string.network)) },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            LazyColumn(contentPadding = it) {
                item {
                    var isDownloadWithCellularEnabled by CELLULAR_DOWNLOAD.booleanState
                    PreferenceSwitch(
                        title = stringResource(R.string.download_with_cellular),
                        description = stringResource(R.string.download_with_cellular_desc),
                        icon =
                            if (isDownloadWithCellularEnabled) Icons.Outlined.SignalCellular4Bar
                            else Icons.Outlined.SignalCellularConnectedNoInternet4Bar,
                        isChecked = isDownloadWithCellularEnabled,
                        onClick = {
                            isDownloadWithCellularEnabled = !isDownloadWithCellularEnabled
                            updateValue(CELLULAR_DOWNLOAD, isDownloadWithCellularEnabled)
                        },
                    )
                }
                item {
                    PreferenceItem(
                        title = stringResource(R.string.cookies),
                        description = stringResource(R.string.cookies_desc),
                        icon = Icons.Outlined.Cookie,
                        onClick = { navigateToCookieProfilePage() },
                    )
                }
            }
        },
    )

}
