package com.mothblank.notasegura

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.fetchSemanticsNodes
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.mothblank.notasegura.ui.AccessibilityTags
import com.mothblank.notasegura.ui.theme.NotaSeguraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

        composeRule.onNodeWithContentDescription(string(R.string.common_back)).performClick()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENTS_TAB).performClick()
        assertClickableTouchTargetsAtLeast48Dp()

        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_EMPTY_ACTION).performClick()
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

        assertClickableSemanticsAreNamed()
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
        val emptyActionTop = composeRule
            .onNodeWithTag(AccessibilityTags.PURCHASE_EMPTY_ACTION)
            .fetchSemanticsNode()
            .boundsInRoot
            .top

        assertTrue(
            "Expected dashboard -> search -> empty-state action reading order",
            dashboardTop < searchTop && searchTop < emptyActionTop
        )

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
                    DeviceConfigurationOverride.FontScale(1.5f)
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

        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_EMPTY_ACTION).performClick()
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_PRODUCT_FIELD)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(AccessibilityTags.PURCHASE_SAVE)
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription(string(R.string.common_back)).performClick()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENTS_TAB).performClick()
        composeRule.onNodeWithTag(AccessibilityTags.PAYMENT_EMPTY_ACTION).performClick()
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
        clickableNodes.forEach { node ->
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

        clickableNodes.forEach { node ->
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
