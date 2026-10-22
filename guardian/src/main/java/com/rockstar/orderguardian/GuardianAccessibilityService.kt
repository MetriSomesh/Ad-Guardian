package com.rockstar.orderguardian

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.rockstar.orderguardian.data.LocalEventLog
import com.rockstar.orderguardian.data.RepositoryProvider
import com.rockstar.orderguardian.shared.AdAction
import com.rockstar.orderguardian.shared.AdRules
import com.rockstar.orderguardian.shared.BlockedEvent
import com.rockstar.orderguardian.shared.CheckoutFingerprint
import com.rockstar.orderguardian.shared.CheckoutGate
import com.rockstar.orderguardian.shared.CheckoutRules
import com.rockstar.orderguardian.shared.EjectAction
import com.rockstar.orderguardian.shared.EjectLogic
import com.rockstar.orderguardian.shared.MonitoredPackages
import com.rockstar.orderguardian.shared.ScreenKind
import com.rockstar.orderguardian.shared.ScreenSnapshot
import java.util.UUID

/**
 * The detector and the ejector.
 *
 * For Amazon/Flipkart it runs every visible screen through [CheckoutRules]
 * and [CheckoutGate]: a checkout screen means "throw him Back out of it and
 * ask the family" — never a full-screen freeze.
 *
 * For YouTube/Instagram/Facebook/WhatsApp it runs [AdRules]: skip past
 * shopping ads where the surface scrolls, eject Back if a tap lands on a
 * shopping page, otherwise just write it in the log. It cannot remove ads
 * from those apps' feeds and does not claim to.
 *
 * Scanning is throttled and capped: feeds fire hundreds of content-changed
 * events a minute, and walking unbounded trees would eat the battery of a
 * basic phone.
 */
class GuardianAccessibilityService : AccessibilityService() {

    private var lastScanAtMillis = 0L

    private val gate: CheckoutGate by lazy {
        CheckoutGate(
            repository = RepositoryProvider.get(applicationContext),
            nowMillis = { System.currentTimeMillis() },
            newId = { UUID.randomUUID().toString() },
        )
    }
    private val eventLog: LocalEventLog by lazy { LocalEventLog(applicationContext) }
    private val settings: GuardianSettings by lazy { GuardianSettings(applicationContext) }

    /** Consecutive scans (in a row) that were checkout screens; drives Back-then-Home. */
    private var consecutiveCheckoutScans = 0

    /** The approval currently being spent, so the confirmation screen can consume it. */
    private var allowedRequestId: String? = null

    /** Per-package cooldown for ad actions, so one ad cannot trigger a scroll storm. */
    private val lastAdActionAt = HashMap<String, Long>()

