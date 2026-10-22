package com.rockstar.orderguardian.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdRulesTest {

    @Test
    fun `youtube shorts style ad with shop now is skipped when scrollable`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.YOUTUBE,
            texts = listOf("Ad", "Portable Mixer Grinder", "Shop Now", "12K views"),
            hasScrollableContainer = true,
        )
        val result = AdRules.evaluate(snapshot)
        assertEquals(AdAction.SKIP_SCROLL, result.action)
        assertEquals("shop now", result.matchedShopCta)
    }

    @Test
    fun `instagram sponsored post with shop now is skipped when scrollable`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.INSTAGRAM,
            texts = listOf("Sponsored", "Kitchen Storage Boxes Set of 6", "Shop Now"),
            hasScrollableContainer = true,
        )
        assertEquals(AdAction.SKIP_SCROLL, AdRules.evaluate(snapshot).action)
    }

    @Test
    fun `facebook community post ad with shop button is noted or skipped by scrollability`() {
        val scrollable = ScreenSnapshot(
            packageName = MonitoredPackages.FACEBOOK,
            texts = listOf("Sponsored", "Suggested for you", "Herbal Hair Oil 200ml", "Shop Now"),
            hasScrollableContainer = true,
        )
        assertEquals(AdAction.SKIP_SCROLL, AdRules.evaluate(scrollable).action)

        val fullScreen = scrollable.copy(hasScrollableContainer = false)
        assertEquals(AdAction.NOTE_ONLY, AdRules.evaluate(fullScreen).action)
    }

    @Test
    fun `whatsapp status ad with shop cta is noted when not scrollable`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.WHATSAPP,
            texts = listOf("Advertisement", "Shop Now"),
            hasScrollableContainer = false,
        )
        assertEquals(AdAction.NOTE_ONLY, AdRules.evaluate(snapshot).action)
    }

    @Test
    fun `ordinary youtube video is not treated as an ad`() {
        // "read" contains the letters "ad": word-boundary matching must save us here.
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.YOUTUBE,
            texts = listOf("How to read a railway timetable", "Watch later", "Share"),
            hasScrollableContainer = true,
        )
        assertEquals(AdAction.NONE, AdRules.evaluate(snapshot).action)
    }

    @Test
    fun `ad without a shop cta is left alone`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.INSTAGRAM,
            texts = listOf("Sponsored", "New web series streaming Friday", "Watch trailer"),
            hasScrollableContainer = true,
        )
        assertEquals(AdAction.NONE, AdRules.evaluate(snapshot).action)
    }

    @Test
    fun `shopping page reached from an ad is ejected`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.FACEBOOK,
            texts = listOf("Shop Now", "Order Summary", "Total: ₹999.00", "Place Order"),
            hasScrollableContainer = false,
        )
        assertEquals(AdAction.EJECT_BACK, AdRules.evaluate(snapshot).action)
    }

    @Test
    fun `ads in a shopping app are not the ad rules' job`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.AMAZON,
            texts = listOf("Sponsored", "Shop Now"),
            hasScrollableContainer = true,
        )
        assertEquals(AdAction.NONE, AdRules.evaluate(snapshot).action)
    }
}
