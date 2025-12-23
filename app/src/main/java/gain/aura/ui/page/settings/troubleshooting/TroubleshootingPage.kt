package gain.aura.ui.page.settings.troubleshooting

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import gain.aura.R
import gain.aura.ui.common.Route
import gain.aura.ui.component.PreferenceItem
import gain.aura.ui.component.PreferenceSubtitle
import gain.aura.ui.page.settings.BasePreferencePage

@Composable
fun TroubleShootingPage(
    modifier: Modifier = Modifier,
    onNavigateTo: (String) -> Unit,
    onBack: () -> Unit,
) {
    BasePreferencePage(
        modifier = modifier,
        title = stringResource(R.string.trouble_shooting),
        onBack = onBack,
    ) {
        LazyColumn(contentPadding = it) {
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
