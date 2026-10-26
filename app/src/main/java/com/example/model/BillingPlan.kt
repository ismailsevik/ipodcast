package com.example.model

enum class BillingTierId(val sku: String) {
    DEVELOPER_SUPPORT_50("ipodcast_plus_support_50")
}

data class BillingPlan(
    val tierId: BillingTierId,
    val title: String,
    val price: String,
    val period: String,
    val badge: String? = null
)

data class SubscriptionState(
    val isPlusActive: Boolean = false,
    val activeTier: BillingTierId? = null,
    val expiryDate: String? = null,
    val purchaseToken: String? = null,
    val purchaseTimestamp: Long = 0L
)

data class PlusFeature(
    val iconName: String,
    val title: String,
    val description: String
)
