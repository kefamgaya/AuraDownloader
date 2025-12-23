package gain.aura.ui.page.settings.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.UpdateDisabled
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import gain.aura.App
import gain.aura.App.Companion.packageInfo
import gain.aura.R
import gain.aura.ui.component.BackButton
import gain.aura.ui.component.ConfirmButton
import gain.aura.ui.component.PreferenceItem
import gain.aura.util.ToastUtil

const val weblate = "https://hosted.weblate.org/engage/seal/"
const val YtdlpRepository = "https://github.com/yt-dlp/yt-dlp"
private const val PRIVACY_POLICY_URL = "https://technologygenius14.blogspot.com/p/privacy-policy-technology-genius-14.html?m=1"
private const val SUPPORT_EMAIL = "AURA@KEPHA14.DEV"
private const val TAG = "AboutPage"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(
    onNavigateBack: () -> Unit,
    onNavigateToCreditsPage: () -> Unit = {},
    onNavigateToUpdatePage: () -> Unit = {},
    onNavigateToDonatePage: () -> Unit = {},
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    val info = App.getVersionReport()
    val versionName = packageInfo.versionName

    //        infoBuilder.append("App version: $versionName ($versionCode)\n")
    //            .append("Device information: Android $release (API ${Build.VERSION.SDK_INT})\n")
    //            .append("Supported ABIs: ${Build.SUPPORTED_ABIS.contentToString()}\n")
    //            .append("\nScreen resolution: $screenHeight x $screenWidth")
    //            .append("Yt-dlp Version:
    // ${YoutubeDL.version(context.applicationContext)}").toString()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(modifier = Modifier, text = stringResource(id = R.string.about)) },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            LazyColumn(modifier = Modifier.padding(it)) {
                item {
                    PreferenceItem(
                        title = stringResource(R.string.version),
                        description = versionName,
                        icon = Icons.Outlined.Info,
                    ) {
                        clipboardManager.setText(AnnotatedString(info))
                        ToastUtil.makeToast(R.string.info_copied)
                    }
                }
                item {
                    PreferenceItem(
                        title = stringResource(R.string.privacy_policy),
                        description = stringResource(R.string.privacy_policy_desc),
                        icon = Icons.Outlined.PrivacyTip,
                    ) {
                        uriHandler.openUri(PRIVACY_POLICY_URL)
                    }
                }
                item {
                    PreferenceItem(
                        title = stringResource(R.string.support_email),
                        description = SUPPORT_EMAIL.lowercase(),
                        icon = Icons.Outlined.Email,
                    ) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "message/rfc822"
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                            putExtra(Intent.EXTRA_SUBJECT, "AURA Support Request")
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, "Send email"))
                        } catch (e: Exception) {
                            clipboardManager.setText(AnnotatedString(SUPPORT_EMAIL))
                            ToastUtil.makeToast(context.getString(R.string.email_copied))
                        }
                    }
                }
            }
        },
    )
}

@Composable
@Preview
fun AutoUpdateUnavailableDialog(onDismissRequest: () -> Unit = {}) {
    val text = stringResource(id = R.string.auto_update_disabled_msg, "F-Droid", "")
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            ConfirmButton(stringResource(id = R.string.got_it)) { onDismissRequest() }
        },
        icon = { Icon(Icons.Outlined.UpdateDisabled, null) },
        title = {
            Text(
                text = stringResource(id = R.string.feature_unavailable),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    )
}
