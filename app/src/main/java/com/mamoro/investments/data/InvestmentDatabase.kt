package com.mamoro.investments.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SecurityEntity::class, TradeEntity::class, IncomeEntity::class,
        SyncEventEntity::class, WalletAccountEntity::class, SyncStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class InvestmentDatabase : RoomDatabase() {
    abstract fun dao(): InvestmentDao

    companion object {
        @Volatile private var instance: InvestmentDatabase? = null
        fun get(context: Context): InvestmentDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                InvestmentDatabase::class.java,
                "investments_v1.db"
            ).build().also { instance = it }
        }
    }
}
