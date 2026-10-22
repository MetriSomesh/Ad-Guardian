package com.rockstar.orderguardian.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckoutRulesTest {

    @Test
    fun `amazon checkout screen with place order and total triggers`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.AMAZON,
            texts = listOf(
                "Deliver to this address",
                "Stainless Steel Water Bottle 1 Litre Leak Proof",
                "Order Summary",
                "Total: ₹1,299.00",
                "Place Order",
            ),
        )
        val result = CheckoutRules.evaluate(snapshot)
        assertEquals(ScreenKind.CHECKOUT, result.kind)
        assertEquals("₹1,299.00", result.priceText)
        assertTrue(result.matchedPhrases.contains("place order"))
    }

    @Test
    fun `flipkart payment screen with amount payable triggers`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.FLIPKART,
            texts = listOf(
                "Payment Methods",
                "Cotton Double Bedsheet King Size With Pillow Covers",
                "Amount Payable ₹2,499",
                "Pay Now",
            ),
        )
        val result = CheckoutRules.evaluate(snapshot)
        assertEquals(ScreenKind.CHECKOUT, result.kind)
        assertEquals("₹2,499", result.priceText)
    }

    @Test
    fun `amazon product page with buy now and price does not trigger`() {
        // Buy Now + a price is a product page, not a checkout. Ejecting here
        // would punish ordinary browsing.
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.AMAZON,
            texts = listOf(
                "Stainless Steel Water Bottle 1 Litre Leak Proof",
                "₹899.00",
                "Buy Now",
                "Add to Cart",
                "Free Delivery",
            ),
        )
        assertEquals(ScreenKind.NOT_CHECKOUT, CheckoutRules.evaluate(snapshot).kind)
    }

    @Test
    fun `flipkart product page does not trigger`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.FLIPKART,
            texts = listOf(
                "Men Cotton Casual Shirt Blue Checkered Full Sleeve",
                "₹1,099",
                "Buy Now",
                "Add to Cart",
            ),
        )
        assertEquals(ScreenKind.NOT_CHECKOUT, CheckoutRules.evaluate(snapshot).kind)
    }

    @Test
    fun `checkout words in a non shopping app do not trigger`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.android.chrome",
            texts = listOf("Place Order", "Total: ₹500.00"),
        )
        assertEquals(ScreenKind.NOT_CHECKOUT, CheckoutRules.evaluate(snapshot).kind)
    }

    @Test
    fun `two strong phrases trigger even when no price is visible`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.AMAZON,
            texts = listOf("Order Summary", "Use This Payment Method", "Continue"),
        )
        assertEquals(ScreenKind.CHECKOUT, CheckoutRules.evaluate(snapshot).kind)
    }

    @Test
    fun `single strong phrase without price does not trigger`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.FLIPKART,
            texts = listOf("Price Details", "Help Centre", "My Account"),
        )
        assertEquals(ScreenKind.NOT_CHECKOUT, CheckoutRules.evaluate(snapshot).kind)
    }

    @Test
    fun `order confirmation screen is recognised so the approval can be consumed`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.AMAZON,
            texts = listOf("Order Placed, Thank You", "Your order has been placed"),
        )
        assertEquals(ScreenKind.ORDER_CONFIRMATION, CheckoutRules.evaluate(snapshot).kind)
    }

    @Test
    fun `product title is captured from checkout screen`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.AMAZON,
            texts = listOf(
                "Deliver to this address",
                "Stainless Steel Water Bottle 1 Litre Leak Proof",
                "Total: ₹1,299.00",
                "Place Order",
            ),
        )
        assertEquals(
            "Stainless Steel Water Bottle 1 Litre Leak Proof",
            CheckoutRules.evaluate(snapshot).productText,
        )
    }

    @Test
    fun `total price prefers the total line over item prices`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.AMAZON,
            texts = listOf("Item ₹499.00", "Delivery ₹40.00", "Order Total: ₹539.00"),
        )
        assertEquals("₹539.00", CheckoutRules.findTotalPrice(snapshot))
    }

    @Test
    fun `hindi checkout phrase triggers with a price`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.FLIPKART,
            texts = listOf("कुल राशि ₹750", "भुगतान करें"),
        )
        val result = CheckoutRules.evaluate(snapshot)
        assertEquals(ScreenKind.CHECKOUT, result.kind)
        assertNotNull(result.priceText)
    }

    @Test
    fun `amazon india checkout screen with place your order and pay triggers`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.AMAZON_IN,
            texts = listOf(
                "Select a delivery address",
                "Wireless Bluetooth Earbuds with Noise Cancellation",
                "Order Total: ₹1,499.00",
                "Pay on Delivery",
                "Place your order and pay",
            ),
        )
        val result = CheckoutRules.evaluate(snapshot)
        assertEquals(ScreenKind.CHECKOUT, result.kind)
        assertEquals("₹1,499.00", result.priceText)
        assertTrue(result.matchedPhrases.contains("place your order and pay"))
        assertTrue(result.matchedPhrases.contains("pay on delivery"))
    }

    @Test
    fun `browser shopify checkout screen with complete order triggers`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.CHROME,
            texts = listOf(
                "Shipping Address",
                "Trending Orthopedic Slippers for Pain Relief",
                "Total Amount ₹899.00",
                "Cash on Delivery",
                "Complete Order",
            ),
        )
        val result = CheckoutRules.evaluateWeb(snapshot)
        assertEquals(ScreenKind.CHECKOUT, result.kind)
        assertEquals("₹899.00", result.priceText)
        assertTrue(result.matchedPhrases.contains("complete order"))
    }

    @Test
    fun `ordinary news article in browser does not trigger checkout`() {
        val snapshot = ScreenSnapshot(
            packageName = MonitoredPackages.CHROME,
            texts = listOf(
                "Daily News Update",
                "Government Announces New Agricultural Policy",
                "Read full story",
                "Comments",
                "Share",
            ),
        )
        val result = CheckoutRules.evaluateWeb(snapshot)
        assertEquals(ScreenKind.NOT_CHECKOUT, result.kind)
    }
}
