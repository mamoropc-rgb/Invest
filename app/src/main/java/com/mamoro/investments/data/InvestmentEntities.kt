package com.mamoro.investments.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "securities", indices = [Index(value = ["ticker"])])
data class SecurityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ticker: String,
    val kind: String,
    val lastPriceMinor: Long,
    val createdAt: Long,
    val archived: Boolean = false
)

@Entity(
    tableName = "trades",
    foreignKeys = [ForeignKey(entity = SecurityEntity::class, parentColumns = ["id"], childColumns = ["securityId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["securityId"])]
)
data class TradeEntity(
    @PrimaryKey val id: String,
    val securityId: String,
    val side: String, // BUY / SELL
    val quantityMicros: Long,
    val unitPriceMinor: Long,
    val feeMinor: Long,
    val happenedAt: Long,
    val walletAccountId: String = "",
    val deleted: Boolean = false
)

@Entity(
    tableName = "income",
    foreignKeys = [ForeignKey(entity = SecurityEntity::class, parentColumns = ["id"], childColumns = ["securityId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["securityId"])]
)
data class IncomeEntity(
    @PrimaryKey val id: String,
    val securityId: String,
    val kind: String, // DIVIDEND / COUPON
    val amountMinor: Long,
    val note: String,
    val happenedAt: Long,
    val walletAccountId: String = "",
    val deleted: Boolean = false
)

@Entity(tableName = "sync_events", indices = [Index(value = ["eventId"], unique = true)])
data class SyncEventEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val eventId: String,
    val operationId: String,
    val eventKind: String,
    val payloadJson: String,
    val createdAt: Long
)

@Entity(tableName = "wallet_accounts")
data class WalletAccountEntity(
    @PrimaryKey val accountId: String,
    val name: String,
    val currency: String,
    val balanceMinor: Long,
    val updatedAt: Long
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val ackSequence: Long = 0
)
