package com.mamoro.investments.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.mamoro.investments.data.InvestmentDatabase
import com.mamoro.investments.data.SyncStateEntity
import com.mamoro.investments.data.WalletAccountEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * V1 walletfree sync contract. Intentionally signature-restricted; the Wallet app
 * must explicitly opt in and be signed with the matching certificate.
 * This provider does not itself change walletfree's ledger.
 */
class InvestmentsSyncProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.mamoro.investments.sync"
        val EVENTS_URI: Uri = Uri.parse("content://$AUTHORITY/events")
        const val SYNC_PERMISSION = "com.mamoro.investments.permission.SYNC"
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        verifyCaller()
        require(uri.path == "/events") { "Unsupported URI" }
        val after = (uri.getQueryParameter("after")?.toLongOrNull() ?: 0L).coerceAtLeast(0L)
        val db = InvestmentDatabase.get(requireNotNull(context))
        val rows = runBlocking(Dispatchers.IO) { db.dao().eventsAfter(after, 500) }
        val cursor = MatrixCursor(arrayOf("sequence", "event_id", "operation_id", "kind", "payload_json", "created_at"))
        rows.forEach { cursor.addRow(arrayOf(it.sequence, it.eventId, it.operationId, it.eventKind, it.payloadJson, it.createdAt)) }
        cursor.setNotificationUri(requireNotNull(context).contentResolver, EVENTS_URI)
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        verifyCaller() // do not rely on ContentProvider read/write permissions protecting call().
        val params = requireNotNull(extras)
        val dao = InvestmentDatabase.get(requireNotNull(context)).dao()
        return when (method) {
            "publishWalletAccount" -> {
                val accountId = requireNotNull(params.getString("account_id")).trim()
                val name = requireNotNull(params.getString("name")).trim()
                val currency = requireNotNull(params.getString("currency"))
                val balance = params.getLong("balance_minor")
                val updatedAt = params.getLong("updated_at")
                require(accountId.isNotBlank() && name.isNotBlank() && accountId.length <= 128 && name.length <= 160)
                require(currency == "RUB") { "Only RUB is supported by protocol v1" }
                require(updatedAt > 0L)
                runBlocking(Dispatchers.IO) {
                    val old = dao.getAccount(accountId)
                    if (old == null || old.updatedAt <= updatedAt) {
                        dao.upsertAccount(WalletAccountEntity(accountId, name, currency, balance, updatedAt))
                    }
                }
                Bundle().apply { putBoolean("ok", true) }
            }
            "ackEvents" -> {
                val ack = params.getLong("through_sequence", -1L)
                require(ack >= 0L)
                runBlocking(Dispatchers.IO) {
                    val max = dao.getMaxSequence()
                    require(ack <= max) { "Cannot ack unissued events" }
                    val old = dao.getAckSequence() ?: 0L
                    if (ack > old) dao.upsertSyncState(SyncStateEntity(ackSequence = ack))
                }
                Bundle().apply { putBoolean("ok", true) }
            }
            else -> throw UnsupportedOperationException("Unknown sync method")
        }
    }

    private fun verifyCaller() {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return
        val pm = requireNotNull(context).packageManager
        if (pm.checkSignatures(Process.myUid(), uid) != PackageManager.SIGNATURE_MATCH) {
            throw SecurityException("Investment sync requires matching signing certificate")
        }
    }

    override fun getType(uri: Uri): String? =
        if (uri.path == "/events") "vnd.android.cursor.dir/vnd.mamoro.investment-event" else null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
