package com.rockstar.orderguardian

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rockstar.orderguardian.data.LocalEventLog
import com.rockstar.orderguardian.data.RepositoryProvider
import com.rockstar.orderguardian.shared.ApprovalRequest
import com.rockstar.orderguardian.shared.EjectStyle
import com.rockstar.orderguardian.shared.MonitoredPackages
import com.rockstar.orderguardian.shared.RequestStatus
import java.text.DateFormat
import java.util.Date

/**
 * Family-facing dashboard for the grandfather's phone.
 *
 * This screen is for whoever sets the phone up (and for testing). Day to
 * day, the grandfather never needs to open it — he only sees the eject
 * notice. Every row here is live state read from the phone or from the
 * approval repository; there is no placeholder content.
 */
class MainActivity : AppCompatActivity() {

    private val settings by lazy { GuardianSettings(this) }
    private val eventLog by lazy { LocalEventLog(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        GuardianForegroundService.start(this)

        findViewById<TextView>(R.id.modeText).text = if (RepositoryProvider.isLiveMode) {
            "Live mode: approvals go to the Approver app on the family's phone (Firebase)."
        } else {
            "On-device test mode: no Firebase project is configured, so approvals can " +
                "only be decided in this app, on this phone. See SETUP_FIREBASE.md in the project."
        }

        val group = findViewById<RadioGroup>(R.id.ejectStyleGroup)
        group.check(
            if (settings.ejectStyle == EjectStyle.BACK_THEN_CLOSE_APP) {
                R.id.radioCloseApp
            } else {
                R.id.radioBack
            },
        )
        group.setOnCheckedChangeListener { _, checkedId ->
            settings.ejectStyle = if (checkedId == R.id.radioCloseApp) {
                EjectStyle.BACK_THEN_CLOSE_APP
            } else {
                EjectStyle.BACK_ONLY
            }
        }

        findViewById<Button>(R.id.refreshButton).setOnClickListener { render() }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        renderStatus()
        renderChecklist()
        renderPending()
        renderEvents()
    }

    private fun renderStatus() {
        val accessibilityOn = isAccessibilityEnabled()
        findViewById<TextView>(R.id.statusText).text = if (accessibilityOn) {
            "Protection is ON.\nAmazon and Flipkart checkouts will be stopped and sent to the family for a decision."
        } else {
            "Protection is OFF.\nOrder checking is not running. Turn it on below (Phone setup, step 1)."
        }
    }

    private fun renderChecklist() {
        val container = findViewById<LinearLayout>(R.id.checklistContainer)
        container.removeAllViews()

        val powerManager = getSystemService(PowerManager::class.java)
        val batteryExempt = powerManager.isIgnoringBatteryOptimizations(packageName)

        container.addView(
            checklistRow(
                done = isAccessibilityEnabled(),
                label = "1. Order checking (Accessibility) is on",
                buttonText = "Open Accessibility settings",
                action = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            ),
        )
        if (!isAccessibilityEnabled()) {
            container.addView(
                checklistRow(
                    done = false,
                    label = "If Accessibility is blocked ('Restricted setting'): open App Info, tap ⋮ (top-right), then tap 'Allow restricted settings'",
                    buttonText = "Open App Info (to unblock)",
                    action = {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                    },
                ),
            )
        }
        container.addView(
            checklistRow(
                done = Settings.canDrawOverlays(this),
                label = "2. The 'Order paused' message can appear over other apps",
                buttonText = "Allow showing over other apps",
                action = {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                },
            ),
        )
        container.addView(
            checklistRow(
                done = batteryExempt,
                label = "3. Battery: this app is allowed to keep running (Unrestricted)",
                buttonText = "Open battery settings",
                action = {
                    try {
                        startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                    } catch (e: Exception) {
                        try {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (e2: Exception) {
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:$packageName"),
                                ),
                            )
                        }
                    }
                },
            ),
        )
    }

    private fun checklistRow(done: Boolean, label: String, buttonText: String, action: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }
        row.addView(TextView(this).apply {
            text = (if (done) "✓  " else "✗  ") + label
            textSize = 16f
            setTextColor(getColor(if (done) R.color.accent_green else R.color.deny_red))
        })
        if (!done) {
            row.addView(Button(this).apply {
                text = buttonText
                setOnClickListener { action() }
            })
        }
        return row
    }

    private fun renderPending() {
        val container = findViewById<LinearLayout>(R.id.pendingContainer)
        container.removeAllViews()
        val repo = RepositoryProvider.get(this)
        val live = repo.listRequests().filter {
            it.status == RequestStatus.PENDING || it.status == RequestStatus.APPROVED
        }
        findViewById<TextView>(R.id.pendingEmptyText).visibility =
            if (live.isEmpty()) View.VISIBLE else View.GONE
        for (request in live) {
            container.addView(pendingRow(request))
        }
    }

    private fun pendingRow(request: ApprovalRequest): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.paper_card))
            setPadding(28, 20, 28, 20)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            params.setMargins(0, 12, 0, 0)
            layoutParams = params
        }
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(request.createdAtMillis))
        row.addView(TextView(this).apply {
            text = "${request.appLabel} — ${request.priceText ?: "price not visible"} — $time"
            textSize = 16f
            setTextColor(getColor(R.color.ink))
        })
        row.addView(TextView(this).apply {
            text = request.productText ?: "Product title was not visible on that screen"
            textSize = 15f
            setTextColor(getColor(R.color.ink_soft))
        })

        if (request.status == RequestStatus.APPROVED) {
            row.addView(TextView(this).apply {
                text = "Approved by family. He can retry this order once."
                textSize = 15f
                setTextColor(getColor(R.color.accent_green))
            })
            return row
        }

        val remainingMillis = request.expiresAtMillis - System.currentTimeMillis()
        row.addView(TextView(this).apply {
            text = if (remainingMillis > 0) {
                "Waiting for a decision. This request expires in ${remainingMillis / 60000 + 1} min."
            } else {
                "This request has expired. A fresh attempt will ask again."
            }
            textSize = 15f
            setTextColor(getColor(R.color.warn_amber))
        })

        if (!RepositoryProvider.isLiveMode) {
            row.addView(TextView(this).apply {
                text = "On-device test mode — these buttons stand in for the family phone:"
                textSize = 14f
                setTextColor(getColor(R.color.ink_soft))
            })
            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            buttons.addView(Button(this).apply {
                text = "Approve"
                setOnClickListener {
                    RepositoryProvider.get(this@MainActivity)
                        .decide(request.id, approve = true, nowMillis = System.currentTimeMillis())
                    render()
                }
            })
            buttons.addView(Button(this).apply {
                text = "Deny"
                setOnClickListener {
                    RepositoryProvider.get(this@MainActivity)
                        .decide(request.id, approve = false, nowMillis = System.currentTimeMillis())
                    render()
                }
            })
            row.addView(buttons)
        }
        return row
    }

    private fun renderEvents() {
        val container = findViewById<LinearLayout>(R.id.eventsContainer)
        container.removeAllViews()
        val events = eventLog.list()
        findViewById<TextView>(R.id.eventsEmptyText).visibility =
            if (events.isEmpty()) View.VISIBLE else View.GONE
        val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        for (event in events) {
            container.addView(TextView(this).apply {
                text = "${format.format(Date(event.timestampMillis))} — " +
                    "${MonitoredPackages.labelFor(event.packageName)} — ${event.detail}"
                textSize = 15f
                setTextColor(getColor(R.color.ink))
                setPadding(0, 8, 0, 8)
            })
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabled.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
