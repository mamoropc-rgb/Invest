package com.mamoro.financialsync

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

/** Wire contract v2. Keep this file identical in WalletFree and Invest. No database or UI code. */
object SyncProtocol {
    const val VERSION = 2
    const val WALLET = "com.mamoro.karman"
    const val INVEST = "com.mamoro.investments"
    const val WALLET_AUTHORITY = "$WALLET.financialsync"
    const val INVEST_AUTHORITY = "$INVEST.financialsync"
    const val WALLET_CERT = "72316db9e71402d3d8818be2f7297597f08a643eb7dd7d1e1ebfdde0deebb2a8"
    const val INVEST_CERT = "dd671e36ea07cfb005840994262af869456994b7cf3c1b2c08d558b311098412"
    const val MAX_MINOR = 90_000_000_000_000L
    const val MAX_QUANTITY = 100_000_000_000_000L
    const val MAX_RECORD_BYTES = 24 * 1024
    const val MAX_BATCH = 20
    const val MAX_BATCH_BYTES = 512 * 1024
    val currencies = setOf("RUB", "USD", "EUR", "BYN")
    val operationTypes = setOf("BUY", "SELL", "COUPON", "DIVIDEND")
    val kinds = setOf("ACCOUNT", "FX", "OPERATION", "VALUATION", "COMMAND")

    fun peer(app: String): String = when (app) { WALLET -> INVEST; INVEST -> WALLET; else -> error("Unknown app") }
    fun authority(app: String): String = when (app) { WALLET -> WALLET_AUTHORITY; INVEST -> INVEST_AUTHORITY; else -> error("Unknown app") }
    fun owner(kind: String): String = when (kind) {
        "ACCOUNT", "FX", "COMMAND" -> WALLET
        "OPERATION", "VALUATION" -> INVEST
        else -> error("Unknown record kind")
    }
    fun negotiate(min: Int, max: Int): Int {
        require(min > 0 && min <= max && VERSION in min..max) { "Несовместимая версия протокола. Обновите оба приложения." }
        return VERSION
    }
    fun uuid(value: String): String {
        require(value.length == 36 && UUID.fromString(value).toString() == value) { "Некорректный UUID" }
        return value
    }
    fun newId(): String = UUID.randomUUID().toString()
    fun stableId(value: String): String = UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()
    fun minor(value: Long, positive: Boolean = false): Long {
        require(value in (if (positive) 1L else 0L)..MAX_MINOR) { "Некорректная сумма" }
        return value
    }
    fun signedMinor(value: Long): Long { require(value in -MAX_MINOR..MAX_MINOR); return value }
    fun rate(value: Long): Long { require(value in 1..1_000_000_000_000L); return value }
    fun currency(value: String): String { require(value in currencies) { "Неподдерживаемая валюта" }; return value }
    fun day(value: String): String {
        require(value.length == 10 && LocalDate.parse(value).toString() == value)
        return value
    }
    fun amount(priceMinor: Long, quantityMicros: Long): Long {
        minor(priceMinor)
        require(quantityMicros in 0..MAX_QUANTITY)
        return minor(BigDecimal.valueOf(priceMinor).multiply(BigDecimal.valueOf(quantityMicros, 6))
            .setScale(0, RoundingMode.HALF_UP).longValueExact())
    }
    /** One rounding at the destination currency's minor unit. Both rates belong to the operation. */
    fun convert(minor: Long, basePerSourceMicros: Long, basePerTargetMicros: Long): Long {
        signedMinor(minor); rate(basePerSourceMicros); rate(basePerTargetMicros)
        return signedMinor(BigDecimal.valueOf(minor).multiply(BigDecimal.valueOf(basePerSourceMicros))
            .divide(BigDecimal.valueOf(basePerTargetMicros), 0, RoundingMode.HALF_UP).longValueExact())
    }

