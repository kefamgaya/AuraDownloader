package gain.aura.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import gain.aura.util.PreferenceUtil.getInt
import gain.aura.util.PreferenceUtil.getLong
import gain.aura.util.PreferenceUtil.updateInt
import gain.aura.util.PreferenceUtil.updateLong
import java.util.Date

object AdManager {
    private const val TAG = "AdManager"
    
    // Ad Unit IDs
    private const val BANNER_AD_UNIT_ID = "ca-app-pub-1644643871385985/5225622469"
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1644643871385985/1626122952"
    private const val NATIVE_AD_UNIT_ID = "ca-app-pub-1644643871385985/6686877948"
    
    // Preference keys for frequency capping
    private const val PREF_LAST_INTERSTITIAL_TIME = "ad_last_interstitial_time"
    
    // Frequency capping constants
    private const val INTERSTITIAL_COOLDOWN_MINUTES = 5L
    private const val MAX_INTERSTITIALS_PER_SESSION = 3
    
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var sessionInterstitialCount = 0
    
    fun initialize(context: Context) {
        MobileAds.initialize(context) { initializationStatus ->
            val statusMap = initializationStatus.adapterStatusMap
            for (adapterClass in statusMap.keys) {
                val status = statusMap[adapterClass]
                Log.d(TAG, "Adapter: $adapterClass, Status: ${status?.initializationState}")
            }
        }
        
        // Preload interstitial ad
        loadInterstitialAd(context)
    }
    
    fun getBannerAdUnitId(): String = BANNER_AD_UNIT_ID
    
    fun getNativeAdUnitId(): String = NATIVE_AD_UNIT_ID
    
    fun createAdRequest(): AdRequest {
        return AdRequest.Builder().build()
    }
    
    // Interstitial Ad Management
    fun loadInterstitialAd(context: Context) {
        if (isLoadingInterstitial || interstitialAd != null) {
            return
        }
        
        isLoadingInterstitial = true
        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            createAdRequest(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded")
                    interstitialAd = ad
                    isLoadingInterstitial = false
                    
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "Interstitial ad dismissed")
                            interstitialAd = null
                            // Preload next ad
                            loadInterstitialAd(context)
                        }
                        
                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            Log.e(TAG, "Interstitial ad failed to show: ${error.message}")
                            interstitialAd = null
                            loadInterstitialAd(context)
                        }
                        
                        override fun onAdShowedFullScreenContent() {
                            Log.d(TAG, "Interstitial ad showed")
                            sessionInterstitialCount++
                        }
                    }
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Interstitial ad failed to load: ${error.message}")
                    isLoadingInterstitial = false
                }
            }
        )
    }
    
    fun showInterstitialAd(activity: Activity): Boolean {
        // Check frequency capping
        if (!shouldShowInterstitial()) {
            return false
        }
        
        val ad = interstitialAd
        if (ad != null) {
            ad.show(activity)
            updateInterstitialShown()
            return true
        } else {
            // Preload for next time
            loadInterstitialAd(activity)
            return false
        }
    }
    
    private fun shouldShowInterstitial(): Boolean {
        // Check session limit
        if (sessionInterstitialCount >= MAX_INTERSTITIALS_PER_SESSION) {
            return false
        }
        
        // Check cooldown period
        val lastShownTime = PREF_LAST_INTERSTITIAL_TIME.getLong(0L)
        val currentTime = Date().time
        val timeSinceLastAd = (currentTime - lastShownTime) / (1000 * 60) // minutes
        
        if (timeSinceLastAd < INTERSTITIAL_COOLDOWN_MINUTES) {
            return false
        }
        
        return true
    }
    
    private fun updateInterstitialShown() {
        PREF_LAST_INTERSTITIAL_TIME.updateLong(Date().time)
    }
    
    fun resetSession() {
        sessionInterstitialCount = 0
    }
    
    fun isInterstitialAdLoaded(): Boolean {
        return interstitialAd != null
    }
    
    fun isInterstitialAdLoading(): Boolean {
        return isLoadingInterstitial
    }
}

