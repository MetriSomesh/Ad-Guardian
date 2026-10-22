package com.rockstar.orderguardian.approver

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.rockstar.orderguardian.shared.ApprovalLogic
import com.rockstar.orderguardian.shared.ApprovalRequest
import com.rockstar.orderguardian.shared.RequestStatus
import java.text.DateFormat
import java.util.Date

/**
 * The father's screen: a list of real approval requests from Firestore, with
 * big Approve / Deny buttons and a live expiry countdown.
 *
 * When the build has no Firebase configuration there are no requests and
 * there is no pretending otherwise: the not-connected card explains why and
 * the list stays at its real empty state.
 */
class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var requests: List<ApprovalRequest> = emptyList()

    private val countdownTick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, COUNTDOWN_REFRESH_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.notConnectedCard).visibility =
            if (BuildConfig.FIREBASE_CONFIGURED) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.refreshButton).setOnClickListener { render() }

        if (BuildConfig.FIREBASE_CONFIGURED) {
            FirebaseAuth.getInstance().signInAnonymously()
                .addOnCompleteListener { attachListener() }
            attachListener()
        }
        render()
    }

    override fun onStart() {
        super.onStart()
        handler.post(countdownTick)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(countdownTick)
    }

    private fun attachListener() {
        FirebaseFirestore.getInstance().collection(COLLECTION)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    requests = snapshot.documents.mapNotNull { doc -> fromMap(doc.data ?: emptyMap()) }
                        .sortedByDescending { it.createdAtMillis }
                    render()
                }
            }
    }

    private fun render() {
        val container = findViewById<LinearLayout>(R.id.requestsContainer)
        container.removeAllViews()
        findViewById<TextView>(R.id.emptyText).visibility =
            if (requests.isEmpty()) View.VISIBLE else View.GONE
        for (request in requests) {
            container.addView(requestCard(request))
        }
    }

    private fun requestCard(request: ApprovalRequest): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.paper_card))
            setPadding(28, 22, 28, 22)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            params.setMargins(0, 14, 0, 0)
            layoutParams = params
        }
        val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
        card.addView(TextView(this).apply {
            text = "${request.appLabel} — ${timeFormat.format(Date(request.createdAtMillis))}"
            textSize = 15f
            setTextColor(getColor(R.color.ink_soft))
        })
        card.addView(TextView(this).apply {
            text = request.productText ?: "Product title was not visible on his screen"
            textSize = 19f
            setTextColor(getColor(R.color.ink))
        })
        card.addView(TextView(this).apply {
            text = request.priceText ?: "Price was not visible on his screen"
            textSize = 19f
            setTextColor(getColor(R.color.ink))
        })

        when (request.status) {
            RequestStatus.PENDING -> {
                val remaining = request.expiresAtMillis - System.currentTimeMillis()
                card.addView(TextView(this).apply {
                    text = if (remaining > 0) {
                        val minutes = remaining / 60000
                        val seconds = (remaining % 60000) / 1000
                        "Decide within $minutes:${"%02d".format(seconds)} — after that this request expires and the order stays stopped."
                    } else {
                        "This request has expired. The order stays stopped; a new attempt will ask again."
                    }
                    textSize = 15f
                    setTextColor(getColor(R.color.warn_amber))
                })
                if (remaining > 0) {
                    val buttons = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.END
                        setPadding(0, 12, 0, 0)
                    }
                    buttons.addView(Button(this).apply {
                        text = "Approve"
                        textSize = 17f
                        setOnClickListener { decide(request, approve = true) }
                    })
                    buttons.addView(Button(this).apply {
                        text = "Deny"
                        textSize = 17f
                        setOnClickListener { decide(request, approve = false) }
                    })
                    card.addView(buttons)
                }
            }

            RequestStatus.APPROVED -> card.addView(statusLine("You approved this. He can retry the order once.", R.color.accent_green))
            RequestStatus.DENIED -> card.addView(statusLine("You denied this. The order stays stopped.", R.color.deny_red))
            RequestStatus.EXPIRED -> card.addView(statusLine("Expired with no decision. The order stayed stopped.", R.color.ink_soft))
            RequestStatus.CONSUMED -> card.addView(statusLine("Approved and used — the order went through.", R.color.ink_soft))
        }
        return card
    }

    private fun statusLine(text: String, colorRes: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(getColor(colorRes))
        setPadding(0, 8, 0, 0)
    }

    private fun decide(request: ApprovalRequest, approve: Boolean) {
        val (updated, outcome) = ApprovalLogic.decide(request, approve, System.currentTimeMillis())
        if (outcome == ApprovalLogic.DecideOutcome.NOT_PENDING) {
            render()
            return
        }
        FirebaseFirestore.getInstance().collection(COLLECTION).document(request.id)
            .update(
                mapOf(
                    "status" to updated.status.name,
                    "decidedAtMillis" to updated.decidedAtMillis,
                ),
            )
        // The snapshot listener will re-render with the server state; update
        // locally at once so the buttons respond immediately.
        requests = requests.map { if (it.id == request.id) updated else it }
        render()
    }

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
        private const val COLLECTION = "approvalRequests"
        private const val COUNTDOWN_REFRESH_MILLIS = 30_000L
    }
}
