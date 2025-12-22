@file:OptIn(ExperimentalMaterial3Api::class)

package gain.aura.ui.page.download

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gain.aura.App.Companion.applicationScope
import gain.aura.App.Companion.context
import gain.aura.Downloader
import gain.aura.Downloader.State
import gain.aura.Downloader.manageDownloadError
import gain.aura.Downloader.updatePlaylistResult
import gain.aura.R
import gain.aura.util.CUSTOM_COMMAND
import gain.aura.util.DownloadUtil
import gain.aura.util.FORMAT_SELECTION
import gain.aura.util.PLAYLIST
import gain.aura.util.PlaylistResult
import gain.aura.util.PreferenceUtil.getBoolean
import gain.aura.util.ToastUtil
import gain.aura.util.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// TODO: Refactoring for introducing multitasking and download queue management
class HomePageViewModel : ViewModel() {

    private val mutableViewStateFlow = MutableStateFlow(ViewState())
    val viewStateFlow = mutableViewStateFlow.asStateFlow()

    val videoInfoFlow = MutableStateFlow(VideoInfo())

    data class ViewState(
        val showPlaylistSelectionDialog: Boolean = false,
        val url: String = "",
        val showFormatSelectionPage: Boolean = false,
        val isUrlSharingTriggered: Boolean = false,
    )

    fun updateUrl(url: String, isUrlSharingTriggered: Boolean = false) =
        mutableViewStateFlow.update {
            it.copy(url = url, isUrlSharingTriggered = isUrlSharingTriggered)
        }

    fun startDownloadVideo() {
        val url = viewStateFlow.value.url
        Downloader.clearErrorState()
        if (CUSTOM_COMMAND.getBoolean()) {
            applicationScope.launch(Dispatchers.IO) { DownloadUtil.executeCommandInBackground(url) }
            return
        }
        if (!Downloader.isDownloaderAvailable()) return
        if (url.isBlank()) {
            ToastUtil.makeToast(context.getString(R.string.url_empty))
            return
        }
        if (PLAYLIST.getBoolean()) {
            viewModelScope.launch(Dispatchers.IO) { parsePlaylistInfo(url) }
            return
        }

        if (FORMAT_SELECTION.getBoolean()) {
            viewModelScope.launch(Dispatchers.IO) { fetchInfoForFormatSelection(url) }
            return
        }

        Downloader.getInfoAndDownload(url)
    }

    private fun fetchInfoForFormatSelection(url: String) {
        Downloader.updateState(State.FetchingInfo)
        DownloadUtil.fetchVideoInfoFromUrl(url = url)
            .onSuccess { showFormatSelectionPageOrDownload(it) }
            .onFailure {
                manageDownloadError(th = it, url = url, isFetchingInfo = true, isTaskAborted = true)
            }
        Downloader.updateState(State.Idle)
    }

    private fun parsePlaylistInfo(url: String): Unit =
        Downloader.run {
            if (!isDownloaderAvailable()) return
            clearErrorState()
            updateState(State.FetchingInfo)
            DownloadUtil.getPlaylistOrVideoInfo(url)
                .onSuccess { info ->
                    updateState(State.Idle)
                    when (info) {
                        is PlaylistResult -> {
                            showPlaylistPage(info)
                        }

                        is VideoInfo -> {
                            if (FORMAT_SELECTION.getBoolean()) {

                                showFormatSelectionPageOrDownload(info)
                            } else if (isDownloaderAvailable()) {
                                downloadVideoWithInfo(info = info)
                            }
                        }
                    }
                }
                .onFailure {
                    manageDownloadError(
                        th = it,
                        url = url,
                        isFetchingInfo = true,
                        isTaskAborted = true,
                    )
                }
        }

    private fun showPlaylistPage(playlistResult: PlaylistResult) {
        updatePlaylistResult(playlistResult)
        mutableViewStateFlow.update { it.copy(showPlaylistSelectionDialog = true) }
    }

    private fun showFormatSelectionPageOrDownload(info: VideoInfo) {
        if (info.format.isNullOrEmpty()) Downloader.downloadVideoWithInfo(info)
        else {
            videoInfoFlow.update { info }
            mutableViewStateFlow.update { it.copy(showFormatSelectionPage = true) }
        }
    }

    fun hidePlaylistDialog() {
        mutableViewStateFlow.update { it.copy(showPlaylistSelectionDialog = false) }
    }

    fun hideFormatPage() {
        mutableViewStateFlow.update { it.copy(showFormatSelectionPage = false) }
    }

    fun onShareIntentConsumed() {
        mutableViewStateFlow.update { it.copy(isUrlSharingTriggered = false) }
    }

    companion object {
        private const val TAG = "DownloadViewModel"
    }
}
