package com.mothblank.notasegura

import androidx.activity.ComponentActivity
import android.widget.DatePicker
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.mothblank.notasegura.ui.AccessibilityTags
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.Purchase
import java.time.LocalDate
import org.hamcrest.Matcher
import com.mothblank.notasegura.ui.theme.NotaSeguraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AccessibilityRegressionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun clearPersistentUiState() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext as NotaSeguraApplication

        runBlocking {
            withContext(Dispatchers.IO) {
                app.database.clearAllTables()
            }
        }
    }

    @Test
    fun primaryScreens_keepMinimum48DpTouchTargets() {
        setAppContent()
        assertClickableTouchTargetsAtLeast48Dp()

        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_EMPTY_ACTION).performClick()
        assertClickableTouchTargetsAtLeast48Dp()
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_SAVE).performScrollTo()
        assertClickableTouchTargetsAtLeast48Dp()

        composeRule.onNodeWithContentDescription(string(R.string.common_back)).performClick()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENTS_TAB).performClick()
        assertClickableTouchTargetsAtLeast48Dp()

        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_EMPTY_ACTION).performClick()
        assertClickableTouchTargetsAtLeast48Dp()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_SAVE).performScrollTo()
        assertClickableTouchTargetsAtLeast48Dp()
    }

    @Test
    fun actionableSemantics_areNamedAndIconOnlyActionsHaveDescriptions() {
        setAppContent()
        assertClickableSemanticsAreNamed()

        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_SEARCH)
            .performTextInput("produto inexistente")
        composeRule.onNodeWithContentDescription(string(R.string.search_clear))
            .assertHasClickAction()

        composeRule.onNodeWithContentDescription(string(R.string.search_clear)).performClick()
        composeRule.onNodeWithTag(AccessibilityTags.PRIMARY_ADD_ACTION).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.common_back))
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription(string(R.string.select_purchase_date))
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription(string(R.string.select_warranty_end))
            .assertHasClickAction()

        assertClickableSemanticsAreNamed()
    }

    @Test
    fun purchaseDate_acceptsTypingAndCalendarRemainsAccessible() {
        setAppContent()
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_EMPTY_ACTION).performClick()
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_PRODUCT_FIELD)
            .performTextInput("Produto de teste")

        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_DATE_FIELD)
            .performTextInput("31022026")
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_DATE_FIELD)
            .assertTextContains("31/02/2026")
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_SAVE)
            .performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_DATE_FIELD)
            .performTextClearance()
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_DATE_FIELD)
            .performTextInput("12102026")
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_DATE_FIELD)
            .assertTextContains("12/10/2026")
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_SAVE)
            .performScrollTo().assertIsEnabled()

        composeRule.onNodeWithContentDescription(string(R.string.select_purchase_date))
            .performScrollTo().performClick()
        onView(isAssignableFrom(DatePicker::class.java)).check(matches(isDisplayed()))
        selectNativeDate(2020, 9, 21)
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_DATE_FIELD)
            .assertTextContains("21/09/2020")

        composeRule.onNodeWithTag(AccessibilityTags.WARRANTY_DATE_FIELD)
            .performScrollTo().performTextInput("11122027")
        composeRule.onNodeWithTag(AccessibilityTags.WARRANTY_DATE_FIELD)
            .assertTextContains("11/12/2027")
        composeRule.onNodeWithContentDescription(string(R.string.select_warranty_end))
            .performScrollTo().performClick()
        onView(isAssignableFrom(DatePicker::class.java)).check(matches(isDisplayed()))
        selectNativeDate(2028, 12, 24)
        composeRule.onNodeWithTag(AccessibilityTags.WARRANTY_DATE_FIELD)
            .assertTextContains("24/12/2028")
    }

    @Test
    fun paymentDueDate_canBeTypedOrChosenInNativeCalendar() {
        setAppContent()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENTS_TAB).performClick()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_EMPTY_ACTION).performClick()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_TITLE_FIELD)
            .performTextInput("Conta de teste")

        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_DATE_FIELD)
            .performTextInput("31022026")
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_DATE_FIELD)
            .assertTextContains("31/02/2026")
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_SAVE)
            .performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_DATE_FIELD).performTextClearance()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_DATE_FIELD)
            .performTextInput("12102026")
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_DATE_FIELD)
            .assertTextContains("12/10/2026")
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_SAVE)
            .performScrollTo().assertIsEnabled()

        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_DATE_FIELD).performTextClearance()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_SAVE)
            .performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(string(R.string.select_payment_due_date))
            .performScrollTo().performClick()
        onView(isAssignableFrom(DatePicker::class.java)).check(matches(isDisplayed()))
        selectNativeDate(2029, 7, 14)
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_DATE_FIELD)
            .assertTextContains("14/07/2029")
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_SAVE)
            .performScrollTo().assertIsEnabled()
    }

    @Test
    fun headingsAndHomeReadingOrder_areStable() {
        setAppContent()

        composeRule.onNode(
            isHeading() and hasText(string(R.string.dashboard_title))
        ).assertExists()

        val dashboardTop = composeRule
            .onNodeWithTag(AccessibilityTags.DASHBOARD)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val searchTop = composeRule
            .onNodeWithTag(AccessibilityTags.PURCHASE_SEARCH)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue("Expected dashboard before purchase search", dashboardTop < searchTop)

        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_LIST)
            .performScrollToNode(hasTestTag(AccessibilityTags.PURCHASE_EMPTY_ACTION))
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_EMPTY_ACTION).performClick()
        composeRule.onNode(
            isHeading() and hasText(string(R.string.purchase_section_document))
        ).assertExists()
        composeRule.onNode(
            isHeading() and hasText(string(R.string.purchase_section_product))
        ).assertExists()
        composeRule.onNode(
            isHeading() and hasText(string(R.string.purchase_section_warranty))
        ).assertExists()
    }

    @Test
    fun denseDeadlines_doNotBlockPurchaseScrollingOnCompactDisplays() {
        val app = composeRule.activity.application as NotaSeguraApplication
        val today = LocalDate.now()
        runBlocking {
            withContext(Dispatchers.IO) {
                repeat(20) { index ->
                    app.database.paymentDao().insertPayment(
                        Payment(
                            id = "payment_$index",
                            title = "Pagamento $index",
                            amountCents = 1_000L,
                            dueDate = today.minusDays(index.toLong())
                        )
                    )
                }
                repeat(9) { index ->
                    app.database.purchaseDao().savePurchase(
                        Purchase(
                            id = "purchase_$index",
                            productName = "Produto $index",
                            purchaseDate = today.minusDays(40),
                            warrantyEndDate = today.plusDays(index.toLong()),
                            createdAt = 1L,
                            updatedAt = 1L
                        ),
                        emptyList(), emptyList(), emptyList()
                    )
                }
            }
        }
        val compactDisplay = DeviceConfigurationOverride.FontScale(1.5f)
            .then(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 480.dp)))
        setAppContent(compactDisplay)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Pagamentos: 20 · Garantias: 9"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // The dashboard opens expanded, and scroll is still available at large font sizes.
        composeRule.onNodeWithTag(AccessibilityTags.DASHBOARD_TOGGLE)
            .assertTextContains(string(R.string.dashboard_collapse))
        composeRule.onNodeWithText("19 pagamentos atrasados").assertExists()
        // One payment and one warranty expire today.
        composeRule.onNodeWithText("2 prazos vencem hoje").assertExists()
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_LIST).performScrollToIndex(9)
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_CARD_PREFIX + "purchase_8")
            .assertIsDisplayed()

        // Collapsing only hides the detail cards; urgent deadlines remain unmistakable.
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_LIST).performScrollToIndex(0)
        composeRule.onNodeWithTag(AccessibilityTags.DASHBOARD_TOGGLE).performClick()
        composeRule.onNodeWithTag(AccessibilityTags.DASHBOARD_TOGGLE)
            .assertTextContains(string(R.string.dashboard_expand))
        composeRule.onNodeWithText("19 pagamentos atrasados").assertExists()
        composeRule.onNodeWithText("2 prazos vencem hoje").assertExists()
    }

    @Test
    fun typography_respondsToSystemFontScale() {
        composeRule.setContent {
            Column {
                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.FontScale(1f)
                ) {
                    NotaSeguraTheme {
                        Text(
                            text = "Acessibilidade",
                            modifier = Modifier.testTag("font_scale_normal"),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.FontScale(2f)
                ) {
                    NotaSeguraTheme {
                        Text(
                            text = "Acessibilidade",
                            modifier = Modifier.testTag("font_scale_large"),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        val normalHeight = composeRule
            .onNodeWithTag("font_scale_normal")
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val largeHeight = composeRule
            .onNodeWithTag("font_scale_large")
            .fetchSemanticsNode()
            .boundsInRoot
            .height

        assertTrue(
            "NotaSegura typography must grow when Android font scale grows",
            largeHeight > normalHeight
        )
    }

    @Test
    fun criticalPurchaseAndPaymentFlows_remainReachableAtLargeFontAndDisplayScale() {
        val constrainedLargeText =
            DeviceConfigurationOverride.FontScale(1.5f)
                .then(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 480.dp)))

        setAppContent(constrainedLargeText)

        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_LIST)
            .performScrollToNode(hasTestTag(AccessibilityTags.PURCHASE_EMPTY_ACTION))
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_EMPTY_ACTION)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_PRODUCT_FIELD)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_SAVE)
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription(string(R.string.common_back)).performClick()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENTS_TAB).performClick()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_EMPTY_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithContentDescription(string(R.string.select_payment_due_date))
            .assertHasClickAction()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_TITLE_FIELD)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_SAVE)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun androidAccessibilityFramework_passesCriticalScreens() {
        setAppContent()
        composeRule.enableAccessibilityChecks()

        composeRule.onRoot().tryPerformAccessibilityChecks()

        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_EMPTY_ACTION).performClick()
        composeRule.onRoot().tryPerformAccessibilityChecks()

        composeRule.onNodeWithContentDescription(string(R.string.common_back)).performClick()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENTS_TAB).performClick()
        composeRule.onRoot().tryPerformAccessibilityChecks()

        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_EMPTY_ACTION).performClick()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    /** Sets an actual date in Android's platform picker before confirming it. */
    private fun selectNativeDate(year: Int, month: Int, day: Int) {
        onView(isAssignableFrom(DatePicker::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> =
                isAssignableFrom(DatePicker::class.java)

            override fun getDescription(): String = "Select native calendar date"

            override fun perform(uiController: UiController, view: View) {
                (view as DatePicker).updateDate(year, month - 1, day)
            }
        })
        onView(withId(android.R.id.button1)).perform(click())
    }

    private fun setAppContent(
        configurationOverride: DeviceConfigurationOverride? = null
    ) {
        composeRule.setContent {
            if (configurationOverride == null) {
                NotaSeguraTheme {
                    NotaSeguraApp()
                }
            } else {
                DeviceConfigurationOverride(configurationOverride) {
                    NotaSeguraTheme {
                        NotaSeguraApp()
                    }
                }
            }
        }
    }

    private fun assertClickableTouchTargetsAtLeast48Dp() {
        val clickableNodes = composeRule
            .onAllNodes(hasClickAction())
            .fetchSemanticsNodes()

        assertTrue("Expected at least one clickable semantics node", clickableNodes.isNotEmpty())

        val minimumPixels = with(composeRule.density) { 48.dp.toPx() }
        clickableNodes
            .filter { node ->
                node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f
            }
            .forEach { node ->
            val bounds = node.touchBoundsInRoot
            assertTrue(
                "Touch target is narrower than 48dp: ${node.config}",
                bounds.width + 0.5f >= minimumPixels
            )
            assertTrue(
                "Touch target is shorter than 48dp: ${node.config}",
                bounds.height + 0.5f >= minimumPixels
            )
        }
    }

    private fun assertClickableSemanticsAreNamed() {
        val clickableNodes = composeRule
            .onAllNodes(hasClickAction())
            .fetchSemanticsNodes()

        clickableNodes
            .filter { node ->
                node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f
            }
            .forEach { node ->
            val config = node.config
            if (config.contains(SemanticsActions.SetText)) {
                return@forEach
            }

            val contentDescriptions = config
                .getOrNull(SemanticsProperties.ContentDescription)
                .orEmpty()
                .filter { it.isNotBlank() }
            val text = config
                .getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .map { it.text }
                .filter { it.isNotBlank() }
            val stateDescription = config
                .getOrNull(SemanticsProperties.StateDescription)
                .orEmpty()
            val clickLabel = config
                .getOrNull(SemanticsActions.OnClick)
                ?.label
                .orEmpty()

            assertTrue(
                "Clickable element has no accessible name or action label: $config",
                contentDescriptions.isNotEmpty() ||
                    text.isNotEmpty() ||
                    stateDescription.isNotBlank() ||
                    clickLabel.isNotBlank()
            )
        }
    }

    private fun string(resId: Int): String =
        composeRule.activity.getString(resId)
}
