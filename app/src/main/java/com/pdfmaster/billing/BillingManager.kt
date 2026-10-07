package com.pdfmaster.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.pdfmaster.BuildConfig
import com.pdfmaster.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Plan(val productId: String, val type: String) {
    MONTHLY("pdfmaster_pro_monthly", ProductType.SUBS),
    YEARLY("pdfmaster_pro_yearly", ProductType.SUBS),
    LIFETIME("pdfmaster_pro_lifetime", ProductType.INAPP),
}

/** Google Play Billing. Pro status is cached so Pro tools work offline after purchase. */
class BillingManager(
    context: Context,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
    private val debuggable: Boolean,
) : PurchasesUpdatedListener {

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val purchased = MutableStateFlow(prefs.cachedPro)

    val isPro: StateFlow<Boolean> = combine(purchased, prefs.debugPro) { bought, debug ->
        BuildConfig.UNLOCK_ALL || bought || (debuggable && debug)
    }
        .stateIn(scope, SharingStarted.Eagerly, BuildConfig.UNLOCK_ALL || prefs.cachedPro)

    private val _products = MutableStateFlow<Map<Plan, ProductDetails>>(emptyMap())
    val products: StateFlow<Map<Plan, ProductDetails>> = _products.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun connect() {
        if (BuildConfig.UNLOCK_ALL || client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { loadProducts(); refreshPurchases() }
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private suspend fun loadProducts() {
        val map = mutableMapOf<Plan, ProductDetails>()
        for (type in listOf(ProductType.SUBS, ProductType.INAPP)) {
            val plans = Plan.entries.filter { it.type == type }
            val params = QueryProductDetailsParams.newBuilder().setProductList(
                plans.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it.productId).setProductType(type).build() }
            ).build()
            val result = client.queryProductDetails(params)
            result.productDetailsList.orEmpty().forEach { pd -> plans.firstOrNull { it.productId == pd.productId }?.let { map[it] = pd } }
        }
        _products.value = map
    }

    suspend fun refreshPurchases() {
        if (!client.isReady) return
        val all = mutableListOf<Purchase>()
        for (type in listOf(ProductType.SUBS, ProductType.INAPP)) {
            val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build())
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
            all += result.purchasesList
        }
        handle(all, fullList = true)
    }

    fun formattedPrice(plan: Plan): String? {
        val pd = _products.value[plan] ?: return null
        return pd.oneTimePurchaseOfferDetails?.formattedPrice
            ?: pd.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
    }

    fun launch(activity: Activity, plan: Plan): Boolean {
        val pd = _products.value[plan] ?: run {
            connect()
            return false
        }
        val builder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd)
        // Prefer an offer with a free trial (the yearly plan's 7-day trial) when one exists.
        pd.subscriptionOfferDetails?.let { offers ->
            val offer = offers.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } } ?: offers.first()
            builder.setOfferToken(offer.offerToken)
        }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(builder.build())).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.let { scope.launch { handle(it, fullList = false) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _message.value = result.debugMessage
        }
    }

    private suspend fun handle(purchases: List<Purchase>, fullList: Boolean) {
        val proIds = Plan.entries.map { it.productId }.toSet()
        val active = purchases.filter { p -> p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.any { it in proIds } }
        active.filter { !it.isAcknowledged }.forEach { p ->
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
        }
        val pro = active.isNotEmpty() || (!fullList && purchased.value)
        purchased.value = pro
        prefs.cachedPro = pro
    }

    fun clearMessage() { _message.value = null }
}
