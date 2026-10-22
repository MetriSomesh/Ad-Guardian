package com.rockstar.orderguardian.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.rockstar.orderguardian.shared.ApprovalLogic
import com.rockstar.orderguardian.shared.ApprovalRepository
import com.rockstar.orderguardian.shared.ApprovalRequest
import com.rockstar.orderguardian.shared.RequestStatus

/**
 * Firestore-backed approval store, used only when the app was built with a
 * real google-services.json (BuildConfig.FIREBASE_CONFIGURED).
 *
 * Writes are fire-and-forget with an optimistic in-memory cache; a snapshot
 * listener keeps the cache in step with decisions made in the approver app.
 * The guardian phone can create requests and consume its own approvals;
 * the decision itself is expected from the approver app (see
 * SETUP_FIREBASE.md for the security rules this relies on).
 */
class FirestoreApprovalRepository : ApprovalRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val collection = firestore.collection(COLLECTION)

    @Volatile
    private var cache: List<ApprovalRequest> = emptyList()

    private var registration: ListenerRegistration? = null

    init {
        // Anonymous auth so the shipped security rules (authenticated-only)
        // can apply. The listener is attached once sign-in completes.
        FirebaseAuth.getInstance().signInAnonymously()
            .addOnCompleteListener { attachListener() }
        attachListener()
    }

    private fun attachListener() {
        if (registration != null) return
        registration = collection.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                cache = snapshot.documents.mapNotNull { doc -> fromMap(doc.data ?: emptyMap()) }
                    .sortedByDescending { it.createdAtMillis }
            }
        }
    }

    override fun createRequest(request: ApprovalRequest): ApprovalRequest {
        cache = cache + request
        collection.document(request.id).set(toMap(request))
        return request
    }

    override fun getRequest(id: String): ApprovalRequest? = cache.firstOrNull { it.id == id }

    override fun pendingOrApprovedFor(fingerprintHash: String): ApprovalRequest? =
        cache.lastOrNull {
            it.fingerprintHash == fingerprintHash &&
                (it.status == RequestStatus.PENDING || it.status == RequestStatus.APPROVED)
        }

    override fun listRequests(): List<ApprovalRequest> = cache

    override fun decide(id: String, approve: Boolean, nowMillis: Long): ApprovalRequest? {
        val current = getRequest(id) ?: return null
        val (updated, _) = ApprovalLogic.decide(current, approve, nowMillis)
        cache = cache.map { if (it.id == id) updated else it }
        collection.document(id).update(
            mapOf(
                "status" to updated.status.name,
                "decidedAtMillis" to updated.decidedAtMillis,
            ),
        )
        return updated
    }

    override fun consumeApproval(id: String): ApprovalRequest? {
        val current = getRequest(id) ?: return null
        val updated = ApprovalLogic.consume(current)
        cache = cache.map { if (it.id == id) updated else it }
        collection.document(id).update("status", updated.status.name)
        return updated
    }

    private fun toMap(r: ApprovalRequest): Map<String, Any?> = mapOf(
        "id" to r.id,
        "fingerprintHash" to r.fingerprintHash,
        "packageName" to r.packageName,
        "appLabel" to r.appLabel,
        "productText" to r.productText,
        "priceText" to r.priceText,
        "createdAtMillis" to r.createdAtMillis,
        "expiresAtMillis" to r.expiresAtMillis,
        "status" to r.status.name,
        "decidedAtMillis" to r.decidedAtMillis,
    )

    private fun fromMap(m: Map<String, Any?>): ApprovalRequest? {
        val id = m["id"] as? String ?: return null
        return ApprovalRequest(
            id = id,
            fingerprintHash = m["fingerprintHash"] as? String ?: return null,
            packageName = m["packageName"] as? String ?: return null,
            appLabel = m["appLabel"] as? String ?: "",
            productText = m["productText"] as? String,
            priceText = m["priceText"] as? String,
            createdAtMillis = (m["createdAtMillis"] as? Number)?.toLong() ?: 0L,
            expiresAtMillis = (m["expiresAtMillis"] as? Number)?.toLong() ?: 0L,
            status = runCatching { RequestStatus.valueOf(m["status"] as String) }
                .getOrDefault(RequestStatus.PENDING),
            decidedAtMillis = (m["decidedAtMillis"] as? Number)?.toLong(),
        )
    }

    companion object {
        const val COLLECTION = "approvalRequests"
    }
}
