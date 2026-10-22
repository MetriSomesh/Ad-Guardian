package com.rockstar.orderguardian.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckoutGateTest {

    private var now = 1_000_000L
    private var idCounter = 0
    private val repo = InMemoryApprovalRepository()
    private val gate = CheckoutGate(repo, nowMillis = { now }, newId = { "req-${++idCounter}" })

    private val bottle = CheckoutFingerprint(
        MonitoredPackages.AMAZON, "Stainless Steel Water Bottle 1 Litre", "₹1,299.00",
    )
    private val bottleName = "Stainless Steel Water Bottle 1 Litre"

    @Test
    fun `first checkout attempt ejects and creates one pending request`() {
        val decision = gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
        assertTrue(decision is CheckoutGate.GateDecision.EjectWithNewRequest)
        assertEquals(1, repo.listRequests().size)
        assertEquals(RequestStatus.PENDING, repo.listRequests()[0].status)
    }

    @Test
    fun `duplicate attempt while pending ejects without creating a second request`() {
        gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
        val second = gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
        assertTrue(second is CheckoutGate.GateDecision.EjectDuplicate)
        assertEquals(1, repo.listRequests().size)
    }

    @Test
    fun `approved request lets the retry through`() {
        val first = gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
            as CheckoutGate.GateDecision.EjectWithNewRequest
        repo.decide(first.request.id, approve = true, nowMillis = now)

        val retry = gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
        assertTrue(retry is CheckoutGate.GateDecision.Allow)
    }

    @Test
    fun `allow once is consumed when the order confirmation is seen`() {
        val first = gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
            as CheckoutGate.GateDecision.EjectWithNewRequest
        repo.decide(first.request.id, approve = true, nowMillis = now)
        gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00") // the allowed retry
        gate.onOrderConfirmed(bottle.hash)

        assertEquals(RequestStatus.CONSUMED, repo.getRequest(first.request.id)?.status)
        // A brand new attempt at the same item is a new purchase: new request, ejected again.
        val next = gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
        assertTrue(next is CheckoutGate.GateDecision.EjectWithNewRequest)
    }

    @Test
    fun `denied request keeps ejecting and a later attempt creates a new request`() {
        val first = gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
            as CheckoutGate.GateDecision.EjectWithNewRequest
        repo.decide(first.request.id, approve = false, nowMillis = now)

        val retry = gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
        assertTrue(retry is CheckoutGate.GateDecision.EjectWithNewRequest)
        assertEquals(2, repo.listRequests().size)
    }

    @Test
    fun `pending request expires after twelve minutes and the next attempt creates a fresh one`() {
        gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
        now += ApprovalLogic.DEFAULT_EXPIRY_MILLIS + 1

        val afterExpiry = gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
        assertTrue(afterExpiry is CheckoutGate.GateDecision.EjectWithNewRequest)
        assertEquals(2, repo.listRequests().size)
    }

    @Test
    fun `approval arriving after expiry does not allow the purchase`() {
        val first = gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
            as CheckoutGate.GateDecision.EjectWithNewRequest
        now += ApprovalLogic.DEFAULT_EXPIRY_MILLIS + 1

        val decided = repo.decide(first.request.id, approve = true, nowMillis = now)
        assertEquals(RequestStatus.EXPIRED, decided?.status)

        val attempt = gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
        assertTrue(attempt is CheckoutGate.GateDecision.EjectWithNewRequest)
    }

    @Test
    fun `approved allow window ends at expiry`() {
        val first = gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
            as CheckoutGate.GateDecision.EjectWithNewRequest
        repo.decide(first.request.id, approve = true, nowMillis = now)
        now += ApprovalLogic.DEFAULT_EXPIRY_MILLIS + 1

        val attempt = gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
        assertTrue(attempt !is CheckoutGate.GateDecision.Allow)
    }

    @Test
    fun `approval for one price does not allow the same product at a changed price`() {
        val first = gate.onCheckoutDetected(bottle, bottleName, "₹1,299.00")
            as CheckoutGate.GateDecision.EjectWithNewRequest
        repo.decide(first.request.id, approve = true, nowMillis = now)

        val dearer = CheckoutFingerprint(MonitoredPackages.AMAZON, bottleName, "₹1,499.00")
        val attempt = gate.onCheckoutDetected(dearer, bottleName, "₹1,499.00")
        assertTrue(attempt is CheckoutGate.GateDecision.EjectWithNewRequest)
    }

    @Test
    fun `double decision is rejected`() {
        val request = ApprovalLogic.newRequest("r1", bottle, bottleName, "₹1,299.00", now)
        val (approved, firstOutcome) = ApprovalLogic.decide(request, approve = true, nowMillis = now)
        assertEquals(ApprovalLogic.DecideOutcome.APPLIED, firstOutcome)
        val (_, secondOutcome) = ApprovalLogic.decide(approved, approve = false, nowMillis = now)
        assertEquals(ApprovalLogic.DecideOutcome.NOT_PENDING, secondOutcome)
    }
}
