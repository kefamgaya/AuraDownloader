package gain.aura.update

import android.app.Activity
import android.content.IntentSender
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import com.google.android.play.core.ktx.isImmediateUpdateAllowed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Play In-App Updates.
 * Shows native Google Play update dialog when a new version is available.
 */
class InAppUpdateManager(
    private val activity: ComponentActivity
) : DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "InAppUpdateManager"
        // Priority threshold for immediate updates (5 = critical, 0 = low)
        private const val HIGH_PRIORITY_UPDATE = 4
        // Days before showing update prompt again for flexible updates
        private const val DAYS_FOR_FLEXIBLE_UPDATE = 3
    }
    
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    
    private var updateResultLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    
    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val bytesDownloaded = state.bytesDownloaded()
                val totalBytesToDownload = state.totalBytesToDownload()
                val progress = if (totalBytesToDownload > 0) {
                    (bytesDownloaded * 100 / totalBytesToDownload).toInt()
                } else 0
                _updateState.value = UpdateState.Downloading(progress)
                Log.d(TAG, "Downloading update: $progress%")
            }
            InstallStatus.DOWNLOADED -> {
                _updateState.value = UpdateState.Downloaded
                Log.d(TAG, "Update downloaded, ready to install")
                // For flexible updates, prompt user to restart
                completeUpdate()
            }
            InstallStatus.INSTALLING -> {
                _updateState.value = UpdateState.Installing
                Log.d(TAG, "Installing update...")
            }
            InstallStatus.INSTALLED -> {
                _updateState.value = UpdateState.Installed
                Log.d(TAG, "Update installed")
            }
            InstallStatus.FAILED -> {
                _updateState.value = UpdateState.Failed("Update failed")
                Log.e(TAG, "Update failed")
            }
            InstallStatus.CANCELED -> {
                _updateState.value = UpdateState.Idle
                Log.d(TAG, "Update canceled by user")
            }
            else -> {}
        }
    }
    
    init {
        activity.lifecycle.addObserver(this)
        registerUpdateResultLauncher()
    }
    
    private fun registerUpdateResultLauncher() {
        updateResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    Log.d(TAG, "User accepted the update")
                    _updateState.value = UpdateState.Accepted
                }
                Activity.RESULT_CANCELED -> {
                    Log.d(TAG, "User declined the update")
                    _updateState.value = UpdateState.Declined
                }
                else -> {
                    Log.e(TAG, "Update flow failed with result code: ${result.resultCode}")
                    _updateState.value = UpdateState.Failed("Update flow failed")
                }
            }
        }
    }
    
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        // Check if an update was downloaded while app was in background
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                // Update was downloaded, prompt to complete
                completeUpdate()
            }
            // For immediate updates that were interrupted
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startUpdateFlow(appUpdateInfo, AppUpdateType.IMMEDIATE)
            }
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        appUpdateManager.unregisterListener(installStateUpdatedListener)
    }
    
    /**
     * Check for available updates from Google Play.
     * Call this when the app starts or when you want to check for updates.
     */
    fun checkForUpdate() {
        Log.d(TAG, "Checking for updates...")
        _updateState.value = UpdateState.Checking
        
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            val updateAvailability = appUpdateInfo.updateAvailability()
            val updatePriority = appUpdateInfo.updatePriority()
            
            Log.d(TAG, "Update availability: $updateAvailability, priority: $updatePriority")
            Log.d(TAG, "Available version code: ${appUpdateInfo.availableVersionCode()}")
            Log.d(TAG, "Is immediate allowed: ${appUpdateInfo.isImmediateUpdateAllowed}")
            Log.d(TAG, "Is flexible allowed: ${appUpdateInfo.isFlexibleUpdateAllowed}")
            
            when (updateAvailability) {
                UpdateAvailability.UPDATE_AVAILABLE -> {
                    _updateState.value = UpdateState.Available(
                        versionCode = appUpdateInfo.availableVersionCode(),
                        priority = updatePriority
                    )
                    
                    // Determine update type based on priority
                    val updateType = when {
                        // High priority updates should be immediate (blocking)
                        updatePriority >= HIGH_PRIORITY_UPDATE && appUpdateInfo.isImmediateUpdateAllowed -> {
                            AppUpdateType.IMMEDIATE
                        }
                        // Normal updates can be flexible (background download)
                        appUpdateInfo.isFlexibleUpdateAllowed -> {
                            AppUpdateType.FLEXIBLE
                        }
                        // Fallback to immediate if flexible not allowed
                        appUpdateInfo.isImmediateUpdateAllowed -> {
                            AppUpdateType.IMMEDIATE
                        }
                        else -> null
                    }
                    
                    if (updateType != null) {
                        startUpdateFlow(appUpdateInfo, updateType)
                    } else {
                        Log.w(TAG, "No suitable update type available")
                        _updateState.value = UpdateState.Idle
                    }
                }
                UpdateAvailability.UPDATE_NOT_AVAILABLE -> {
                    Log.d(TAG, "No update available")
                    _updateState.value = UpdateState.NotAvailable
                }
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                    Log.d(TAG, "Update already in progress")
                    // Resume the update
                    startUpdateFlow(appUpdateInfo, AppUpdateType.IMMEDIATE)
                }
                else -> {
                    Log.d(TAG, "Update availability unknown: $updateAvailability")
                    _updateState.value = UpdateState.Idle
                }
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to check for updates", exception)
            _updateState.value = UpdateState.Failed(exception.message ?: "Failed to check for updates")
        }
    }
    
    private fun startUpdateFlow(appUpdateInfo: AppUpdateInfo, updateType: Int) {
        Log.d(TAG, "Starting update flow, type: ${if (updateType == AppUpdateType.IMMEDIATE) "IMMEDIATE" else "FLEXIBLE"}")
        
        // Register listener for flexible updates to track download progress
        if (updateType == AppUpdateType.FLEXIBLE) {
            appUpdateManager.registerListener(installStateUpdatedListener)
        }
        
        try {
            val updateOptions = AppUpdateOptions.newBuilder(updateType).build()
            
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                updateResultLauncher!!,
                updateOptions
            )
        } catch (e: IntentSender.SendIntentException) {
            Log.e(TAG, "Failed to start update flow", e)
            _updateState.value = UpdateState.Failed("Failed to start update: ${e.message}")
        }
    }
    
    /**
     * Complete a flexible update by restarting the app.
     * Call this after the update has been downloaded.
     */
    fun completeUpdate() {
        Log.d(TAG, "Completing update (app will restart)...")
        appUpdateManager.completeUpdate()
    }
    
    /**
     * Represents the current state of the update process.
     */
    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class Available(val versionCode: Int, val priority: Int) : UpdateState()
        object NotAvailable : UpdateState()
        object Accepted : UpdateState()
        object Declined : UpdateState()
        data class Downloading(val progress: Int) : UpdateState()
        object Downloaded : UpdateState()
        object Installing : UpdateState()
        object Installed : UpdateState()
        data class Failed(val message: String) : UpdateState()
    }
}

