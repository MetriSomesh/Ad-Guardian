package com.rockstar.orderguardian.shared

/**
 * The per-purchase decision maker on the grandfather's phone.
 *
 * It answers one question — "this checkout screen just appeared; what now?" —
 * using the approval requests held in [repository]:
 *
 *  - A live approval exists for this exact fingerprint and has not been
 *    spent  -> ALLOW (the retry goes through).
 *  - A request for this fingerprint is already PENDING
 *    -> EJECT as a duplicate. No second request is created, so the approver
 *       is never spammed twice for the same tap-spree.
 *  - Otherwise -> EJECT and create a fresh PENDING request.
 *
 * An approval becomes an allow-once window: it ends when the order is
 * confirmed on screen ([onOrderConfirmed]), when it expires, or when a
 * different fingerprint shows up (a changed price/product never inherits it).
 */
class CheckoutGate(
    private val repository: ApprovalRepository,
    private val nowMillis: () -> Long,
    private val newId: () -> String,
) {
    sealed interface GateDecision {
        /** Let this attempt through; [requestId] is the approval being spent gradually (consumed on confirmation). */
        data class Allow(val requestId: String) : GateDecision

        /** Throw him out of checkout; [request] was just created and needs sending to the approver. */
        data class EjectWithNewRequest(val request: ApprovalRequest) : GateDecision

        /** Throw him out; a request for this same purchase is already waiting for a decision. */
        data class EjectDuplicate(val request: ApprovalRequest) : GateDecision
    }

    fun onCheckoutDetected(
        fingerprint: CheckoutFingerprint,
        productText: String?,
        priceText: String?,
    ): GateDecision {
        val now = nowMillis()
        val existing = repository.pendingOrApprovedFor(fingerprint.hash) ?: return createAndEject(fingerprint, productText, priceText, now)
        // An entry whose window has passed counts for nothing, whatever its
        // stored status says: the attempt is treated as brand new.
        val effective = ApprovalLogic.expireIfNeeded(existing, now)
        return when (effective.status) {
            RequestStatus.APPROVED -> GateDecision.Allow(effective.id)
            RequestStatus.PENDING -> GateDecision.EjectDuplicate(effective)
            else -> createAndEject(fingerprint, productText, priceText, now)
        }
    }

    private fun createAndEject(
        fingerprint: CheckoutFingerprint,
        productText: String?,
        priceText: String?,
        now: Long,
    ): GateDecision {
        val request = ApprovalLogic.newRequest(newId(), fingerprint, productText, priceText, now)
        repository.createRequest(request)
        return GateDecision.EjectWithNewRequest(request)
    }

    /** The allowed order completed: spend the approval so it cannot be replayed for a second order. */
    fun onOrderConfirmed(fingerprintHash: String) {
        repository.pendingOrApprovedFor(fingerprintHash)
            ?.takeIf { it.status == RequestStatus.APPROVED }
            ?.let { repository.consumeApproval(it.id) }
    }
}

/** How hard to throw him out of a checkout screen. */
enum class EjectStyle { BACK_ONLY, BACK_THEN_CLOSE_APP }

enum class EjectAction { BACK, HOME }

/**
 * First offence on a screen: Back. If the very next scan is *still* a
 * checkout/payment screen (Back landed on another payment step) and the
 * family chose the stronger style, close the app with Home. In BACK_ONLY
 * style it is Back every time — some checkouts need two Backs to escape,
 * and that is still gentler than closing the app.
 */
object EjectLogic {
    fun nextAction(consecutiveCheckoutScans: Int, style: EjectStyle): EjectAction =
        if (consecutiveCheckoutScans >= 2 && style == EjectStyle.BACK_THEN_CLOSE_APP) {
            EjectAction.HOME
        } else {
            EjectAction.BACK
        }
}
