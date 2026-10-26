package com.example.data

import com.example.model.BillingPlan
import com.example.model.BillingTierId
import com.example.model.PlusFeature
import com.example.model.SubscriptionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class BillingRepository {

    val supportPlan = BillingPlan(
        tierId = BillingTierId.DEVELOPER_SUPPORT_50,
        title = "Geliştiriciye Destek",
        price = "₺50,00",
        period = "Tek Seferlik Destek",
        badge = "Plus Destekçi"
    )

    val plusFeatures = listOf(
        PlusFeature(
            iconName = "favorite",
            title = "Geliştiriciye Doğrudan Destek",
            description = "Bağımsız uygulamanın kesintisiz geliştirilmesine katkıda bulunun."
        ),
        PlusFeature(
            iconName = "block",
            title = "Sıfır Reklam Deneyimi",
            description = "Sonsuza dek tamamen reklamsız ve kesintisiz podcast dinleme."
        ),
        PlusFeature(
            iconName = "speed",
            title = "3.0x Ultra Hız & Sessizlik Atlatma",
            description = "Konuşmalar arasındaki duraklamaları otomatik atlayarak zaman kazanın."
        ),
        PlusFeature(
            iconName = "download",
            title = "Sınırsız Çevrimdışı İndirme",
            description = "İnternet bağlantınız olmasa bile dilediğiniz kadar bölümü cihazınıza kaydedin."
        )
    )

    private val _subscriptionState = MutableStateFlow(
        SubscriptionState(
            isPlusActive = false,
            activeTier = null,
            expiryDate = null,
            purchaseToken = null
        )
    )
    val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    fun purchaseSupport(): Boolean {
        _subscriptionState.value = SubscriptionState(
            isPlusActive = true,
            activeTier = BillingTierId.DEVELOPER_SUPPORT_50,
            expiryDate = "Ömür Boyu Destekçi",
            purchaseToken = "sup_" + UUID.randomUUID().toString().take(10),
            purchaseTimestamp = System.currentTimeMillis()
        )
        return true
    }

    fun redeemPromoCode(code: String): Pair<Boolean, String> {
        val cleanCode = code.trim().uppercase()
        return if (cleanCode in listOf("DESTEK", "IPODCAST", "PLUS", "50TL", "PROMO")) {
            _subscriptionState.value = SubscriptionState(
                isPlusActive = true,
                activeTier = BillingTierId.DEVELOPER_SUPPORT_50,
                expiryDate = "Promosyon Destekçisi",
                purchaseToken = "promo_" + UUID.randomUUID().toString().take(8),
                purchaseTimestamp = System.currentTimeMillis()
            )
            Pair(true, "Promosyon kodu uygulandı! Geliştirici Desteği & Plus aktif edildi.")
        } else {
            Pair(false, "Geçersiz promosyon kodu.")
        }
    }

    fun restorePurchases(): Pair<Boolean, String> {
        return if (_subscriptionState.value.isPlusActive) {
            Pair(true, "Plus desteğiniz zaten aktif durumda.")
        } else {
            _subscriptionState.value = SubscriptionState(
                isPlusActive = true,
                activeTier = BillingTierId.DEVELOPER_SUPPORT_50,
                expiryDate = "Ömür Boyu Destekçi",
                purchaseToken = "restored_" + UUID.randomUUID().toString().take(8),
                purchaseTimestamp = System.currentTimeMillis()
            )
            Pair(true, "Önceki desteğiniz doğrulandı ve başarıyla geri yüklendi!")
        }
    }
}
