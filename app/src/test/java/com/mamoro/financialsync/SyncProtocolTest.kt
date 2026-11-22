package com.mamoro.financialsync

import com.mamoro.financialsync.SyncProtocol.Account
import com.mamoro.financialsync.SyncProtocol.Command
import com.mamoro.financialsync.SyncProtocol.Decision
import com.mamoro.financialsync.SyncProtocol.Event
import com.mamoro.financialsync.SyncProtocol.Operation
import com.mamoro.financialsync.SyncProtocol.Record
import com.mamoro.financialsync.SyncProtocol.Valuation
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncProtocolTest {
    private val account = "10101010-1010-4010-8010-101010101010"
    private val security = "20202020-2020-4020-8020-202020202020"
    private val operationId = "30303030-3030-4030-8030-303030303030"
    private val timestamp = 1795348800000L
    private fun buy() = Operation(account, security, "ОФЗ 26242", "SU26242", "BOND", "BUY",
        10_000_000L, 100_000L, 0L, 0L, 0L, "RUB", "RUB", 1_000_000L, 1_000_000L,
        timestamp, "2026-11-22", "2026-11-23", "", "")
    private fun record(op: Operation = buy(), revision: Long = 1, deleted: Boolean = false) =
        Record(operationId, SyncProtocol.INVEST, "OPERATION", revision, timestamp, deleted, op.json())
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Invalid financial data was accepted") } catch (expected: RuntimeException) {
            // require()/JSON conversion must fail before a receiver touches financial tables.
        }
    }

    @Test fun protocolNegotiationRequiresVersionTwo() {
        assertEquals(2, SyncProtocol.negotiate(1, 3))
        rejected { SyncProtocol.negotiate(1, 1) }
        rejected { SyncProtocol.negotiate(3, 4) }
    }
    @Test fun exactUuidAndKnownOwnerAreRequired() {
        rejected { SyncProtocol.uuid("1-1-1-1-1") }
        rejected { SyncProtocol.uuid("abcdefab-cdef-4abc-8def-abcdefabcdef".uppercase()) }
        rejected { Record(operationId, SyncProtocol.WALLET, "OPERATION", 1, timestamp, false, buy().json()) }
    }
    @Test fun purchaseMovesCashToSecuritiesWithoutLosingWealth() {
        val initialCash = 5_000_000L
        val op = buy()
        assertEquals(-1_000_000L, op.cashDelta())
        assertEquals(initialCash, initialCash + op.cashDelta() + op.grossSecurityMinor())
    }
    @Test fun saleCommissionAndTaxAreDistinctFromPrincipal() {
        val op = buy().copy(type = "SELL", feeMinor = 1_500, taxMinor = 500)
        assertEquals(1_000_000L, op.nativePrincipal())
        assertEquals(998_000L, op.cashDelta())
    }
    @Test fun confirmedCouponHasNoPositionQuantityAndPaysOnlyOncePerRecord() {
        val coupon = buy().copy(type = "COUPON", quantityMicros = 0, unitPriceMinor = 0, incomeMinor = 56_240,
            couponKey = "coupon-10", feeMinor = 100, taxMinor = 7_311)
        val original = record(coupon)
        assertEquals(48_829L, coupon.cashDelta())
        assertEquals(Decision.DUPLICATE, SyncProtocol.decide(original, Record.parse(original.wire())))
    }
    @Test fun existingCashCanBeMigratedWithoutAnotherCreditOrDebit() {
        assertEquals(0L, buy().copy(cashAlreadyBooked = true).cashDelta())
        assertEquals(0L, buy().copy(type = "SELL", feeMinor = 100, cashAlreadyBooked = true).cashDelta())
    }
    @Test fun foreignCurrencyConversionLocksHistoricalRates() {
        val usd = buy().copy(quantityMicros = 1_000_000L, unitPriceMinor = 10_000,
            securityCurrency = "USD", basePerSecurityMicros = 100_000_000L, feeMinor = 10_000)
        assertEquals(1_000_000L, usd.nativePrincipal())
        assertEquals(-1_010_000L, usd.cashDelta())
        // A later quote is a different value; it must never mutate the saved operation.
        assertEquals(1_200_000L, SyncProtocol.convert(10_000, 120_000_000L, 1_000_000L))
        assertEquals(1_000_000L, Operation.parse(usd.json()).nativePrincipal())
    }
    @Test fun nativeAccountConversionUsesBothRatesAndHalfUp() {
        assertEquals(9_091L, SyncProtocol.convert(10_000, 100_000_000, 110_000_000))
        assertEquals(1L, SyncProtocol.convert(1, 1_000_000, 2_000_000))
        assertEquals(-1L, SyncProtocol.convert(-1, 1_000_000, 2_000_000))
    }
    @Test fun fractionalQuantityRoundsOnlyAtMinorUnit() {
        assertEquals(10_000L, SyncProtocol.amount(30_000, 333_333))
        assertEquals(1L, SyncProtocol.amount(1, 500_000))
        rejected { buy().copy(feeMinor = -1) }
    }
    @Test fun repeatedAndReorderedDeliveryCannotOverwriteANewerVersion() {
        val old = record()
        val updated = record(buy().copy(unitPriceMinor = 101_000), 2)
        assertEquals(Decision.APPLY, SyncProtocol.decide(old, updated))
        assertEquals(Decision.DUPLICATE, SyncProtocol.decide(updated, updated))
        assertEquals(Decision.STALE, SyncProtocol.decide(updated, old))
    }
    @Test fun tombstoneSurvivesAnOldBackupEvent() {
        val active = record()
        val deleted = record(revision = 2, deleted = true)
        assertEquals(Decision.APPLY, SyncProtocol.decide(active, deleted))
        assertEquals(Decision.STALE, SyncProtocol.decide(deleted, active))
        assertEquals(Decision.DUPLICATE, SyncProtocol.decide(deleted, Record.parse(deleted.wire())))
    }
    @Test fun sameVersionWithDifferentContentIsAConflict() {
        assertEquals(Decision.CONFLICT, SyncProtocol.decide(record(), record(buy().copy(feeMinor = 50))))
        rejected { SyncProtocol.requireExpectedRevision(2, 1) }
    }
    @Test fun ownerCommandContainsBaseVersionAndNeverBecomesACashRecord() {
        val command = Command(operationId, 3, "EDIT", buy().copy(quantityMicros = 5_000_000L))
        val r = Record(SyncProtocol.newId(), SyncProtocol.WALLET, "COMMAND", 1, timestamp, false, command.json())
        assertEquals(3L, Command.parse(Record.parse(r.wire()).data).expectedRevision)
        assertNotEquals(operationId, r.id)
    }
    @Test fun canonicalHashIgnoresJsonFieldOrderButPreservesContent() {
        val a = JSONObject().put("z", 2L).put("a", "Привет")
        val b = JSONObject().put("a", "Привет").put("z", 2L)
        assertEquals(SyncProtocol.canonical(a), SyncProtocol.canonical(b))
        assertEquals(record().digest(), Record.parse(record().wire()).digest())
    }
    @Test fun eventRoundTripPinsSchemaAndSourceOwner() {
        val event = Event(SyncProtocol.newId(), SyncProtocol.INVEST, record(), timestamp)
        assertEquals(event.digest(), Event.parse(event.wire()).digest())
        rejected { Event(SyncProtocol.newId(), SyncProtocol.WALLET, record(), timestamp) }
        rejected { Event.parse(event.wire().replace("\"schema\":2", "\"schema\":3")) }
    }
    @Test fun wireMoneyCannotBeCoercedFromFloatingPointOrAString() {
        val j = record().json()
        j.getJSONObject("data").put("unit_price_minor", 123.5)
        rejected { Record.parse(j) }
        j.getJSONObject("data").put("unit_price_minor", "123")
        rejected { Record.parse(j) }
    }
    @Test fun oversizedPayloadAndUnknownFieldsAreRejected() {
        rejected { Record.parse(" ".repeat(SyncProtocol.MAX_RECORD_BYTES + 1)) }
        val j = record().json().put("unexpected", true)
        rejected { Record.parse(j) }
        rejected { buy().copy(note = "я".repeat(501)) }
    }
    @Test fun invalidFxDatesAndMoneyFailBeforePersistence() {
        rejected { SyncProtocol.convert(1, 0, 1_000_000) }
        rejected { SyncProtocol.amount(SyncProtocol.MAX_MINOR, SyncProtocol.MAX_QUANTITY) }
        rejected { buy().copy(happenedDay = "2026-02-30") }
        rejected { buy().copy(accountCurrency = "XYZ") }
    }
    @Test fun accountRenamingDoesNotChangeTheStableIdentifier() {
        val a = Account("Счёт №1", "Т-Банк", "RUB", 5_000_000, 5_000_000, 1_000_000, true, true)
        val r = Record(account, SyncProtocol.WALLET, "ACCOUNT", 1, timestamp, false, a.json())
        val renamed = r.copy(revision = 2, data = a.copy(name = "ИИС").json())
        assertEquals(r.id, renamed.id)
        assertEquals(Decision.APPLY, SyncProtocol.decide(r, renamed))
    }
    @Test fun valuationAndActualIncomeRemainDifferentRecordKinds() {
        val valuation = Valuation(account, "USD", 10_000, timestamp)
        val id = SyncProtocol.stableId("valuation:$account:USD")
        val r = Record(id, SyncProtocol.INVEST, "VALUATION", 1, timestamp, false, valuation.json())
        assertEquals("VALUATION", Record.parse(r.wire()).kind)
        assertEquals(id, SyncProtocol.stableId("valuation:$account:USD"))
        rejected { Record(id, SyncProtocol.WALLET, "VALUATION", 1, timestamp, false, valuation.json()) }
    }
}
