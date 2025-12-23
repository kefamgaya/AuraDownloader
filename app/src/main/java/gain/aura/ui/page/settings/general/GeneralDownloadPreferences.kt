package gain.aura.ui.page.settings.general

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import gain.aura.App
import gain.aura.R
import gain.aura.ui.common.booleanState
import gain.aura.ui.component.BackButton
import gain.aura.ui.component.PreferenceSwitch
import gain.aura.ui.page.download.NotificationPermissionDialog
import gain.aura.util.CONFIGURE
import gain.aura.util.NOTIFICATION
import gain.aura.util.NotificationUtil
import gain.aura.util.PreferenceUtil
import gain.aura.util.PreferenceUtil.getBoolean
import gain.aura.util.PreferenceUtil.updateBoolean
import gain.aura.util.PreferenceUtil.updateValue
import gain.aura.util.THUMBNAIL
import gain.aura.util.ToastUtil

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GeneralDownloadPreferences(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloadNotification by NOTIFICATION.booleanState
    var isNotificationPermissionGranted by remember {
        mutableStateOf(NotificationUtil.areNotificationsEnabled())
    }

    var showNotificationDialog by remember { mutableStateOf(false) }

    val notificationPermission =
        if (Build.VERSION.SDK_INT >= 33)
            rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS) { status ->
                // Close dialog after permission is granted or denied
                showNotificationDialog = false
                if (!status) ToastUtil.makeToast(context.getString(R.string.permission_denied))
                else isNotificationPermissionGranted = true
            }
        else null

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(id = R.string.general_settings)) },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            LazyColumn(modifier = Modifier, contentPadding = it) {
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.download_notification),
                        description =
                            stringResource(
                                id =
                                    if (isNotificationPermissionGranted)
                                        R.string.download_notification_desc
                                    else R.string.permission_denied
                            ),
                        icon =
                            if (!isNotificationPermissionGranted) Icons.Outlined.NotificationsOff
                            else if (!downloadNotification) Icons.Outlined.Notifications
                            else Icons.Outlined.NotificationsActive,
                        isChecked = downloadNotification && isNotificationPermissionGranted,
                        onClick = {
                            if (notificationPermission?.status is PermissionStatus.Denied) {
                                showNotificationDialog = true
                            } else if (isNotificationPermissionGranted) {
                                if (downloadNotification) NotificationUtil.cancelAllNotifications()
                                downloadNotification = !downloadNotification
                                PreferenceUtil.updateValue(NOTIFICATION, downloadNotification)
                            }
                        },
                    )
                }

                item {
                    var configureBeforeDownload by CONFIGURE.booleanState
                    PreferenceSwitch(
                        title = stringResource(id = R.string.settings_before_download),
                        description = stringResource(id = R.string.settings_before_download_desc),
                        icon =
                            if (configureBeforeDownload) Icons.Outlined.DoneAll
                            else Icons.Outlined.RemoveDone,
                        isChecked = configureBeforeDownload,
                        onClick = {
                            configureBeforeDownload = !configureBeforeDownload
                            PreferenceUtil.updateValue(CONFIGURE, configureBeforeDownload)
                        },
                    )
                }

                item {
                    var thumbnailSwitch by remember { mutableStateOf(THUMBNAIL.getBoolean()) }
                    PreferenceSwitch(
                        title = stringResource(id = R.string.create_thumbnail),
                        description = stringResource(id = R.string.create_thumbnail_summary),
                        icon = Icons.Outlined.Image,
                        isChecked = thumbnailSwitch,
                        onClick = {
                            thumbnailSwitch = !thumbnailSwitch
                            PreferenceUtil.updateValue(THUMBNAIL, thumbnailSwitch)
                        },
                    )
                }
            }
        },
    )
    if (showNotificationDialog) {
        NotificationPermissionDialog(
            onDismissRequest = { showNotificationDialog = false },
            onPermissionGranted = {
                // Close dialog first, then launch permission request
                showNotificationDialog = false
                notificationPermission?.launchPermissionRequest()
                NOTIFICATION.updateBoolean(true)
                downloadNotification = true
            },
        )
    }
}

@Composable
fun DialogCheckBoxItem(
    modifier: Modifier = Modifier,
    text: String,
    checked: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(value = checked, enabled = true, onValueChange = onValueChange)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            modifier = Modifier.clearAndSetSemantics {},
            checked = checked,
            onCheckedChange = onValueChange,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

