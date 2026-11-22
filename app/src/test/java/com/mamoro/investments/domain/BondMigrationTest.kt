package com.mamoro.investments.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamoro.investments.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BondMigrationTest {
    @Test fun openingVersionOnePreservesEveryFinancialTable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("""CREATE TABLE IF NOT EXISTS `securities` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `ticker` TEXT NOT NULL, `kind` TEXT NOT NULL, `lastPriceMinor` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
            db.execSQL("""CREATE INDEX IF NOT EXISTS `index_securities_ticker` ON `securities` (`ticker`)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `trades` (`id` TEXT NOT NULL, `securityId` TEXT NOT NULL, `side` TEXT NOT NULL, `quantityMicros` INTEGER NOT NULL, `unitPriceMinor` INTEGER NOT NULL, `feeMinor` INTEGER NOT NULL, `happenedAt` INTEGER NOT NULL, `walletAccountId` TEXT NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`securityId`) REFERENCES `securities`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""")
            db.execSQL("""CREATE INDEX IF NOT EXISTS `index_trades_securityId` ON `trades` (`securityId`)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `income` (`id` TEXT NOT NULL, `securityId` TEXT NOT NULL, `kind` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `note` TEXT NOT NULL, `happenedAt` INTEGER NOT NULL, `walletAccountId` TEXT NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`securityId`) REFERENCES `securities`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""")
            db.execSQL("""CREATE INDEX IF NOT EXISTS `index_income_securityId` ON `income` (`securityId`)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `sync_events` (`sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` TEXT NOT NULL, `operationId` TEXT NOT NULL, `eventKind` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)""")
            db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_events_eventId` ON `sync_events` (`eventId`)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `wallet_accounts` (`accountId` TEXT NOT NULL, `name` TEXT NOT NULL, `currency` TEXT NOT NULL, `balanceMinor` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`accountId`))""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `sync_state` (`id` INTEGER NOT NULL, `ackSequence` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)""")
            db.execSQL("""INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'bf9161934c112c350887e152a21c4be3')""")

            db.execSQL("INSERT INTO securities VALUES ('s','ОФЗ','SU','BOND',100000,1,0)")
            db.execSQL("INSERT INTO trades VALUES ('t','s','BUY',10000000,99000,100,2,'a',0)")
            db.execSQL("INSERT INTO income VALUES ('i','s','COUPON',2500,'Получен',3,'a',0)")
            db.execSQL("INSERT INTO wallet_accounts VALUES ('a','Брокер','RUB',500000,4)")
            db.execSQL("INSERT INTO sync_events VALUES (1,'e','t','TRADE_UPSERT','{}',5)")
            db.execSQL("INSERT INTO sync_state VALUES (1,1)")
            db.version = 1
        }
        val db = Room.databaseBuilder(context, InvestmentDatabase::class.java, name)
            .addMigrations(BondMigrationV2).allowMainThreadQueries().build()
        try {
            assertEquals(100000L, db.dao().getSecurity("s")!!.lastPriceMinor)
            assertEquals(10000000L, db.dao().getTrade("t")!!.quantityMicros)
            assertEquals(2500L, db.dao().getIncome("i")!!.amountMinor)
            assertEquals("Получен", db.dao().getIncome("i")!!.note)
            assertEquals(500000L, db.dao().getAccount("a")!!.balanceMinor)
            assertEquals(1L, db.dao().getMaxSequence())
            assertEquals(1L, db.dao().getAckSequence())
            assertNull(db.bondDao().reference("s"))
            assertTrue(db.bondDao().tradeDates().isEmpty())
            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
