package com.mamoro.investments.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mamoro.investments.ui.theme.InvestmentsTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.graphics.Bitmap
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PremiumRenderTest {
    @get:Rule val rule = createAndroidComposeRule<PremiumCaptureActivity>()
    @Test fun portfolio() {
        rule.onNodeWithText("Стоимость портфеля").assertIsDisplayed()
        rule.waitForIdle()
        capture("portfolio")
    }
    @Test fun actions() {
        rule.onNodeWithText("Быстрое действие").performClick()
        listOf("Купить бумагу", "Продать", "Добавить дивиденд", "Добавить купон", "Пополнить счёт", "Вывести средства")
            .forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        capture("actions")
        rule.onNodeWithContentDescription("Закрыть меню").performClick()
        rule.onNodeWithText("Быстрые действия").assertDoesNotExist()
        rule.onNodeWithText("Стоимость портфеля").assertIsDisplayed()
    }
    @Test fun history() {
        rule.onNodeWithText("Операции").performClick()
        rule.onNodeWithText("История операций").assertIsDisplayed()
        capture("operations")
        rule.onNodeWithText("Покупки").performClick()
        rule.onNodeWithText("Продажа").assertDoesNotExist()
        rule.onNodeWithText("Выплата дивидендов").assertDoesNotExist()
        rule.onNodeWithContentDescription("Назад к портфелю").performClick()
        rule.onNodeWithText("Стоимость портфеля").assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi") fun tabletKeepsContentInsideWorkspace() {
        val caption = rule.onNodeWithText("Стоимость портфеля").assertIsDisplayed().fetchSemanticsNode()
        org.junit.Assert.assertTrue("Portfolio stays within the tablet workspace",
            caption.boundsInRoot.left >= 180f && caption.boundsInRoot.right <= 1268f)
        capture("tablet-landscape")
        // A complete amount must remain visible, including its final zeroes, at 150% font scale.
        rule.runOnUiThread {
            rule.activity.setContent {
                InvestmentsTheme {
                    CompositionLocalProvider(LocalDensity provides Density(1f,1.5f)) {
                        Box(Modifier.fillMaxSize().padding(16.dp)) {
                            DataHistoryChart(listOf(PortfolioHistoryPoint(0L,136_000_000L),
                                PortfolioHistoryPoint(86_400_000L,148_000_000L)),
                                Modifier.width(420.dp).height(250.dp), axes = true)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("1\u00a0520\u00a0000").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val result = layouts.single()
        capture("axis-font-150")
        org.junit.Assert.assertFalse("Axis values must show every digit at 150%: size=${result.size} constraints=${result.layoutInput.constraints} widthOverflow=${result.didOverflowWidth} heightOverflow=${result.didOverflowHeight} lineEnd=${result.getLineEnd(0)} text=${result.layoutInput.text}", result.hasVisualOverflow)
    }
    @Test fun cashActionStaysInWallet() {
        rule.onNodeWithText("Быстрое действие").performClick()
        rule.onNodeWithText("Пополнить счёт").performClick()
        rule.onNodeWithText("Пополнение счёта").assertIsDisplayed()
        rule.onNodeWithText("Понятно").performClick()
        rule.onNodeWithText("Стоимость портфеля").assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi") fun portraitTabletUsesOneVerticalScroll() {
        rule.onNodeWithText("Стоимость портфеля").assertIsDisplayed()
        rule.onNodeWithText("Все операции").performScrollTo().assertIsDisplayed()
        capture("tablet-portrait")
    }
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi") fun tabletRecentOperationsOpensHistory() {
        rule.onNodeWithTag("tablet-navigation").assertIsDisplayed()
        rule.onNodeWithTag("portfolio-holdings").assertIsDisplayed()
        rule.onNodeWithText("Последние операции").assertIsDisplayed()
        rule.onNodeWithText("Все операции").performClick()
        rule.mainClock.advanceTimeBy(400) // Pager transition and the tablet header expansion.
        rule.waitForIdle()
        capture("tablet-history")
        rule.onNodeWithText("История операций").assertIsDisplayed()
    }
    @Test fun selectedPeriodSurvivesTabSwitch() {
        rule.onNodeWithText("3М").performClick()
        rule.onNodeWithText("3М").assertIsSelected()
        rule.onNodeWithText("Активы").performClick()
        rule.onNodeWithText("Мои бумаги").assertIsDisplayed()
        rule.onNodeWithText("Портфель").performClick()
        rule.onNodeWithText("3М").assertIsSelected()
    }
    @Test fun emptyHistoryDoesNotInventReturns() {
        rule.activity.intent.putExtra("screen", "empty")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("История стоимости портфеля пока недоступна").assertIsDisplayed()
        rule.onNode(hasContentDescription("История стоимости:", substring = true)).assertDoesNotExist()
        rule.onNodeWithText("+12\u00a0560 ₽", substring = true).assertDoesNotExist()
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            android.view.inspector.WindowInspector.getGlobalWindowViews().filter { it.isShown }.forEach { root ->
                val position = IntArray(2)
                root.getLocationOnScreen(position)
                canvas.save(); canvas.translate(position[0].toFloat(), position[1].toFloat())
                root.draw(canvas); canvas.restore()
            }
            val file = File("build/outputs/premium-host-captures/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
