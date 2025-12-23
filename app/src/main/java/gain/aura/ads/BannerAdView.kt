package gain.aura.ads

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import gain.aura.App.Companion.isFDroidBuild

@Composable
fun BannerAdView(
    modifier: Modifier = Modifier,
    adUnitId: String = AdManager.getBannerAdUnitId(),
) {
    if (isFDroidBuild()) {
        return
    }
    
    val context = LocalContext.current
    
    AndroidView(
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        update = { adView ->
            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)
        },
        onRelease = { adView ->
            adView.destroy()
        }
    )
}



