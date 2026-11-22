package com.mamoro.investments.domain

import com.mamoro.investments.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TInvestApiTest {
    @Test fun readsReferenceDatesAndDecimalWithoutFloatingPointLoss() {
        val value = TInvestJson.reference(JSONObject("""{"instrument":{"uid":"bond-id","name":"ОФЗ","currency":"rub","stateRegDate":"2020-01-15T00:00:00Z","placementDate":"2020-02-01T00:00:00Z","maturityDate":"2030-01-15T00:00:00Z","nominal":{"units":"1000","nano":0,"currency":"rub"},"aciValue":{"units":"12","nano":123456789,"currency":"rub"}}}"""),100L)
        assertEquals("RUB",value.currency)
        assertEquals(LocalDate.of(2020,1,15).toEpochDay(),value.issueDay)
        assertEquals("12.123456789",value.accruedInterest)
        assertNull(TInvestJson.day("not-a-date"))
        assertNull(TInvestJson.day(""))
    }
    @Test fun absentFloatingCouponRemainsUnknownAndRecordDateIsPreserved() {
        val result = TInvestJson.coupons(JSONObject("""{"events":[{"couponDate":"2030-05-02T00:00:00Z","couponNumber":"7","fixDate":"2030-04-29T00:00:00Z","couponType":"COUPON_TYPE_FLOATING"},{"couponDate":"2030-08-02T00:00:00Z","couponNumber":"8","couponType":"COUPON_TYPE_FLOATING","payOneBond":{"units":"0","nano":0,"currency":"rub"}}]}"""),"bond")
        assertEquals(2,result.size)
        assertNull(result[0].amountPerBond)
        assertNull(result[1].amountPerBond)
        assertEquals(LocalDate.of(2030,4,29).toEpochDay(),result[0].fixDay)
    }
    @Test fun roundsOnlyAfterQuantityMultiplication() {
        assertEquals(1L,TInvestJson.totalMinor("0.004",3_000_000))
        assertEquals(123456789L,TInvestJson.totalMinor("12.3456789",100_000_000_000L))
    }
    @Test fun searchUsesBondFilterAndReturnsOnlyBonds() = runBlocking {
        val api = TInvestApi({"dummy-test-read-token"},TInvestTransport { method,_,request ->
            assertEquals(TInvestMethod.SEARCH,method)
            assertEquals("ОФЗ",request.getString("query"))
            assertEquals("INSTRUMENT_TYPE_BOND",request.getString("instrumentKind"))
            JSONObject("""{"instruments":[{"uid":"a","name":"ОФЗ","instrumentKind":"INSTRUMENT_TYPE_BOND"},{"uid":"b","name":"Акция","instrumentKind":"INSTRUMENT_TYPE_SHARE"}]}""")
        },0)
        assertEquals(listOf("a"),api.search(" ОФЗ ").map { it.uid })
    }
    @Test fun missingTokenNeverCallsTransport() = runBlocking {
        var called = false
        val api = TInvestApi({null},TInvestTransport { _,_,_ -> called=true; JSONObject() },0)
        assertTrue(runCatching { api.search("ОФЗ") }.isFailure)
        assertFalse(called)
    }
    @Test fun yearlyDownloadsDeduplicateBoundaryCouponAndKeepStableIdentity() = runBlocking {
        var calls = 0
        val ref = TInvestJson.reference(JSONObject("""{"instrument":{"uid":"bond-id","name":"ОФЗ","currency":"rub","maturityDate":"2031-01-01T00:00:00Z"}}"""),0)
        val api = TInvestApi({"dummy-test-read-token"},TInvestTransport { method,_,request ->
            assertEquals(TInvestMethod.COUPONS,method)
            assertEquals("bond-id",request.getString("instrumentId"))
            assertTrue(request.getString("to").endsWith("Z"))
            calls++
            JSONObject("""{"events":[{"couponDate":"2030-06-01T00:00:00Z","couponNumber":"2","couponType":"COUPON_TYPE_CONSTANT","payOneBond":{"units":"50","currency":"rub"}}]}""")
        },0)
        val result = api.download(ref,LocalDate.of(2029,1,1).toEpochDay())
        assertEquals(3,calls)
        assertEquals(1,result.coupons.size)
        assertEquals("bond-id/2",result.coupons.single().eventKey)
    }
}
