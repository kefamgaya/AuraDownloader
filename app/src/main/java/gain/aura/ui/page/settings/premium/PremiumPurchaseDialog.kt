package gain.aura.ui.page.settings.premium

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gain.aura.R
import gain.aura.billing.BillingManager
import gain.aura.billing.BillingManager.PurchaseResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumPurchaseDialog(
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val isPremium by BillingManager.isPremium.collectAsState()
    val purchaseResult by BillingManager.purchaseFlowResult.collectAsState()
    val restoreResult by BillingManager.restoreResult.collectAsState()
    val productPrice by BillingManager.productPrice.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Fetch product price when dialog opens
    LaunchedEffect(Unit) {
        BillingManager.fetchProductPrice()
    }
    
    // Handle purchase result
    LaunchedEffect(purchaseResult) {
        when (purchaseResult) {
            is PurchaseResult.Success -> {
                isLoading = false
                onDismissRequest()
            }
            is PurchaseResult.Error -> {
                isLoading = false
            }
            is PurchaseResult.Cancelled -> {
                isLoading = false
            }
            null -> {}
        }
    }
    
    // Handle restore result
    LaunchedEffect(restoreResult) {
        val currentRestoreResult = restoreResult
        when (currentRestoreResult) {
            is BillingManager.RestoreResult.Success -> {
                isRestoring = false
                if (currentRestoreResult.restored) {
                    onDismissRequest()
                }
            }
            is BillingManager.RestoreResult.Error -> {
                isRestoring = false
            }
            null -> {}
        }
    }
    
    // If already premium, don't show dialog
    if (isPremium) {
        onDismissRequest()
        return
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with icon and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.premium_aura),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Price display
            if (productPrice != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = productPrice ?: "",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "One-time purchase",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Description
            Text(
                text = stringResource(R.string.premium_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Benefits
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.premium_benefits),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Error messages
            if (purchaseResult is PurchaseResult.Error) {
                Text(
                    text = stringResource(R.string.purchase_error, (purchaseResult as PurchaseResult.Error).message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            val currentRestoreResult = restoreResult
            if (currentRestoreResult is BillingManager.RestoreResult.Success) {
                Text(
                    text = if (currentRestoreResult.restored) {
                        stringResource(R.string.premium_restored)
                    } else {
                        stringResource(R.string.no_purchases_found)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (currentRestoreResult.restored) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            if (currentRestoreResult is BillingManager.RestoreResult.Error) {
                Text(
                    text = stringResource(R.string.restore_error, currentRestoreResult.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Purchase button
            Button(
                onClick = {
                    isLoading = true
                    if (context is Activity) {
                        BillingManager.launchPurchaseFlow(context)
                    }
                },
                enabled = !isLoading && !isRestoring,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Processing...")
                } else {
                    Text(stringResource(R.string.purchase_premium))
                }
            }
            
            // Restore purchases button
            TextButton(
                onClick = {
                    isRestoring = true
                    BillingManager.restorePurchases(context)
                },
                enabled = !isLoading && !isRestoring,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restoring...")
                } else {
                    Text(stringResource(R.string.restore_purchases))
                }
            }
            
            // Cancel button
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
