// INTEGRATION TEMPLATE for the WalletFree Android project.
// This is NOT compiled into InvestmentsPremiumGlass. Adapt package name and call
// Wallet's own Room repository to apply individual events transactionally.
// Both apps must have matching Android signing certificate for this V1 contract.

package com.walletfree.integration

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject

class WalletInvestmentsBridge(private val resolver: ContentResolver) {
    private val eventsUri = Uri.parse("content://com.mamoro.investments.sync/events")
    private val rootUri = Uri.parse("content://com.mamoro.investments.sync")

    /**
     * Run on Dispatchers.IO, never on the UI thread.
     * Wallet owns and durably stores the last acknowledged sequence.
     * applyEvent MUST be an idempotent, transactional insertion/update/delete of
     * a cash-ledger record keyed by operation_id. Reject empty account mapping.
     * Advance ack ONLY after wallet DB commit succeeds.
     */
    fun importEvents(afterSequence: Long, applyEvent: (eventId: String, payload: JSONObject) -> Unit): Long {
        var lastCommitted = afterSequence
        while (true) {
            var received = 0
            val uri = eventsUri.buildUpon().appendQueryParameter("after", lastCommitted.toString()).build()
            resolver.query(uri, null, null, null, null).use { cursor ->
                requireNotNull(cursor) { "Investments app unavailable" }
                val sequenceCol = cursor.getColumnIndexOrThrow("sequence")
                val eventIdCol = cursor.getColumnIndexOrThrow("event_id")
                val payloadCol = cursor.getColumnIndexOrThrow("payload_json")
                while (cursor.moveToNext()) {
                    val sequence = cursor.getLong(sequenceCol)
                    val eventId = cursor.getString(eventIdCol)
                    val payload = JSONObject(cursor.getString(payloadCol))
                    // The caller must commit the wallet ledger change BEFORE this returns.
                    applyEvent(eventId, payload)
                    lastCommitted = sequence
                    received++
                }
            }
            if (received > 0) {
                val data = Bundle().apply { putLong("through_sequence", lastCommitted) }
                val ack = resolver.call(rootUri, "ackEvents", null, data)
                check(ack?.getBoolean("ok") == true) { "Acknowledgment rejected" }
            }
            if (received < 500) return lastCommitted
        }
    }

    /** Publish only real broker-linked accounts; Wallet is their single source of truth. */
    fun publishAccount(accountId: String, name: String, balanceMinor: Long, updatedAtMillis: Long) {
        val data = Bundle().apply {
            putString("account_id", accountId)
            putString("name", name)
            putString("currency", "RUB")
            putLong("balance_minor", balanceMinor)
            putLong("updated_at", updatedAtMillis)
        }
        val result = resolver.call(rootUri, "publishWalletAccount", null, data)
        check(result?.getBoolean("ok") == true) { "Account snapshot rejected" }
    }
}
