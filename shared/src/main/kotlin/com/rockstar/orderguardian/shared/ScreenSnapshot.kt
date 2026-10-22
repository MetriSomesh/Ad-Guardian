package com.rockstar.orderguardian.shared

/**
 * A flat, redacted view of what an AccessibilityService saw on one screen.
 *
 * [texts] holds the visible text / content-description strings only. The
 * guardian app builds this from the accessibility node tree (capped) and
 * must never put payment details, addresses or OTPs in here.
 */
data class ScreenSnapshot(
    val packageName: String,
    val texts: List<String>,
    /** True when any node on the screen reported itself scrollable. */
    val hasScrollableContainer: Boolean = false,
) {
    /** All texts normalised for matching: lower-cased, whitespace collapsed. */
    val normalisedTexts: List<String> by lazy { texts.map { TextNormaliser.normalise(it) } }

    /**
     * Word-boundary phrase match: the phrase's words must appear as consecutive
     * whole words in a text. Substring matching would make the label "ad" fire
     * inside "read" and "shop" fire inside "workshop".
     */
    fun containsPhrase(phrase: String): Boolean {
        val needleWords = TextNormaliser.normalise(phrase).split(' ').filter { it.isNotEmpty() }
        if (needleWords.isEmpty()) return false
        return normalisedTexts.any { text ->
            val words = text.split(' ').filter { it.isNotEmpty() }
            if (needleWords.size == 1) {
                words.contains(needleWords[0])
            } else {
                words.windowed(needleWords.size).any { it == needleWords }
            }
        }
    }
}

object TextNormaliser {
    private val whitespace = Regex("\\s+")
    private val punctuation = Regex("[\\p{Punct}।]")

    /** Lower-cases, drops punctuation and collapses whitespace. Hindi (Devanagari) text is left intact. */
    fun normalise(raw: String): String =
        raw.lowercase()
            .replace(punctuation, " ")
            .replace(whitespace, " ")
            .trim()
}

/** Package names the guardian watches. Kept in one place so apps and tests agree. */
object MonitoredPackages {
    const val AMAZON = "com.amazon.mShop.android.shopping"
    const val AMAZON_IN = "in.amazon.mShop.android.shopping"
    const val FLIPKART = "com.flipkart.android"
    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"
    const val FACEBOOK = "com.facebook.katana"
    const val FACEBOOK_LITE = "com.facebook.lite"
    const val WHATSAPP = "com.whatsapp"

    // Browsers (Chrome, Custom Tabs, Samsung, Firefox, Edge, Opera, Brave, DuckDuckGo, Vivo, Oppo, Xiaomi)
    const val CHROME = "com.android.chrome"
    const val CHROME_ALT = "com.google.android.apps.chrome"
    const val BROWSER = "com.android.browser"
    const val SAMSUNG_BROWSER = "com.sec.android.app.sbrowser"
    const val FIREFOX = "org.mozilla.firefox"
    const val EDGE = "com.microsoft.emmx"
    const val OPERA = "com.opera.browser"
    const val BRAVE = "com.brave.browser"
    const val DUCKDUCKGO = "com.duckduckgo.mobile.android"
    const val VIVO_BROWSER = "com.vivo.browser"
    const val HEYTAP_BROWSER = "com.heytap.browser"
    const val MI_BROWSER = "com.mi.globalbrowser"

    val BROWSERS = setOf(
        CHROME, CHROME_ALT, BROWSER, SAMSUNG_BROWSER, FIREFOX, EDGE,
        OPERA, BRAVE, DUCKDUCKGO, VIVO_BROWSER, HEYTAP_BROWSER, MI_BROWSER,
    )
    val SHOPPING = setOf(AMAZON, AMAZON_IN, FLIPKART)
    val SOCIAL = setOf(YOUTUBE, INSTAGRAM, FACEBOOK, FACEBOOK_LITE, WHATSAPP)
    val ALL = SHOPPING + SOCIAL + BROWSERS

    /** Short human label used in requests and the event log. Never invent one: unknown packages show their raw name. */
    fun labelFor(packageName: String): String = when (packageName) {
        AMAZON, AMAZON_IN -> "Amazon"
        FLIPKART -> "Flipkart"
        YOUTUBE -> "YouTube"
        INSTAGRAM -> "Instagram"
        FACEBOOK, FACEBOOK_LITE -> "Facebook"
        WHATSAPP -> "WhatsApp"
        in BROWSERS -> "Browser"
        else -> packageName
    }
}