    enum class Decision { APPLY, DUPLICATE, STALE, CONFLICT }
    fun decide(existing: Record?, incoming: Record): Decision {
        if (existing == null) return Decision.APPLY
        require(existing.id == incoming.id && existing.ownerApp == incoming.ownerApp && existing.kind == incoming.kind)
        return when {
            incoming.revision < existing.revision -> Decision.STALE
            incoming.revision > existing.revision -> Decision.APPLY
            incoming.digest() == existing.digest() -> Decision.DUPLICATE
            else -> Decision.CONFLICT
        }
    }
    fun requireExpectedRevision(actual: Long, expected: Long) {
        require(actual == expected) { "Конфликт версий: операция уже изменена в другом приложении" }
    }
    fun nextRevision(previous: Long): Long { require(previous >= 0); return Math.addExact(previous, 1L) }

    data class Record(val id: String, val ownerApp: String, val kind: String, val revision: Long,
                      val modifiedAt: Long, val deleted: Boolean, val data: JSONObject) {
        init {
            uuid(id); require(kind in kinds && ownerApp == owner(kind)); require(revision > 0 && modifiedAt > 0)
            when (kind) {
                "ACCOUNT" -> Account.parse(data)
                "OPERATION" -> Operation.parse(data)
                "VALUATION" -> Valuation.parse(data)
                "FX" -> Fx.parse(data)
                "COMMAND" -> { require(!deleted && revision == 1L); Command.parse(data) }
            }
            require(canonical(data).toByteArray(Charsets.UTF_8).size <= MAX_RECORD_BYTES - 1024)
        }
        fun json(): JSONObject = JSONObject().put("id", id).put("owner_app", ownerApp).put("kind", kind)
            .put("revision", revision).put("modified_at", modifiedAt).put("deleted", deleted).put("data", data)
        fun wire(): String = canonical(json())
        fun digest(): String = sha256(wire())
        companion object {
            fun parse(json: JSONObject): Record {
                json.keysOnly("id", "owner_app", "kind", "revision", "modified_at", "deleted", "data")
                return Record(json.string("id"), json.string("owner_app"), json.string("kind"),
                    json.integer("revision"), json.integer("modified_at"), json.boolean("deleted"), json.getJSONObject("data"))
            }
            fun parse(wire: String): Record { bounded(wire); return parse(JSONObject(wire)) }
        }
    }

    data class Event(val eventId: String, val sourceApp: String, val record: Record, val createdAt: Long) {
        init { uuid(eventId); require(sourceApp == record.ownerApp && createdAt > 0) }
        fun wire(): String = canonical(JSONObject().put("schema", VERSION).put("event_id", eventId)
            .put("source_app", sourceApp).put("created_at", createdAt).put("record", record.json())).also(::bounded)
        fun digest(): String = sha256(wire())
        companion object {
            fun parse(wire: String): Event {
                bounded(wire)
                val j = JSONObject(wire)
                j.keysOnly("schema", "event_id", "source_app", "created_at", "record")
                require(j.integer("schema") == VERSION.toLong()) { "Несовместимый протокол" }
                return Event(j.string("event_id"), j.string("source_app"), Record.parse(j.getJSONObject("record")), j.integer("created_at"))
            }
        }
    }

    data class Account(val name: String, val bank: String, val currency: String, val balanceMinor: Long,
                       val balanceBaseMinor: Long, val basePerNativeMicros: Long, val included: Boolean, val active: Boolean) {
        init {
            require(name.isNotBlank() && name.length <= 100 && bank.length <= 100)
            currency(currency); signedMinor(balanceMinor); signedMinor(balanceBaseMinor); rate(basePerNativeMicros)
            if (currency == "RUB") require(basePerNativeMicros == 1_000_000L)
        }
        fun json(): JSONObject = JSONObject().put("name", name).put("bank", bank).put("currency", currency)
            .put("balance_minor", balanceMinor).put("balance_base_minor", balanceBaseMinor)
            .put("base_per_native_micros", basePerNativeMicros).put("included", included).put("active", active)
        companion object { fun parse(j: JSONObject): Account {
            j.keysOnly("name", "bank", "currency", "balance_minor", "balance_base_minor", "base_per_native_micros", "included", "active")
            return Account(j.string("name"), j.string("bank"), j.string("currency"), j.integer("balance_minor"),
                j.integer("balance_base_minor"), j.integer("base_per_native_micros"), j.boolean("included"), j.boolean("active"))
        } }
    }