    /** Tracks the most recent social app (e.g. YouTube) to detect when an ad link opens an external browser. */
    private var lastSocialPackage: String? = null
    private var lastSocialTimeMillis = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        GuardianForegroundService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName !in MonitoredPackages.ALL) return

        val now = System.currentTimeMillis()
        if (now - lastScanAtMillis < SCAN_INTERVAL_MILLIS) return
        lastScanAtMillis = now

        val snapshot = buildSnapshot(packageName) ?: return
        if (packageName in MonitoredPackages.SHOPPING) {
            handleShoppingScreen(snapshot)
        } else if (packageName in MonitoredPackages.BROWSERS) {
            handleBrowserScreen(snapshot)
        } else {
            lastSocialPackage = packageName
            lastSocialTimeMillis = now
            handleSocialScreen(snapshot)
        }
    }

    override fun onInterrupt() {
        // Required override. Nothing to release: scanning is stateless per event.
    }

    // ---------------------------------------------------------------- shopping

    private fun handleShoppingScreen(snapshot: ScreenSnapshot) {
        val evaluation = if (snapshot.packageName in MonitoredPackages.BROWSERS) {
            CheckoutRules.evaluateWeb(snapshot)
        } else {
            CheckoutRules.evaluate(snapshot)
        }
        when (evaluation.kind) {
            ScreenKind.ORDER_CONFIRMATION -> {
                consecutiveCheckoutScans = 0
                allowedRequestId?.let { id ->
                    RepositoryProvider.get(applicationContext).consumeApproval(id)
                    allowedRequestId = null
                }
            }

            ScreenKind.CHECKOUT -> {
                val fingerprint = CheckoutFingerprint(
                    packageName = snapshot.packageName,
                    productText = evaluation.productText,
                    priceText = evaluation.priceText,
                )
                when (val decision = gate.onCheckoutDetected(fingerprint, evaluation.productText, evaluation.priceText)) {
                    is CheckoutGate.GateDecision.Allow -> {
                        consecutiveCheckoutScans = 0
                        allowedRequestId = decision.requestId
                    }

                    is CheckoutGate.GateDecision.EjectWithNewRequest -> {
                        eject()
                        eventLog.add(
                            BlockedEvent(
                                id = UUID.randomUUID().toString(),
                                timestampMillis = System.currentTimeMillis(),
                                packageName = snapshot.packageName,
                                kind = BlockedEvent.Kind.CHECKOUT_EJECTED,
                                detail = logDetail(evaluation.productText, evaluation.priceText),
                            ),
                        )
                        Notifications.requestCreatedNotification(
                            this, decision.request, RepositoryProvider.isLiveMode,
                        )
                    }

                    is CheckoutGate.GateDecision.EjectDuplicate -> {
                        // Same purchase, already waiting on the family: out again,
                        // but no new request and no second notification.
                        eject()
                    }
                }
            }

            ScreenKind.NOT_CHECKOUT -> {
                consecutiveCheckoutScans = 0
            }
        }
    }

    /**
     * Throws him out of the checkout: Back first. If the next scan (of the
     * screen Back landed on) is still a checkout, the counter has risen and
     * the close-app style escalates to Home.
     */
    private fun eject() {
        consecutiveCheckoutScans += 1
        val action = EjectLogic.nextAction(consecutiveCheckoutScans, settings.ejectStyle)
        when (action) {
            EjectAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            EjectAction.HOME -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
                consecutiveCheckoutScans = 0
            }
        }
    }

    // ---------------------------------------------------------------- social / ads

    private fun handleSocialScreen(snapshot: ScreenSnapshot) {
        val evaluation = AdRules.evaluate(snapshot)
        if (evaluation.action == AdAction.NONE) return

        val now = System.currentTimeMillis()
        val last = lastAdActionAt[snapshot.packageName] ?: 0L
        if (now - last < AD_COOLDOWN_MILLIS) return
        lastAdActionAt[snapshot.packageName] = now

        when (evaluation.action) {
            AdAction.SKIP_SCROLL -> {
                val scrolled = scrollForward()
                eventLog.add(
                    BlockedEvent(
                        id = UUID.randomUUID().toString(),
                        timestampMillis = now,
                        packageName = snapshot.packageName,
                        kind = if (scrolled) BlockedEvent.Kind.AD_SKIPPED else BlockedEvent.Kind.AD_NOTED,
                        detail = if (scrolled) {
                            "Shopping ad skipped (${evaluation.matchedShopCta ?: "shop button"})"
                        } else {
                            "Shopping ad seen but the screen would not scroll (${evaluation.matchedShopCta ?: "shop button"})"
                        },
                    ),
                )
            }

            AdAction.EJECT_BACK -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                eventLog.add(
                    BlockedEvent(
                        id = UUID.randomUUID().toString(),
                        timestampMillis = now,
                        packageName = snapshot.packageName,
                        kind = BlockedEvent.Kind.AD_EJECTED,
                        detail = "Shopping page opened from an ad — sent back",
                    ),
                )
            }

            AdAction.NOTE_ONLY -> {
                eventLog.add(
                    BlockedEvent(
                        id = UUID.randomUUID().toString(),
                        timestampMillis = now,
                        packageName = snapshot.packageName,
                        kind = BlockedEvent.Kind.AD_NOTED,
                        detail = "Ad with a shop button on a screen that cannot be skipped",
                    ),
                )
            }

            AdAction.NONE -> Unit
        }
    }

    // ---------------------------------------------------------------- browser

    private fun handleBrowserScreen(snapshot: ScreenSnapshot) {
        val now = System.currentTimeMillis()
        val cameFromSocial = (now - lastSocialTimeMillis) < SOCIAL_TO_BROWSER_WINDOW_MILLIS

        // Layer 1: Instant redirect bounce. If browser was launched directly from a social app
        // (e.g. ad click / Shop Now), immediately close it before the user can interact.
        if (cameFromSocial) {
            val fromApp = MonitoredPackages.labelFor(lastSocialPackage ?: "Social")
            lastSocialTimeMillis = 0L
            performGlobalAction(GLOBAL_ACTION_BACK)
            eventLog.add(
                BlockedEvent(
                    id = UUID.randomUUID().toString(),
                    timestampMillis = now,
                    packageName = snapshot.packageName,
                    kind = BlockedEvent.Kind.AD_EJECTED,
                    detail = "Ad redirect from $fromApp blocked — closed browser",
                ),
            )
            return
        }

        // Layer 2: Web Checkout Gate. Even if opened later or restored from Recent/Background apps,
        // any checkout or order confirmation page is caught and gated.
        val checkoutEval = CheckoutRules.evaluateWeb(snapshot)
        if (checkoutEval.kind == ScreenKind.CHECKOUT || checkoutEval.kind == ScreenKind.ORDER_CONFIRMATION) {
            handleShoppingScreen(snapshot)
            return
        }

        // Layer 3: Store/Product page detection in browser.
        // If an e-commerce product or cart page is active (shopping CTA + price or checkout keywords),
        // eject back so orders cannot be placed.
        val hasShoppingCta = AdRules.SHOP_CTAS.any { snapshot.containsPhrase(it) }
        val hasPrice = CheckoutRules.findTotalPrice(snapshot) != null
        val hasShoppingKeyword = listOf(
            "cart", "checkout", "free delivery", "cash on delivery", "pay on delivery",
            "add to cart", "buy now", "order now", "item total", "subtotal", "shipping",
            "delivery charges", "secure checkout",
        ).any { snapshot.containsPhrase(it) }

        if (hasShoppingCta && (hasPrice || hasShoppingKeyword)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            eventLog.add(
                BlockedEvent(
                    id = UUID.randomUUID().toString(),
                    timestampMillis = now,
                    packageName = snapshot.packageName,
                    kind = BlockedEvent.Kind.AD_EJECTED,
                    detail = "Shopping store page in browser closed",
                ),
            )
        }
    }

    // ---------------------------------------------------------------- tree reading

    /** Builds a capped snapshot from the active window. Returns null when the tree is unavailable. */
    private fun buildSnapshot(packageName: String): ScreenSnapshot? {
        val root = rootInActiveWindow ?: return null
        val texts = ArrayList<String>()
        var scrollable = false
        var visited = 0
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++
            if (node.isScrollable) scrollable = true
            val text = node.text?.toString()?.trim().orEmpty()
            val description = node.contentDescription?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) texts.add(text)
            if (description.isNotEmpty() && description != text) texts.add(description)
            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { stack.add(it) }
            }
        }
        return ScreenSnapshot(
            packageName = packageName,
            texts = texts,
            hasScrollableContainer = scrollable,
        )
    }

    private fun scrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++
            if (node.isScrollable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { stack.add(it) }
            }
        }
        return false
    }

    private fun logDetail(productText: String?, priceText: String?): String {
        // Redacted by construction: product is truncated to 60 chars and no
        // payment data is ever read into the log.
        val product = productText?.take(60) ?: "Product title not visible on that screen"
        return if (priceText != null) "$product — $priceText" else product
    }

    companion object {
        private const val SCAN_INTERVAL_MILLIS = 250L
        private const val AD_COOLDOWN_MILLIS = 8000L
        private const val SOCIAL_TO_BROWSER_WINDOW_MILLIS = 8_000L
        private const val MAX_NODES = 500
    }
}
