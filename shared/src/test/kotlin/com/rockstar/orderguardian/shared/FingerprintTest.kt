package com.rockstar.orderguardian.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class FingerprintTest {

    private val bottle = "Stainless Steel Water Bottle 1 Litre"

    @Test
    fun `same app product and price give the same fingerprint`() {
        val a = CheckoutFingerprint(MonitoredPackages.AMAZON, bottle, "₹1,299.00")
        val b = CheckoutFingerprint(MonitoredPackages.AMAZON, bottle, "₹1,299.00")
        assertEquals(a.hash, b.hash)
    }

    @Test
    fun `changed price is a different fingerprint so an old approval cannot be reused`() {
        val a = CheckoutFingerprint(MonitoredPackages.AMAZON, bottle, "₹1,299.00")
        val b = CheckoutFingerprint(MonitoredPackages.AMAZON, bottle, "₹1,499.00")
        assertNotEquals(a.hash, b.hash)
    }

    @Test
    fun `changed product is a different fingerprint`() {
        val a = CheckoutFingerprint(MonitoredPackages.AMAZON, bottle, "₹1,299.00")
        val b = CheckoutFingerprint(MonitoredPackages.AMAZON, "Cotton Double Bedsheet King Size", "₹1,299.00")
        assertNotEquals(a.hash, b.hash)
    }

    @Test
    fun `same product on a different app is a different fingerprint`() {
        val a = CheckoutFingerprint(MonitoredPackages.AMAZON, bottle, "₹1,299.00")
        val b = CheckoutFingerprint(MonitoredPackages.FLIPKART, bottle, "₹1,299.00")
        assertNotEquals(a.hash, b.hash)
    }

    @Test
    fun `price formatting differences do not change the fingerprint`() {
        val a = CheckoutFingerprint(MonitoredPackages.AMAZON, bottle, "₹1,299.00")
        val b = CheckoutFingerprint(MonitoredPackages.AMAZON, bottle, "Rs 1299.00")
        assertEquals(a.hash, b.hash)
    }

    @Test
    fun `product capitalisation and extra spaces do not change the fingerprint`() {
        val a = CheckoutFingerprint(MonitoredPackages.AMAZON, "Stainless Steel  Water Bottle", "₹999")
        val b = CheckoutFingerprint(MonitoredPackages.AMAZON, "stainless steel water bottle", "₹999")
        assertEquals(a.hash, b.hash)
    }
}
