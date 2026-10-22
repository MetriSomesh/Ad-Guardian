package com.rockstar.orderguardian.shared

/** What kind of screen the detector believes this is, for a shopping app. */
enum class ScreenKind { NOT_CHECKOUT, CHECKOUT, ORDER_CONFIRMATION }

data class CheckoutEvaluation(
    val kind: ScreenKind,
    /** Checkout phrases actually seen on screen (normalised), for logging and debugging rules. */
    val matchedPhrases: List<String>,
    /** First total/payable amount found, as displayed, e.g. "₹1,299.00". Null when no total was visible. */
    val priceText: String?,
    /** Best-effort product title captured from the screen; null when nothing suitable was visible. */
    val productText: String?,
)

/**
 * Checkout detection for Amazon / Flipkart.
 *
 * A screen is a checkout only when at least two independent signals agree:
 *  1. the foreground package is a monitored shopping app, AND
 *  2. at least one strong checkout phrase is visible, AND
 *  3. a total/price pattern is visible OR a second strong phrase is visible.
 *
 * "Buy Now" on its own is deliberately NOT a strong phrase: it appears on
 * ordinary product pages, and ejecting a man for looking at a product page
 * would make the app unusable within a day. Buy Now is the *start* of a
 * checkout; the gate fires on the address/payment/place-order screens that
 * follow it.
 */
object CheckoutRules {

    val STRONG_CHECKOUT_PHRASES = listOf(
        // English phrases seen on Amazon India / Flipkart checkout, address and payment steps.
        "place order",
        "place your order",
        "place your order and pay",
        "proceed to checkout",
        "proceed to buy",
        "continue to payment",
        "go to payment",
        "make payment",
        "pay now",
        "deliver to this address",
        "deliver here",
        "select a delivery address",
        "choose a delivery address",
        "shipping address",
        "delivery address",
        "order summary",
        "review your order",
        "price details",
        "payment method",
        "payment methods",
        "select a payment method",
        "choose a payment method",
        "cash on delivery",
        "pay on delivery",
        "amount payable",
        "total amount",
        "order total",
        "items total",
        "use this payment method",
        "enter card details",
        "pay securely",
        "complete order",
        "continue to shipping",
        "proceed to pay",
        "confirm order",
        "place your order now",
        "swipe to place order",
        "slide to place order",
        "slide to pay",
        // Hindi equivalents.
        "ऑर्डर करें",
        "अपना ऑर्डर दें",
        "भुगतान करें",
        "भुगतान जारी रखें",
        "इस पते पर भेजें",
        "कुल राशि",
        "डिलीवरी पर भुगतान",
    )

    private val CONFIRMATION_PHRASES = listOf(
        "order placed",
        "order has been placed",
        "your order has been placed",
        "order confirmed",
        "thank you for your order",
        "thanks for shopping",
        "ऑर्डर हो गया",
        "ऑर्डर कन्फर्म",
    )

    /** Button / chrome words that are never a product title. */
    private val NON_PRODUCT_PHRASES = STRONG_CHECKOUT_PHRASES + CONFIRMATION_PHRASES + listOf(
        "buy now", "add to cart", "add to basket", "go to cart", "see all buying options",
        "quantity", "qty", "in stock", "free delivery", "delivery", "returns", "wishlist",
        "share", "search", "back", "home", "account", "orders", "cart", "menu", "more",
        "अभी खरीदें", "कार्ट में डालें",
    )

    private val AMOUNT_REGEX = Regex(
        "(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*\\.?[0-9]{0,2})",
        RegexOption.IGNORE_CASE,
    )

    fun evaluate(snapshot: ScreenSnapshot): CheckoutEvaluation {
        if (snapshot.packageName !in MonitoredPackages.SHOPPING) {
            return CheckoutEvaluation(ScreenKind.NOT_CHECKOUT, emptyList(), findTotalPrice(snapshot), null)
        }
        return evaluateContent(snapshot)
    }

    /**
     * Evaluates checkout phrases and totals regardless of package name.
     * Used by the guardian to detect web/browser checkouts opened from ad links.
     */
    fun evaluateWeb(snapshot: ScreenSnapshot): CheckoutEvaluation = evaluateContent(snapshot)

    private fun evaluateContent(snapshot: ScreenSnapshot): CheckoutEvaluation {
        val matched = STRONG_CHECKOUT_PHRASES.filter { snapshot.containsPhrase(it) }
        val price = findTotalPrice(snapshot)
        val product = extractProductText(snapshot)

        val isConfirmation = CONFIRMATION_PHRASES.any { snapshot.containsPhrase(it) }
        if (isConfirmation) {
            return CheckoutEvaluation(ScreenKind.ORDER_CONFIRMATION, matched, price, product)
        }
        val isCheckout = matched.isNotEmpty() && (price != null || matched.size >= 2)
        return CheckoutEvaluation(
            if (isCheckout) ScreenKind.CHECKOUT else ScreenKind.NOT_CHECKOUT,
            matched, price, product,
        )
    }

    /**
     * The amount the family would actually pay: prefer an amount on a line that
     * mentions a total/payable/order wording; otherwise the largest amount on
     * screen (totals are larger than per-item prices in almost every real layout).
     * Returns the matched text as displayed, or null when no currency amount exists.
     */
    fun findTotalPrice(snapshot: ScreenSnapshot): String? {
        val totalLineHints = listOf("total", "payable", "amount", "कुल")
        var bestTotalLine: Pair<String, Double>? = null
        var bestAny: Pair<String, Double>? = null
        for (text in snapshot.texts) {
            for (match in AMOUNT_REGEX.findAll(text)) {
                val value = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
                val hit = match.value.trim() to value
                if (bestAny == null || value > bestAny!!.second) bestAny = hit
                val lower = text.lowercase()
                if (totalLineHints.any { lower.contains(it) }) {
                    if (bestTotalLine == null || value > bestTotalLine!!.second) bestTotalLine = hit
                }
            }
        }
        return (bestTotalLine ?: bestAny)?.first
    }

    /**
     * Best-effort product title: the longest visible text that is not a button
     * label, price, or checkout phrase. Product titles on Amazon/Flipkart are
     * sentences; buttons are not. Returns null rather than guessing.
     */
    fun extractProductText(snapshot: ScreenSnapshot): String? =
        snapshot.texts
            .map { it.trim() }
            .filter { it.length in 20..200 }
            .filter { text -> NON_PRODUCT_PHRASES.none { TextNormaliser.normalise(text).contains(TextNormaliser.normalise(it)) } }
            .filter { text -> !AMOUNT_REGEX.containsMatchIn(text) }
            .filter { text -> !text.contains("http", ignoreCase = true) }
            .maxByOrNull { it.length }
            ?.take(120)
}
