package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BillingRepository
import com.example.data.PodcastDownloadManager
import com.example.data.PodcastRepository
import com.example.model.BillingTierId
import com.example.model.Podcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("iPodcast", appName)
    }

    @Test
    fun `test real podcast repository subscription toggle and persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PodcastRepository(context)

        val testPodcast = Podcast(
            id = "test_1",
            title = "Test Podcast",
            author = "Test Author",
            description = "Test Desc",
            coverUrl = "https://example.com/cover.jpg",
            feedUrl = "https://example.com/feed.xml"
        )

        val initialSubscribed = repo.podcasts.value.any { it.id == testPodcast.id }
        assertFalse(initialSubscribed)

        val isNowSub = repo.toggleSubscription(testPodcast)
        assertTrue(isNowSub)
        assertTrue(repo.podcasts.value.any { it.id == testPodcast.id })

        val isUnsub = repo.toggleSubscription(testPodcast)
        assertFalse(isUnsub)
        assertFalse(repo.podcasts.value.any { it.id == testPodcast.id })
    }

    @Test
    fun `test 50TL developer support billing purchase and promo code`() {
        val billingRepo = BillingRepository()
        assertFalse("Plus should be inactive initially", billingRepo.subscriptionState.value.isPlusActive)
        assertEquals("₺50,00", billingRepo.supportPlan.price)
        assertEquals(BillingTierId.DEVELOPER_SUPPORT_50, billingRepo.supportPlan.tierId)

        val purchaseSuccess = billingRepo.purchaseSupport()
        assertTrue(purchaseSuccess)
        assertTrue(billingRepo.subscriptionState.value.isPlusActive)
        assertEquals(BillingTierId.DEVELOPER_SUPPORT_50, billingRepo.subscriptionState.value.activeTier)
        assertNotNull(billingRepo.subscriptionState.value.purchaseToken)

        val (promoSuccess, _) = billingRepo.redeemPromoCode("DESTEK")
        assertTrue(promoSuccess)
        assertTrue(billingRepo.subscriptionState.value.isPlusActive)
    }

    @Test
    fun `test podcast download manager file deletion and storage accounting`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloadManager = PodcastDownloadManager(context)

        val tempFile = File(context.filesDir, "test_audio.mp3")
        tempFile.writeText("fake mp3 audio data")
        assertTrue(tempFile.exists())

        val deleted = downloadManager.deleteDownloadedFile(tempFile.absolutePath)
        assertTrue(deleted)
        assertFalse(tempFile.exists())
    }
}
