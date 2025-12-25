package gain.aura.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import gain.aura.util.PreferenceUtil
import gain.aura.util.PreferenceUtil.getBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object BillingManager : DefaultLifecycleObserver, PurchasesUpdatedListener {
    private const val TAG = "BillingManager"
    private const val PREMIUM_PRODUCT_ID = "premium_aura"
    private const val PREF_IS_PREMIUM = "is_premium"
    private const val MAX_RETRIES = 3
    
    private var billingClient: BillingClient? = null
    private var appContext: Context? = null
    private var retryCount = 0
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()
    
    private val _purchaseFlowResult = MutableStateFlow<PurchaseResult?>(null)
    val purchaseFlowResult: StateFlow<PurchaseResult?> = _purchaseFlowResult.asStateFlow()
    
    private val _restoreResult = MutableStateFlow<RestoreResult?>(null)
    val restoreResult: StateFlow<RestoreResult?> = _restoreResult.asStateFlow()
    
    private val _productPrice = MutableStateFlow<String?>(null)
    val productPrice: StateFlow<String?> = _productPrice.asStateFlow()
    
    sealed class PurchaseResult {
        data class Success(val purchase: Purchase) : PurchaseResult()
        data class Error(val message: String) : PurchaseResult()
        object Cancelled : PurchaseResult()
    }
    
    sealed class RestoreResult {
        data class Success(val restored: Boolean) : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }
    
    fun initialize(context: Context) {
        appContext = context.applicationContext
        retryCount = 0
        
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
        
        startConnection()
    }
    
    private fun startConnection() {
        val context = appContext ?: return
        
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                Log.d(TAG, "Billing setup finished: code=${billingResult.responseCode}, msg=${billingResult.debugMessage}")
                
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing setup successful - checking premium status and fetching price")
                    retryCount = 0
                    checkPremiumStatus(context)
                    fetchProductPrice()
                } else {
                    Log.e(TAG, "Billing setup failed: code=${billingResult.responseCode}, msg=${billingResult.debugMessage}")
                    // Try to reconnect if it's a recoverable error
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        Log.d(TAG, "Retrying connection (attempt $retryCount of $MAX_RETRIES)")
                        CoroutineScope(Dispatchers.IO).launch {
                            kotlinx.coroutines.delay(1000L * retryCount)
                            startConnection()
                        }
                    }
                }
            }
            
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                // Try to reconnect
                if (retryCount < MAX_RETRIES) {
                    retryCount++
                    Log.d(TAG, "Attempting reconnection (attempt $retryCount of $MAX_RETRIES)")
                    CoroutineScope(Dispatchers.IO).launch {
                        kotlinx.coroutines.delay(1000L * retryCount)
                        startConnection()
                    }
                }
            }
        })
    }
    
    private fun checkPremiumStatus(context: Context) {
        val client = billingClient ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
                
                client.queryPurchasesAsync(params) { billingResult, purchasesList ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val isPremium = purchasesList.any { purchase: Purchase ->
                            purchase.products.contains(PREMIUM_PRODUCT_ID) && 
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                        }
                        
                        _isPremium.value = isPremium
                        PreferenceUtil.encodeBoolean(PREF_IS_PREMIUM, isPremium)
                        
                        Log.d(TAG, "Premium status: $isPremium")
                    } else {
                        // Fallback to stored preference
                        _isPremium.value = PREF_IS_PREMIUM.getBoolean(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking premium status", e)
                // Fallback to stored preference
                _isPremium.value = PREF_IS_PREMIUM.getBoolean(false)
            }
        }
    }
    
    fun launchPurchaseFlow(activity: Activity) {
        val client = billingClient ?: run {
            _purchaseFlowResult.value = PurchaseResult.Error("Billing client not initialized")
            return
        }
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        
        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            val listSize = (productDetailsList as? java.util.List<*>)?.size ?: 0
            Log.d(TAG, "queryProductDetailsAsync response: code=${billingResult.responseCode}, message=${billingResult.debugMessage}")
            Log.d(TAG, "Product details list size: $listSize")
            
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                // Access first element using Java List methods (compatible with billing-ktx)
                val productDetails: ProductDetails? = try {
                    val list = productDetailsList as? java.util.List<ProductDetails>
                    if (list != null && list.size > 0) {
                        list.get(0)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error accessing product details list", e)
                    null
                }
                
                if (productDetails != null) {
                    Log.d(TAG, "Product found: ${productDetails.productId}, title: ${productDetails.title}")
                    // For one-time products, don't call setOfferToken
                    val productDetailsParamsList = listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .build()
                    )
                    
                    val billingFlowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(productDetailsParamsList)
                        .build()
                    
                    val responseCode = client.launchBillingFlow(activity, billingFlowParams).responseCode
                    
                    if (responseCode != BillingClient.BillingResponseCode.OK) {
                        _purchaseFlowResult.value = PurchaseResult.Error("Failed to launch purchase flow (code: $responseCode)")
                    }
                } else {
                    Log.e(TAG, "Product '$PREMIUM_PRODUCT_ID' not found. Make sure: 1) App is published to testing track, 2) Product is active, 3) License testers configured")
                    _purchaseFlowResult.value = PurchaseResult.Error("Product not found. Please ensure app is properly set up in Play Console.")
                }
            } else {
                val errorMsg = when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "Billing service disconnected"
                    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Billing service unavailable"
                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Billing unavailable (check Play Store)"
                    BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "Developer error - check Play Console setup"
                    BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "Feature not supported"
                    else -> billingResult.debugMessage ?: "Failed to query product (code: ${billingResult.responseCode})"
                }
                Log.e(TAG, "Query product failed: $errorMsg")
                _purchaseFlowResult.value = PurchaseResult.Error(errorMsg)
            }
        }
    }
    
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.products.contains(PREMIUM_PRODUCT_ID)) {
                    handlePurchase(purchase)
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _purchaseFlowResult.value = PurchaseResult.Cancelled
        } else {
            _purchaseFlowResult.value = PurchaseResult.Error(billingResult.debugMessage ?: "Purchase failed")
        }
    }
    
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            }
            
            _isPremium.value = true
            PreferenceUtil.encodeBoolean(PREF_IS_PREMIUM, true)
            _purchaseFlowResult.value = PurchaseResult.Success(purchase)
            Log.d(TAG, "Premium purchase successful")
        }
    }
    
    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged")
            } else {
                Log.e(TAG, "Failed to acknowledge purchase: ${billingResult.debugMessage}")
            }
        }
    }
    
    fun restorePurchases(context: Context) {
        val client = billingClient ?: run {
            _restoreResult.value = RestoreResult.Error("Billing client not initialized")
            return
        }
        
        if (!client.isReady) {
            _restoreResult.value = RestoreResult.Error("Billing client not ready")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
                
                client.queryPurchasesAsync(params) { billingResult, purchasesList ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val premiumPurchase: Purchase? = purchasesList.firstOrNull { purchase: Purchase ->
                            purchase.products.contains(PREMIUM_PRODUCT_ID) && 
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                        }
                        
                        if (premiumPurchase != null) {
                            // Acknowledge if needed
                            if (!premiumPurchase.isAcknowledged) {
                                acknowledgePurchase(premiumPurchase)
                            }
                            
                            _isPremium.value = true
                            PreferenceUtil.encodeBoolean(PREF_IS_PREMIUM, true)
                            _restoreResult.value = RestoreResult.Success(true)
                            Log.d(TAG, "Premium restored successfully")
                        } else {
                            _restoreResult.value = RestoreResult.Success(false)
                            Log.d(TAG, "No premium purchase found to restore")
                        }
                    } else {
                        _restoreResult.value = RestoreResult.Error(
                            billingResult.debugMessage ?: "Failed to query purchases"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring purchases", e)
                _restoreResult.value = RestoreResult.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun isPremium(): Boolean {
        return _isPremium.value
    }
    
    fun fetchProductPrice() {
        val client = billingClient ?: return
        
        if (!client.isReady) {
            return
        }
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        
        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            val listSize = (productDetailsList as? java.util.List<*>)?.size ?: 0
            Log.d(TAG, "fetchProductPrice response: code=${billingResult.responseCode}, listSize=$listSize")
            
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                // Access first element using Java List methods (compatible with billing-ktx)
                val productDetails: ProductDetails? = try {
                    val list = productDetailsList as? java.util.List<ProductDetails>
                    if (list != null && list.size > 0) {
                        list.get(0)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error accessing product details list", e)
                    null
                }
                
                if (productDetails != null) {
                    try {
                        // For INAPP products, access one-time purchase offer details
                        val oneTimePurchaseOfferDetails = productDetails.getOneTimePurchaseOfferDetails()
                        val price = oneTimePurchaseOfferDetails?.formattedPrice
                        _productPrice.value = price
                        Log.d(TAG, "Product price fetched: $price")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error accessing product price", e)
                        _productPrice.value = null
                    }
                } else {
                    Log.w(TAG, "No product details returned for '$PREMIUM_PRODUCT_ID'")
                }
            } else {
                Log.e(TAG, "Failed to fetch product price: code=${billingResult.responseCode}, msg=${billingResult.debugMessage}")
            }
        }
    }
    
}
