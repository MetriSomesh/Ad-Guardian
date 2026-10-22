package com.rockstar.orderguardian.shared

/** Minimal in-memory ApprovalRepository for tests: same semantics as the SharedPreferences implementation. */
class InMemoryApprovalRepository : ApprovalRepository {
    private val byId = LinkedHashMap<String, ApprovalRequest>()

    override fun createRequest(request: ApprovalRequest): ApprovalRequest {
        byId[request.id] = request
        return request
    }

    override fun getRequest(id: String): ApprovalRequest? = byId[id]

    override fun pendingOrApprovedFor(fingerprintHash: String): ApprovalRequest? =
        byId.values.lastOrNull {
            it.fingerprintHash == fingerprintHash &&
                (it.status == RequestStatus.PENDING || it.status == RequestStatus.APPROVED)
        }

    override fun listRequests(): List<ApprovalRequest> = byId.values.toList()

    override fun decide(id: String, approve: Boolean, nowMillis: Long): ApprovalRequest? {
        val current = byId[id] ?: return null
        val (updated, _) = ApprovalLogic.decide(current, approve, nowMillis)
        byId[id] = updated
        return updated
    }

    override fun consumeApproval(id: String): ApprovalRequest? {
        val current = byId[id] ?: return null
        val updated = ApprovalLogic.consume(current)
        byId[id] = updated
        return updated
    }
}
