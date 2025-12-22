package gain.aura.ui.page.settings.troubleshooting

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import gain.aura.App
import gain.aura.R
import gain.aura.ui.common.Route
import gain.aura.ui.component.PreferenceItem
import gain.aura.ui.component.PreferenceSubtitle
import gain.aura.ui.page.settings.BasePreferencePage
import gain.aura.util.PreferenceUtil
import gain.aura.util.PreferenceUtil.getString
import gain.aura.util.UpdateUtil
import gain.aura.util.YT_DLP_VERSION
import gain.aura.util.makeToast
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TroubleShootingPage(
    modifier: Modifier = Modifier,
    onNavigateTo: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BasePreferencePage(
        modifier = modifier,
        title = stringResource(R.string.trouble_shooting),
        onBack = onBack,
    ) {
        LazyColumn(contentPadding = it) {
            item { PreferenceSubtitle(text = stringResource(R.string.update)) }
            item {
                var isUpdating by remember { mutableStateOf(false) }
                var ytdlpVersion by remember {
                    mutableStateOf(
                        YoutubeDL.getInstance().version(context.applicationContext)
                            ?: context.getString(R.string.ytdlp_update)
                    )
                }
                PreferenceItem(
                    title = stringResource(id = R.string.ytdlp_update_action),
                    description = ytdlpVersion,
                    leadingIcon = {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.padding(start = 8.dp, end = 16.dp)
                                        .size(24.dp)
                                        .padding(2.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Update,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 8.dp, end = 16.dp).size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                    isUpdating = true
                                    UpdateUtil.updateYtDlp()
                                    ytdlpVersion = YT_DLP_VERSION.getString()
                                }
                                .onFailure { th ->
                                    th.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        context.makeToast(
                                            App.context.getString(R.string.yt_dlp_update_fail)
                                        )
                                    }
                                }
                                .onSuccess {
                                    withContext(Dispatchers.Main) {
                                        context.makeToast(
                                            context.getString(R.string.yt_dlp_up_to_date) +
                                                " (${YT_DLP_VERSION.getString()})"
                                        )
                                    }
                                }
                            isUpdating = false
                        }
                    },
                    onClickLabel = stringResource(id = R.string.update),
                )
            }

            item { PreferenceSubtitle(text = stringResource(R.string.network)) }
            item {
                PreferenceItem(
                    title = stringResource(R.string.cookies),
                    description = stringResource(R.string.cookies_desc),
                    icon = Icons.Outlined.Cookie,
                    onClick = { onNavigateTo(Route.COOKIE_PROFILE) },
                )
            }
        }
    }
}
