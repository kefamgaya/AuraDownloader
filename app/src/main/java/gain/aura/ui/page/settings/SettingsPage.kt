package gain.aura.ui.page.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.EnergySavingsLeaf
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.ViewComfy
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import gain.aura.R
import gain.aura.ui.common.Route
import gain.aura.ui.common.intState
import gain.aura.ui.component.PreferencesHintCard
import gain.aura.ui.component.SettingItem
import gain.aura.util.EXTRACT_AUDIO
import gain.aura.util.PreferenceUtil.getBoolean
import gain.aura.util.PreferenceUtil.updateInt
import gain.aura.util.SHOW_SPONSOR_MSG

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    onNavigateBack: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onNavigateToHome: (() -> Unit)? = null,
    onNavigateToDownloadQueue: (() -> Unit)? = null,
    currentRoute: String? = null,
) {
    val context = LocalContext.current
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var showBatteryHint by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                !pm.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                false
            }
        )
    }
    val intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent()
        }
    val isActivityAvailable: Boolean =
        if (Build.VERSION.SDK_INT < 23) false
        else if (Build.VERSION.SDK_INT < 33)
            context.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .isNotEmpty()
        else
            context.packageManager
                .queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_SYSTEM_ONLY.toLong()),
                )
                .isNotEmpty()

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                showBatteryHint = !pm.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val showSponsorMessage by SHOW_SPONSOR_MSG.intState

    LaunchedEffect(Unit) { SHOW_SPONSOR_MSG.updateInt(showSponsorMessage + 1) }

    val typography = MaterialTheme.typography

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val overrideTypography =
                remember(typography) { typography.copy(headlineMedium = typography.displaySmall) }

            MaterialTheme(typography = overrideTypography) {
                LargeTopAppBar(
                    title = { Text(text = stringResource(id = R.string.settings)) },
                    scrollBehavior = scrollBehavior,
                    expandedHeight = TopAppBarDefaults.LargeAppBarExpandedHeight + 24.dp,
                )
            }
        },
        bottomBar = {
            NavigationBar {
                val isHomeSelected = currentRoute == Route.HOME
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
                            onNavigateToHome?.invoke() ?: onNavigateBack()
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
                            onNavigateToDownloadQueue?.invoke() ?: onNavigateBack()
                        }
                    },
                )
            }
        },
    ) {
        LazyColumn(modifier = Modifier, contentPadding = it) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                item {
                    AnimatedVisibility(
                        visible = showBatteryHint && isActivityAvailable,
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        PreferencesHintCard(
                            title = stringResource(R.string.battery_configuration),
                            icon = Icons.Rounded.EnergySavingsLeaf,
                            description = stringResource(R.string.battery_configuration_desc),
                        ) {
                            launcher.launch(intent)
                            showBatteryHint =
                                !pm.isIgnoringBatteryOptimizations(context.packageName)
                        }
                    }
                }
            }
            if (!showBatteryHint && showSponsorMessage > 30)
                item {
                    PreferencesHintCard(
                        title = stringResource(id = R.string.sponsor),
                        icon = Icons.Rounded.VolunteerActivism,
                        description = stringResource(id = R.string.sponsor_desc),
                    ) {
                        onNavigateTo(Route.DONATE)
                    }
                }
            item {
                SettingItem(
                    title = stringResource(id = R.string.general_settings),
                    description = stringResource(id = R.string.general_settings_desc),
                    icon = Icons.Rounded.SettingsApplications,
                ) {
                    onNavigateTo(Route.GENERAL_DOWNLOAD_PREFERENCES)
                }
            }
            item {
                SettingItem(
                    title = stringResource(id = R.string.download_directory),
                    description = stringResource(id = R.string.download_directory_desc),
                    icon = Icons.Rounded.Folder,
                ) {
                    onNavigateTo(Route.DOWNLOAD_DIRECTORY)
                }
            }
            item {
                SettingItem(
                    title = stringResource(id = R.string.format),
                    description = stringResource(id = R.string.format_settings_desc),
                    icon =
                        if (EXTRACT_AUDIO.getBoolean()) Icons.Rounded.AudioFile
                        else Icons.Rounded.VideoFile,
                ) {
                    onNavigateTo(Route.DOWNLOAD_FORMAT)
                }
            }
            item {
                SettingItem(
                    title = stringResource(id = R.string.look_and_feel),
                    description = stringResource(id = R.string.display_settings),
                    icon = Icons.Rounded.Palette,
                ) {
                    onNavigateTo(Route.APPEARANCE)
                }
            }
            item {
                SettingItem(
                    title = stringResource(R.string.trouble_shooting),
                    description = stringResource(R.string.trouble_shooting_desc),
                    icon = Icons.Rounded.BugReport,
                ) {
                    onNavigateTo(Route.TROUBLESHOOTING)
                }
            }
            item {
                SettingItem(
                    title = stringResource(id = R.string.about),
                    description = stringResource(id = R.string.about_page),
                    icon = Icons.Rounded.Info,
                ) {
                    onNavigateTo(Route.ABOUT)
                }
            }
        }
    }
}
