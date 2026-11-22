package com.mamoro.investments.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bond_reference", foreignKeys = [ForeignKey(entity = SecurityEntity::class,
    parentColumns = ["id"], childColumns = ["securityId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["ref_uid"], unique = true)])
data class BondReferenceEntity(@PrimaryKey val securityId: String,
    @Embedded(prefix = "ref_") val reference: BondReference, val fromDay: Long, val throughDay: Long)

@Entity(tableName = "bond_coupons", primaryKeys = ["securityId", "eventKey"],
    foreignKeys = [ForeignKey(entity = SecurityEntity::class, parentColumns = ["id"],
        childColumns = ["securityId"], onDelete = ForeignKey.RESTRICT)])
data class BondCouponEntity(val securityId: String, @Embedded val coupon: BondCoupon, val active: Boolean = true)

@Entity(tableName = "bond_trade_dates", foreignKeys = [ForeignKey(entity = TradeEntity::class,
    parentColumns = ["id"], childColumns = ["tradeId"], onDelete = ForeignKey.RESTRICT)])
data class BondTradeDateEntity(@PrimaryKey val tradeId: String, val executionDay: Long, val settlementDay: Long?)

@Entity(tableName = "bond_coupon_receipts", primaryKeys = ["securityId", "eventKey"],
    foreignKeys = [ForeignKey(entity = SecurityEntity::class, parentColumns = ["id"], childColumns = ["securityId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = IncomeEntity::class, parentColumns = ["id"], childColumns = ["incomeId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["incomeId"], unique = true)])
data class BondCouponReceiptEntity(val securityId: String, val eventKey: String, val incomeId: String)

@Dao
interface BondDao {
    @Query("SELECT * FROM bond_reference") fun observeReferences(): Flow<List<BondReferenceEntity>>
    @Query("SELECT * FROM bond_coupons ORDER BY payDay") fun observeCoupons(): Flow<List<BondCouponEntity>>
    @Query("SELECT * FROM bond_trade_dates") fun observeTradeDates(): Flow<List<BondTradeDateEntity>>
    @Query("SELECT * FROM bond_coupon_receipts") fun observeReceipts(): Flow<List<BondCouponReceiptEntity>>
    @Query("SELECT * FROM bond_reference WHERE securityId=:id") suspend fun reference(id: String): BondReferenceEntity?
    @Query("SELECT * FROM bond_reference WHERE ref_uid=:uid") suspend fun byUid(uid: String): BondReferenceEntity?
    @Query("SELECT * FROM bond_coupons WHERE securityId=:id AND eventKey=:key") suspend fun coupon(id: String, key: String): BondCouponEntity?
    @Query("SELECT * FROM bond_coupon_receipts WHERE securityId=:id AND eventKey=:key") suspend fun receipt(id: String, key: String): BondCouponReceiptEntity?
    @Query("SELECT * FROM bond_coupon_receipts WHERE incomeId=:id") suspend fun receiptForIncome(id: String): BondCouponReceiptEntity?
    @Query("SELECT * FROM bond_trade_dates WHERE tradeId=:id") suspend fun tradeDate(id: String): BondTradeDateEntity?
    @Query("SELECT * FROM bond_trade_dates") suspend fun tradeDates(): List<BondTradeDateEntity>
    @Query("UPDATE bond_coupons SET active=0 WHERE securityId=:id AND payDay BETWEEN :from AND :through")
    suspend fun invalidateCovered(id: String, from: Long, through: Long)
    @Upsert suspend fun saveReference(value: BondReferenceEntity)
    @Upsert suspend fun saveCoupons(values: List<BondCouponEntity>)
    @Upsert suspend fun saveTradeDate(value: BondTradeDateEntity)
    @Upsert suspend fun saveReceipt(value: BondCouponReceiptEntity)
}

/** Additive only: the six financial/sync tables and their rows are never rewritten. */
object BondMigrationV2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `bond_reference` (
            `securityId` TEXT NOT NULL, `ref_uid` TEXT NOT NULL, `ref_figi` TEXT NOT NULL,
            `ref_isin` TEXT NOT NULL, `ref_ticker` TEXT NOT NULL, `ref_classCode` TEXT NOT NULL,
            `ref_name` TEXT NOT NULL, `ref_currency` TEXT NOT NULL, `ref_nominal` TEXT,
            `ref_accruedInterest` TEXT, `ref_issueDay` INTEGER, `ref_placementDay` INTEGER,
            `ref_maturityDay` INTEGER, `ref_couponsPerYear` INTEGER NOT NULL, `ref_floating` INTEGER NOT NULL,
            `ref_amortizing` INTEGER NOT NULL, `ref_perpetual` INTEGER NOT NULL, `ref_loadedAt` INTEGER NOT NULL,
            `fromDay` INTEGER NOT NULL, `throughDay` INTEGER NOT NULL, PRIMARY KEY(`securityId`),
            FOREIGN KEY(`securityId`) REFERENCES `securities`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bond_reference_ref_uid` ON `bond_reference` (`ref_uid`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `bond_coupons` (`securityId` TEXT NOT NULL,
            `eventKey` TEXT NOT NULL, `number` INTEGER NOT NULL, `payDay` INTEGER NOT NULL, `fixDay` INTEGER,
            `startDay` INTEGER, `endDay` INTEGER, `amountPerBond` TEXT, `currency` TEXT NOT NULL,
            `type` TEXT NOT NULL, `active` INTEGER NOT NULL, PRIMARY KEY(`securityId`,`eventKey`),
            FOREIGN KEY(`securityId`) REFERENCES `securities`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `bond_trade_dates` (`tradeId` TEXT NOT NULL,
            `executionDay` INTEGER NOT NULL, `settlementDay` INTEGER, PRIMARY KEY(`tradeId`),
            FOREIGN KEY(`tradeId`) REFERENCES `trades`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `bond_coupon_receipts` (`securityId` TEXT NOT NULL,
            `eventKey` TEXT NOT NULL, `incomeId` TEXT NOT NULL, PRIMARY KEY(`securityId`,`eventKey`),
            FOREIGN KEY(`securityId`) REFERENCES `securities`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(`incomeId`) REFERENCES `income`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bond_coupon_receipts_incomeId` ON `bond_coupon_receipts` (`incomeId`)")
    }
}
