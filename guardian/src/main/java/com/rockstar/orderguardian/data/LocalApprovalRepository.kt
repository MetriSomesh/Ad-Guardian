package com.rockstar.orderguardian.data

import android.content.Context
import android.content.SharedPreferences
import com.rockstar.orderguardian.shared.ApprovalLogic
import com.rockstar.orderguardian.shared.ApprovalRepository
import com.rockstar.orderguardian.shared.ApprovalRequest
import com.rockstar.orderguardian.shared.RequestStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device approval store (SharedPreferences as a small JSON document).
 *
 * This is the "on-device test mode" backend: requests created by real
 * detections on this phone, decidable from this phone's dashboard. It does
 * not and cannot reach the father's phone — that is what the Firestore
 * implementation is for.
 */
class LocalApprovalRepository(context: Context) : ApprovalRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun createRequest(request: ApprovalRequest): ApprovalRequest {
        val all = loadAll().toMutableList()
        all.removeAll { it.id == request.id }
        all.add(request)
        saveAll(all)
        return request
    }

    @Synchronized
    override fun getRequest(id: String): ApprovalRequest? = loadAll().firstOrNull { it.id == id }

    @Synchronized
    override fun pendingOrApprovedFor(fingerprintHash: String): ApprovalRequest? =
        loadAll().lastOrNull {
            it.fingerprintHash == fingerprintHash &&
                (it.status == RequestStatus.PENDING || it.status == RequestStatus.APPROVED)
        }

    @Synchronized
    override fun listRequests(): List<ApprovalRequest> =
        loadAll().sortedByDescending { it.createdAtMillis }

    @Synchronized
    override fun decide(id: String, approve: Boolean, nowMillis: Long): ApprovalRequest? {
        val all = loadAll().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return null
        val (updated, _) = ApprovalLogic.decide(all[index], approve, nowMillis)
        all[index] = updated
        saveAll(all)
        return updated
    }

    @Synchronized
    override fun consumeApproval(id: String): ApprovalRequest? {
        val all = loadAll().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return null
        val updated = ApprovalLogic.consume(all[index])
        all[index] = updated
        saveAll(all)
        return updated
    }

    private fun loadAll(): List<ApprovalRequest> {
        val raw = prefs.getString(KEY_REQUESTS, "[]") ?: "[]"
        val array = JSONArray(raw)
        val out = ArrayList<ApprovalRequest>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            out.add(
                ApprovalRequest(
                    id = o.getString("id"),
                    fingerprintHash = o.getString("fingerprintHash"),
                    packageName = o.getString("packageName"),
                    appLabel = o.getString("appLabel"),
                    productText = o.optString("productText").ifEmpty { null },
                    priceText = o.optString("priceText").ifEmpty { null },
                    createdAtMillis = o.getLong("createdAtMillis"),
                    expiresAtMillis = o.getLong("expiresAtMillis"),
                    status = RequestStatus.valueOf(o.getString("status")),
                    decidedAtMillis = if (o.has("decidedAtMillis") && !o.isNull("decidedAtMillis")) {
                        o.getLong("decidedAtMillis")
                    } else {
                        null
                    },
                ),
            )
        }
        return out
    }

    private fun saveAll(requests: List<ApprovalRequest>) {
        // Keep the store bounded: the newest 100 requests are plenty for a family log.
        val trimmed = requests.sortedBy { it.createdAtMillis }.takeLast(100)
        val array = JSONArray()
        for (r in trimmed) {
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("fingerprintHash", r.fingerprintHash)
                    .put("packageName", r.packageName)
                    .put("appLabel", r.appLabel)
                    .put("productText", r.productText ?: "")
                    .put("priceText", r.priceText ?: "")
                    .put("createdAtMillis", r.createdAtMillis)
                    .put("expiresAtMillis", r.expiresAtMillis)
                    .put("status", r.status.name)
                    .put("decidedAtMillis", r.decidedAtMillis ?: JSONObject.NULL),
            )
        }
        prefs.edit().putString(KEY_REQUESTS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "approval_requests_local"
        private const val KEY_REQUESTS = "requests_json"
    }
}
