package com.mamoro.investments.data

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Decimal source values are kept intact; rounding to kopecks happens after multiplication. */
data class BondReference(
    val uid: String, val figi: String, val isin: String, val ticker: String, val classCode: String,
    val name: String, val currency: String, val nominal: String?, val accruedInterest: String?,
    val issueDay: Long?, val placementDay: Long?, val maturityDay: Long?,
    val couponsPerYear: Int, val floating: Boolean, val amortizing: Boolean, val perpetual: Boolean,
    val loadedAt: Long
)
data class BondSearchResult(val uid: String, val figi: String, val isin: String, val ticker: String,
    val name: String, val classCode: String)
data class BondCoupon(
    val eventKey: String, val number: Long, val payDay: Long, val fixDay: Long?,
    val startDay: Long?, val endDay: Long?, val amountPerBond: String?,
    val currency: String, val type: String
)
data class BondDownload(val reference: BondReference, val coupons: List<BondCoupon>,
    val fromDay: Long, val throughDay: Long)

internal object TInvestJson {
    fun day(value: String?): Long? = value?.takeIf { it.isNotBlank() }?.let {
        runCatching { Instant.parse(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }.getOrNull()
    }
    fun money(value: JSONObject?): String? {
        if (value == null || value.isNull("units") && value.isNull("nano")) return null
        val units = value.optString("units", "0").toLongOrNull() ?: return null
        val nanos = value.optString("nano", "0").toIntOrNull() ?: return null
        if (nanos <= -1_000_000_000 || nanos >= 1_000_000_000) return null
        if (units > 0 && nanos < 0 || units < 0 && nanos > 0) return null
        return BigDecimal.valueOf(units).add(BigDecimal.valueOf(nanos.toLong(),9)).toPlainString()
    }
    fun search(json: JSONObject): List<BondSearchResult> {
        val rows = json.optJSONArray("instruments") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val r = rows.optJSONObject(index) ?: return@mapNotNull null
            if (r.optString("instrumentKind") !in listOf("INSTRUMENT_TYPE_BOND","bond","1")
                && r.optString("instrumentType") != "bond") return@mapNotNull null
            val uid = r.optString("uid")
            val name = r.optString("name")
            if (uid.isBlank() || name.isBlank()) null else BondSearchResult(uid,r.optString("figi"),
                r.optString("isin"),r.optString("ticker"),name,r.optString("classCode"))
        }.distinctBy { it.uid }.take(50)
    }
    fun reference(json: JSONObject, now: Long): BondReference {
        val r = json.optJSONObject("instrument") ?: error("API не вернул карточку облигации")
        val uid = r.optString("uid")
        require(uid.isNotBlank() && r.optString("name").isNotBlank()) { "Неполная карточка облигации" }
        return BondReference(uid,r.optString("figi"),r.optString("isin"),r.optString("ticker"),
            r.optString("classCode"),r.optString("name"),r.optString("currency").uppercase(),
            money(r.optJSONObject("nominal")),money(r.optJSONObject("aciValue")),
            day(r.optString("stateRegDate")),day(r.optString("placementDate")),day(r.optString("maturityDate")),
            r.optInt("couponQuantityPerYear"),r.optBoolean("floatingCouponFlag"),
            r.optBoolean("amortizationFlag"),r.optBoolean("perpetualFlag"),now)
    }
    fun coupons(json: JSONObject, uid: String): List<BondCoupon> {
        val rows = json.optJSONArray("events") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val r = rows.optJSONObject(index) ?: return@mapNotNull null
            val pay = day(r.optString("couponDate")) ?: return@mapNotNull null
            val number = r.optString("couponNumber","0").toLongOrNull() ?: 0L
            val type = r.optString("couponType")
            val value = r.optJSONObject("payOneBond")
            val currency = value?.optString("currency").orEmpty().uppercase()
            val amount = money(value)?.takeIf { BigDecimal(it).signum() >= 0 }
                ?.takeUnless { currency.isBlank() || BigDecimal(it).signum() == 0 &&
                    type in listOf("COUPON_TYPE_FLOATING","COUPON_TYPE_VARIABLE","COUPON_TYPE_UNSPECIFIED","") }
            BondCoupon(uid + "/" + if (number > 0) number.toString() else "date-" + pay,number,pay,
                day(r.optString("fixDate")),day(r.optString("couponStartDate")),day(r.optString("couponEndDate")),
                amount,currency,type)
        }.distinctBy { it.eventKey }.sortedBy { it.payDay }
    }
    fun totalMinor(amount: String, quantityMicros: Long): Long {
        require(quantityMicros >= 0L)
        return BigDecimal(amount).multiply(BigDecimal.valueOf(quantityMicros,6))
            .movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact()
    }
    fun utc(day: Long): String = LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toString()
}