    /** Fees and taxes are in the cash account currency; security price and gross are in securityCurrency. */
    data class Operation(val accountId: String, val securityId: String, val securityName: String, val ticker: String,
                         val securityKind: String, val type: String, val quantityMicros: Long, val unitPriceMinor: Long,
                         val incomeMinor: Long, val feeMinor: Long, val taxMinor: Long,
                         val securityCurrency: String, val accountCurrency: String,
                         val basePerSecurityMicros: Long, val basePerAccountMicros: Long,
                         val happenedAt: Long, val happenedDay: String, val settlementDay: String?,
                         val couponKey: String, val note: String, val cashAlreadyBooked: Boolean = false) {
        init {
            uuid(accountId); uuid(securityId)
            require(securityName.isNotBlank() && securityName.length <= 160 && ticker.length <= 40)
            require(securityKind in setOf("STOCK", "BOND", "FUND") && type in operationTypes)
            require(happenedAt > 0); day(happenedDay); settlementDay?.let(::day)
            require(couponKey.length <= 200 && note.length <= 500)
            currency(securityCurrency); currency(accountCurrency); rate(basePerSecurityMicros); rate(basePerAccountMicros)
            if (securityCurrency == "RUB") require(basePerSecurityMicros == 1_000_000L)
            if (accountCurrency == "RUB") require(basePerAccountMicros == 1_000_000L)
            minor(feeMinor); minor(taxMinor)
            if (type == "BUY" || type == "SELL") {
                require(quantityMicros in 1..MAX_QUANTITY); minor(unitPriceMinor, true); require(incomeMinor == 0L)
                minor(amount(unitPriceMinor, quantityMicros), true)
            } else { require(quantityMicros == 0L && unitPriceMinor == 0L); minor(incomeMinor, true) }
            nativePrincipal(); signedMinor(cashDelta())
        }
        fun grossSecurityMinor(): Long = if (type == "BUY" || type == "SELL") amount(unitPriceMinor, quantityMicros) else incomeMinor
        fun nativePrincipal(): Long = minor(convert(grossSecurityMinor(), basePerSecurityMicros, basePerAccountMicros), true)
        fun cashDelta(): Long {
            if (cashAlreadyBooked) return 0
            val principal = if (type == "BUY") -nativePrincipal() else nativePrincipal()
            return Math.subtractExact(Math.subtractExact(principal, feeMinor), taxMinor)
        }
        fun json(): JSONObject = JSONObject().put("account_id", accountId).put("security_id", securityId)
            .put("security_name", securityName).put("ticker", ticker).put("security_kind", securityKind).put("type", type)
            .put("quantity_micros", quantityMicros).put("unit_price_minor", unitPriceMinor).put("income_minor", incomeMinor)
            .put("fee_minor", feeMinor).put("tax_minor", taxMinor).put("security_currency", securityCurrency)
            .put("account_currency", accountCurrency).put("base_per_security_micros", basePerSecurityMicros)
            .put("base_per_account_micros", basePerAccountMicros).put("happened_at", happenedAt).put("happened_day", happenedDay)
            .put("settlement_day", settlementDay ?: JSONObject.NULL).put("coupon_key", couponKey).put("note", note)
            .put("cash_already_booked", cashAlreadyBooked)
        companion object { fun parse(j: JSONObject): Operation {
            j.keysOnly("account_id", "security_id", "security_name", "ticker", "security_kind", "type", "quantity_micros",
                "unit_price_minor", "income_minor", "fee_minor", "tax_minor", "security_currency", "account_currency",
                "base_per_security_micros", "base_per_account_micros", "happened_at", "happened_day", "settlement_day",
                "coupon_key", "note", "cash_already_booked")
            return Operation(j.string("account_id"), j.string("security_id"), j.string("security_name"), j.string("ticker"),
                j.string("security_kind"), j.string("type"), j.integer("quantity_micros"), j.integer("unit_price_minor"),
                j.integer("income_minor"), j.integer("fee_minor"), j.integer("tax_minor"), j.string("security_currency"),
                j.string("account_currency"), j.integer("base_per_security_micros"), j.integer("base_per_account_micros"),
                j.integer("happened_at"), j.string("happened_day"), if (j.isNull("settlement_day")) null else j.string("settlement_day"),
                j.string("coupon_key"), j.string("note"), j.boolean("cash_already_booked"))
        } }
    }

