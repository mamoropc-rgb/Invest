package com.mamoro.investments.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamoro.investments.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BondRepositoryTest {
    private val today = LocalDate.now().toEpochDay()
    private fun download(amount: String = "25.55", currency: String = "RUB") = BondDownload(
        BondReference("uid","figi","RU000","SU26242","TQOB","ОФЗ",currency,"1000","1.25",
            today-100,today-90,today+365,2,false,false,false,1234),
        listOf(BondCoupon("uid/1",1,today-1,today-3,today-80,today-2,amount,currency,"COUPON_TYPE_CONSTANT")),
        today-100,today+365)

    private fun scenario(block: suspend (InvestmentDatabase,BondRepository,InvestmentsRepository) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context,InvestmentDatabase::class.java).allowMainThreadQueries().build()
        try { block(db,BondRepository(db),InvestmentsRepository(context,db)) } finally { db.close() }
    }
    @Test fun confirmationIsAtomicAndIdempotent() = scenario { db,bonds,repo ->
        val id = bonds.importBond(download(),99000)
        repo.createIncome(id,"COUPON",2200,"Получен","", "uid/1", today)
        assertTrue(runCatching { repo.createIncome(id,"COUPON",2200,"Повтор","", "uid/1") }.isFailure)
        assertEquals(1,repo.dashboard.first().income.size)
        assertEquals(1L,db.dao().getMaxSequence())
        assertEquals(2200L,repo.dashboard.first().summary.couponsMinor)
        val incomeId = db.bondDao().receipt(id,"uid/1")!!.incomeId
        repo.deleteIncome(incomeId)
        repo.createIncome(id,"COUPON",2300,"Исправлено","", "uid/1")
        assertEquals(2300L,repo.dashboard.first().summary.couponsMinor)
    }
    @Test fun refreshAndFullSaleCannotRewritePaidIncome() = scenario { db,bonds,repo ->
        val id = bonds.importBond(download(),99000)
        repo.createTrade(id,"BUY",10000000,99000,0,"",today-10,today-9)
        repo.createIncome(id,"COUPON",22000,"Факт","","uid/1")
        repo.createTrade(id,"SELL",10000000,99500,100,"",today-2,today-1)
        bonds.link(id,download("99.99"))
        val state = bonds.state.first()
        val dashboard = repo.dashboard.first()
        val payment = BondCalendar.calculate(state.coupons,dashboard.trades,state.tradeDates,dashboard.income,state.receipts).single()
        assertEquals(22000L,payment.received!!.amountMinor)
        assertEquals(10000000L,payment.quantityMicros)
        assertEquals(0L,dashboard.summary.positions.single().quantityMicros)
        assertEquals(22000L,dashboard.summary.couponsMinor)
        assertEquals(3L,db.dao().getMaxSequence())
    }
    @Test fun linkingOldIncomeDoesNotDuplicateCash() = scenario { db,bonds,repo ->
        val id = bonds.importBond(download(),99000)
        repo.createIncome(id,"COUPON",2000,"Раньше введён вручную","")
        val incomeId = repo.dashboard.first().income.single().id
        bonds.linkIncome(id,"uid/1",incomeId)
        assertEquals(1L,db.dao().getMaxSequence())
        assertTrue(runCatching { repo.createIncome(id,"COUPON",2000,"","","uid/1") }.isFailure)
        assertEquals(1,repo.dashboard.first().income.size)
    }
    @Test fun failedImportAndInvalidBackdatedSaleRollBack() = scenario { db,bonds,repo ->
        assertTrue(runCatching { bonds.importBond(download(currency="USD"),99000) }.isFailure)
        assertTrue(repo.dashboard.first().securities.isEmpty())
        val id = bonds.importBond(download(),99000)
        repo.createTrade(id,"BUY",1000000,99000,0,"",today-5,today-4)
        assertTrue(runCatching { repo.createTrade(id,"SELL",1000000,99000,0,"",today-10,today-9) }.isFailure)
        assertEquals(1,db.dao().getActiveTrades(id).size)
        assertEquals(1L,db.dao().getMaxSequence())
    }
    @Test fun futureScheduleNeverCreatesIncome() = scenario { db,bonds,repo ->
        val future = download().let { it.copy(coupons=it.coupons.map { c -> c.copy(payDay=today+1) }) }
        val id = bonds.importBond(future,99000)
        assertTrue(runCatching { repo.createIncome(id,"COUPON",100,"","","uid/1") }.isFailure)
        assertTrue(repo.dashboard.first().income.isEmpty())
        assertEquals(0L,db.dao().getMaxSequence())
    }
}
