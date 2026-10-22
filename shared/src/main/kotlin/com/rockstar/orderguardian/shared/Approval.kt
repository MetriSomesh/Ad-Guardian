package com.rockstar.orderguardian.shared

enum class RequestStatus { PENDING, APPROVED, DENIED, EXPIRED, CONSUMED }

/**
 * One approval request, as stored locally (SharedPreferences JSON) or in
 * Firestore. All timestamps are epoch millis; the backend copy uses server
 * timestamps in addition (see SETUP_FIREBASE.md), the state rules here only
 * ever compare like with like on one device.
 */
data class ApprovalRequest(
    val id: String,
    val fingerprintHash: String,
    val packageName: String,
    val appLabel: String,
    val productText: String?,
    val priceText: String?,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val status: RequestStatus = RequestStatus.PENDING,
    val decidedAtMillis: Long? = null,
) {
    fun isExpiredAt(nowMillis: Long): Boolean = nowMillis > expiresAtMillis
}

/** Pure state transitions for approval requests. Every transition is testable and total. */
object ApprovalLogic {

    const val DEFAULT_EXPIRY_MILLIS: Long = 12 * 60 * 1000L // 12 minutes

    fun newRequest(
        id: String,
        fingerprint: CheckoutFingerprint,
        productText: String?,
        priceText: String?,
        nowMillis: Long,
        expiryMillis: Long = DEFAULT_EXPIRY_MILLIS,
    ): ApprovalRequest = ApprovalRequest(
        id = id,
        fingerprintHash = fingerprint.hash,
        packageName = fingerprint.packageName,
        appLabel = MonitoredPackages.labelFor(fingerprint.packageName),
        productText = productText?.take(120),
        priceText = priceText,
        createdAtMillis = nowMillis,
        expiresAtMillis = nowMillis + expiryMillis,
    )

    enum class DecideOutcome { APPLIED, NOT_PENDING, EXPIRED }

    /**
     * Applies an approve/deny decision. A decision that arrives after expiry
     * does not resurrect the request: it becomes EXPIRED and the purchase
     * does not proceed.
     */
    fun decide(request: ApprovalRequest, approve: Boolean, nowMillis: Long): Pair<ApprovalRequest, DecideOutcome> {
        if (request.status != RequestStatus.PENDING) return request to DecideOutcome.NOT_PENDING
        if (request.isExpiredAt(nowMillis)) {
            return request.copy(status = RequestStatus.EXPIRED) to DecideOutcome.EXPIRED
        }
        return request.copy(
            status = if (approve) RequestStatus.APPROVED else RequestStatus.DENIED,
            decidedAtMillis = nowMillis,
        ) to DecideOutcome.APPLIED
    }

    /** Marks a PENDING or APPROVED request EXPIRED once its window has passed. */
    fun expireIfNeeded(request: ApprovalRequest, nowMillis: Long): ApprovalRequest =
        if ((request.status == RequestStatus.PENDING || request.status == RequestStatus.APPROVED) &&
            request.isExpiredAt(nowMillis)
        ) {
            request.copy(status = RequestStatus.EXPIRED)
        } else {
            request
        }

    /** An approval is spent the moment the order it allowed is confirmed on screen. */
    fun consume(request: ApprovalRequest): ApprovalRequest =
        if (request.status == RequestStatus.APPROVED) request.copy(status = RequestStatus.CONSUMED) else request
}

/**
 * Persistence boundary shared by both apps.
 *
 * Two implementations ship in this repo:
 *  - LocalApprovalRepository (guardian app): SharedPreferences on the same
 *    phone. On-device test mode only; it cannot reach another phone.
 *  - FirestoreApprovalRepository (both apps): used only when a real
 *    google-services.json is configured (see SETUP_FIREBASE.md).
 */
interface ApprovalRepository {
    fun createRequest(request: ApprovalRequest): ApprovalRequest
    fun getRequest(id: String): ApprovalRequest?
    fun pendingOrApprovedFor(fingerprintHash: String): ApprovalRequest?
    fun listRequests(): List<ApprovalRequest>
    fun decide(id: String, approve: Boolean, nowMillis: Long): ApprovalRequest?
    fun consumeApproval(id: String): ApprovalRequest?
}

/** One entry in the guardian's on-device blocked-events log. Product text is truncated and redacted by the caller. */
data class BlockedEvent(
    val id: String,
    val timestampMillis: Long,
    val packageName: String,
    val kind: Kind,
    val detail: String,
) {
    enum class Kind { CHECKOUT_EJECTED, AD_SKIPPED, AD_EJECTED, AD_NOTED }
}