    data class Valuation(val accountId: String, val currency: String, val marketMinor: Long, val asOf: Long) {
        init { uuid(accountId); currency(currency); minor(marketMinor); require(asOf > 0) }
        fun json(): JSONObject = JSONObject().put("account_id", accountId).put("currency", currency)
            .put("market_minor", marketMinor).put("as_of", asOf)
        companion object { fun parse(j: JSONObject): Valuation {
            j.keysOnly("account_id", "currency", "market_minor", "as_of")
            return Valuation(j.string("account_id"), j.string("currency"), j.integer("market_minor"), j.integer("as_of"))
        } }
    }

    data class Fx(val usd: Long, val eur: Long, val byn: Long, val asOf: Long) {
        init { rate(usd); rate(eur); rate(byn); require(asOf > 0) }
        fun forCurrency(code: String): Long = when (currency(code)) { "USD" -> usd; "EUR" -> eur; "BYN" -> byn; else -> 1_000_000L }
        fun json(): JSONObject = JSONObject().put("base_currency", "RUB").put("USD", usd).put("EUR", eur).put("BYN", byn).put("as_of", asOf)
        companion object { fun parse(j: JSONObject): Fx {
            j.keysOnly("base_currency", "USD", "EUR", "BYN", "as_of"); require(j.string("base_currency") == "RUB")
            return Fx(j.integer("USD"), j.integer("EUR"), j.integer("BYN"), j.integer("as_of"))
        } }
    }

    data class Command(val targetId: String, val expectedRevision: Long, val action: String, val operation: Operation) {
        init { uuid(targetId); require(expectedRevision > 0 && action in setOf("EDIT", "DELETE")) }
        fun json(): JSONObject = JSONObject().put("target_id", targetId).put("expected_revision", expectedRevision)
            .put("action", action).put("operation", operation.json())
        companion object { fun parse(j: JSONObject): Command {
            j.keysOnly("target_id", "expected_revision", "action", "operation")
            return Command(j.string("target_id"), j.integer("expected_revision"), j.string("action"), Operation.parse(j.getJSONObject("operation")))
        } }
    }

    /** No Float/Double coercion: a fractional, string or overflowing wire amount must be rejected. */
    fun JSONObject.integer(key: String): Long = when (val value = get(key)) {
        is Int -> value.toLong(); is Long -> value; else -> error("Expected integer: $key")
    }
    fun JSONObject.string(key: String): String = (get(key) as? String) ?: error("Expected string: $key")
    fun JSONObject.boolean(key: String): Boolean = (get(key) as? Boolean) ?: error("Expected boolean: $key")
    fun JSONObject.keysOnly(vararg fields: String) {
        val keys = keys().asSequence().toSet()
        require(keys == fields.toSet()) { "Некорректные поля протокола" }
    }
    fun bounded(wire: String) { require(wire.toByteArray(Charsets.UTF_8).size <= MAX_RECORD_BYTES) { "Событие слишком велико" } }
    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
            JSONObject.quote(it) + ":" + canonical(value.get(it))
        }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        is Int, is Long, is Boolean -> value.toString()
        else -> error("Unsupported wire type")
    }
}
