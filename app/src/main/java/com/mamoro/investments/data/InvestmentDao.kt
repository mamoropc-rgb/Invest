package com.mamoro.investments.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InvestmentDao {
    @Query("SELECT * FROM securities ORDER BY archived ASC, name COLLATE NOCASE ASC")
    fun observeSecurities(): Flow<List<SecurityEntity>>

    @Query("SELECT * FROM trades WHERE deleted = 0 ORDER BY happenedAt DESC, id DESC")
    fun observeTrades(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM income WHERE deleted = 0 ORDER BY happenedAt DESC, id DESC")
    fun observeIncome(): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM wallet_accounts ORDER BY name COLLATE NOCASE ASC")
    fun observeWalletAccounts(): Flow<List<WalletAccountEntity>>

    @Query("SELECT MAX(sequence) FROM sync_events")
    fun observeMaxSequence(): Flow<Long?>

    @Query("SELECT * FROM sync_state WHERE id = 1 LIMIT 1")
    fun observeSyncState(): Flow<SyncStateEntity?>

    @Query("SELECT * FROM securities WHERE ticker = :ticker LIMIT 1")
    suspend fun getSecurityByTicker(ticker: String): SecurityEntity?

    @Query("SELECT * FROM securities WHERE id = :id LIMIT 1")
    suspend fun getSecurity(id: String): SecurityEntity?

    @Query("SELECT * FROM trades WHERE securityId = :securityId AND deleted = 0 ORDER BY happenedAt ASC, id ASC")
    suspend fun getActiveTrades(securityId: String): List<TradeEntity>

    @Query("SELECT * FROM trades WHERE id = :id LIMIT 1")
    suspend fun getTrade(id: String): TradeEntity?

    @Query("SELECT * FROM income WHERE id = :id LIMIT 1")
    suspend fun getIncome(id: String): IncomeEntity?

    @Query("SELECT * FROM sync_events WHERE sequence > :sequence ORDER BY sequence ASC LIMIT :limit")
    suspend fun eventsAfter(sequence: Long, limit: Int): List<SyncEventEntity>

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM sync_events")
    suspend fun getMaxSequence(): Long

    @Query("SELECT * FROM wallet_accounts WHERE accountId = :id LIMIT 1")
    suspend fun getAccount(id: String): WalletAccountEntity?

    @Query("SELECT ackSequence FROM sync_state WHERE id = 1")
    suspend fun getAckSequence(): Long?

    @Insert suspend fun insertSecurity(value: SecurityEntity)
    @Update suspend fun updateSecurity(value: SecurityEntity)
    @Insert suspend fun insertTrade(value: TradeEntity)
    @Update suspend fun updateTrade(value: TradeEntity)
    @Insert suspend fun insertIncome(value: IncomeEntity)
    @Update suspend fun updateIncome(value: IncomeEntity)
    @Insert suspend fun appendEvent(value: SyncEventEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAccount(value: WalletAccountEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSyncState(value: SyncStateEntity)
}
