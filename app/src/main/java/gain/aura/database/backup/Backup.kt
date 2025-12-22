package gain.aura.database.backup

import gain.aura.database.objects.CommandTemplate
import gain.aura.database.objects.DownloadedVideoInfo
import gain.aura.database.objects.OptionShortcut
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val templates: List<CommandTemplate>? = null,
    val shortcuts: List<OptionShortcut>? = null,
    val downloadHistory: List<DownloadedVideoInfo>? = null,
)
