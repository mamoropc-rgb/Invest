package com.mamoro.investments.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mamoro.investments.domain.MoneyMath
import com.mamoro.investments.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BondUiTest {
    @get:Rule val rule=createAndroidComposeRule<PremiumCaptureActivity>()
    private fun show(mode: String, font: Float=1f) {
        rule.runOnUiThread { rule.activity.setContent {
            InvestmentsTheme { CompositionLocalProvider(LocalDensity provides Density(1f,font)) { BondCaptureScreen(mode) } }
        } }
        rule.waitForIdle()
    }
    @Test fun searchShowsNamesAndIssueIdentifiers() {
        var selected=""
        rule.runOnUiThread { rule.activity.setContent {
            InvestmentsTheme { BondSearchContent(BondBrowserState(results=BondCaptureFixtures.results,searched=true),true,null,
                {},{ selected=it },{},{},{},{}) }
        } }
        rule.onNodeWithText("ОФЗ 26242 · пример").assertIsDisplayed().performClick()
        assertEquals("demo-bond",selected)
        capture("bond-search")
    }
    @Test fun disconnectedSearchExplainsTokenSetup() {
        rule.runOnUiThread { rule.activity.setContent {
            InvestmentsTheme { BondSearchContent(BondBrowserState(),false,null,{},{},{},{},{},{}) }
        } }
        rule.onNodeWithText("Добавить токен").assertIsDisplayed()
        rule.onNodeWithText("Найти").assertDoesNotExist()
    }
    @Test fun referencePreservesMetadataAndManualMarketPrice() {
        show("bond-reference")
        rule.onNodeWithText("Дата выпуска").assertIsDisplayed()
        rule.onNodeWithText("НКД по справочнику").assertIsDisplayed()
        rule.onNodeWithText("Текущая цена за облигацию, ₽").performScrollTo().assertIsDisplayed()
        capture("bond-reference")
    }
    @Test fun receivedFilterShowsActualIncomeAfterSale() {
        show("bond-calendar")
        rule.onNodeWithText("Полученные").performClick()
        rule.onNodeWithText(MoneyMath.formatMoney(27188L)).assertIsDisplayed()
        rule.onNodeWithText("Купон №11 · после налога").assertIsDisplayed()
        capture("bond-received")
    }
    @Test fun unpaidCouponCanBeConfirmedWithoutChangingFutureToIncome() {
        var key=""
        rule.runOnUiThread { rule.activity.setContent {
            InvestmentsTheme { BondCalendarContent(BondCaptureFixtures.dashboard,BondCaptureFixtures.bonds,
                BondCaptureFixtures.payments,null,{}, { key=it.coupon.eventKey },today=BondCaptureFixtures.today) }
        } }
        rule.onNodeWithText("Подтвердить получение").performScrollTo().performClick()
        assertEquals("demo-bond/12",key)
        assertEquals(1,BondCaptureFixtures.dashboard.income.size)
        capture("bond-calendar")
    }
    @Test fun unknownCouponDoesNotBecomeZeroIncome() {
        show("bond-schedule")
        rule.onNodeWithTag("bond-calendar-list").performScrollToNode(hasText("Не объявлено"))
        rule.onNodeWithText("Не объявлено").assertIsDisplayed()
        rule.onNodeWithText("Оценка · не все даты известны").assertIsDisplayed()
        capture("bond-schedule")
    }
    @Test @Config(qualifiers="w1280dp-h800dp-mdpi") fun tabletCalendarLimitsItsWidth() {
        show("bond-calendar")
        val bounds=rule.onNodeWithText("Календарь купонов").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= 280f && bounds.right < 1000f)
        capture("bond-tablet-landscape")
    }
    @Test @Config(qualifiers="w800dp-h1280dp-mdpi") fun largeFontKeepsDatesAndAmountsReadable() {
        show("bond-calendar",1.5f)
        val layouts=mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("coupon-forecast-total").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("Amount layout: ${layouts.single().size}; constraints=${layouts.single().layoutInput.constraints}; width=${layouts.single().didOverflowWidth}; height=${layouts.single().didOverflowHeight}",layouts.single().hasVisualOverflow)
        rule.onNodeWithText("Расписание").performClick()
        rule.onNodeWithTag("bond-calendar-list").performScrollToNode(hasText("Не объявлено"))
        rule.onNodeWithText("Не объявлено").assertIsDisplayed()
        capture("bond-tablet-portrait-font-150")
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val view=rule.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(bitmap)
            android.view.inspector.WindowInspector.getGlobalWindowViews().filter { it.isShown }.forEach { root ->
                val pos=IntArray(2); root.getLocationOnScreen(pos)
                canvas.save();canvas.translate(pos[0].toFloat(),pos[1].toFloat());root.draw(canvas);canvas.restore()
            }
            val file=File("build/outputs/bond-host-captures/$name.png");file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
}
