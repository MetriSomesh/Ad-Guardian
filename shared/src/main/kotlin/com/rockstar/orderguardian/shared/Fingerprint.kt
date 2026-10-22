package com.rockstar.orderguardian.shared

import java.security.MessageDigest

/**
 * Identity of one specific purchase attempt: which app, what product, at what
 * price. An approval applies to exactly one fingerprint. Change the product or
 * the price and the fingerprint changes, so an old approval cannot be reused
 * for a different order.
 */
data class CheckoutFingerprint(
    val packageName: String,
    val productText: String?,
    val priceText: String?,
) {
    /** SHA-256 (hex) of the normalised components. Safe to log and to send to the backend. */
    val hash: String by lazy {
        val material = listOf(
            packageName,
            TextNormaliser.normalise(productText ?: ""),
            // Price identity is its digits: "₹1,299.00", "Rs 1299" and "₹ 1,299" are the same price.
            (priceText ?: "").replace(Regex("[^0-9.]"), ""),
        ).joinToString("|")
        MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
