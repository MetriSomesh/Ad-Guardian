package com.rockstar.orderguardian.shared

enum class AdAction {
    /** Not an ad surface we act on. */
    NONE,

    /** Ad with a shop call-to-action on a scrollable feed: scroll/swipe past it. */
    SKIP_SCROLL,

    /**
     * Ad with a shop call-to-action but nothing scrollable to skip with
     * (e.g. a status/full-screen surface): note it in the log. The guardian
     * cannot remove the ad from the other app's feed, and does not pretend to.
     */
    NOTE_ONLY,

    /** A tap has landed on a shopping page / checkout inside a social app: throw him Back out. */
    EJECT_BACK,
}

data class AdEvaluation(
    val action: AdAction,
    val matchedAdLabel: String?,
    val matchedShopCta: String?,
)

/**
 * Ad-surface rules for YouTube, Instagram, Facebook and WhatsApp.
 *
 * Honest scope: these apps render their own feeds. No third-party app can
 * delete an ad from them. What the guardian *can* do is recognise an ad
 * surface (an ad label together with a shopping call-to-action), skip past
 * it where scrolling is possible, and eject him if a tap still lands on a
 * shopping/checkout page. Every hit is logged so the family can see which
 * surfaces are actually being caught on the real phone.
 */
object AdRules {

    val AD_LABELS = listOf(
        "sponsored",
        "advertisement",
        "ad",
        "promoted",
        // Hindi: "विज्ञापन" = advertisement, "प्रायोजित" = sponsored.
        "विज्ञापन",
        "प्रायोजित",
    )

    val SHOP_CTAS = listOf(
        "shop now",
        "order now",
        "buy now",
        "shop",
        "view products",
        "view product",
        "visit store",
        "visit site",
        "buy",
        "अभी खरीदें",
        "अभी ऑर्डर करें",
    )

    fun evaluate(snapshot: ScreenSnapshot): AdEvaluation {
        if (snapshot.packageName !in MonitoredPackages.SOCIAL) {
            return AdEvaluation(AdAction.NONE, null, null)
        }
        val label = AD_LABELS.firstOrNull { snapshot.containsPhrase(it) }
        val cta = SHOP_CTAS.firstOrNull { snapshot.containsPhrase(it) }

        // Strongest signal first: he is no longer looking at an ad, he is on a
        // shopping/checkout page inside (or launched from) a social app.
        val looksLikeShoppingPage = cta != null &&
            (CheckoutRules.STRONG_CHECKOUT_PHRASES.any { snapshot.containsPhrase(it) } ||
                CheckoutRules.findTotalPrice(snapshot) != null && snapshot.containsPhrase("cart"))
        if (looksLikeShoppingPage) {
            return AdEvaluation(AdAction.EJECT_BACK, label, cta)
        }
        if (label == null || cta == null) {
            return AdEvaluation(AdAction.NONE, label, cta)
        }
        return if (snapshot.hasScrollableContainer) {
            AdEvaluation(AdAction.SKIP_SCROLL, label, cta)
        } else {
            AdEvaluation(AdAction.NOTE_ONLY, label, cta)
        }
    }
}
