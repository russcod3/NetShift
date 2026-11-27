package edu.cs312.netshift.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import edu.cs312.netshift.data.*
import edu.cs312.netshift.data.db.entity.TestResultEntity
import edu.cs312.netshift.model.*
import edu.cs312.netshift.ui.components.*
import edu.cs312.netshift.ui.history.HistoryScreen
import edu.cs312.netshift.ui.results.CompareScreen
import edu.cs312.netshift.ui.test.*
import edu.cs312.netshift.ui.theme.NetShiftTheme
import edu.cs312.netshift.ui.welcome.WelcomeScreen
import edu.cs312.netshift.measurement.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Isolated UI fixtures: no measurement traffic and no changes to saved user history. */
class UiFlowTest {
    @get:Rule val compose = createComposeRule()

    private fun screenshot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(150)
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-review")
        directory.mkdirs()
        val temporary = File(directory, "$name.tmp")
        temporary.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        check(temporary.renameTo(File(directory, "$name.png")))
    }
    @Test fun welcomeHasAnExplicitStartAction() {
        var started = false
        compose.setContent { NetShiftTheme(darkTheme = true) { WelcomeScreen({ started = true }) } }
        compose.onNodeWithText("Get started").assertIsDisplayed()
        screenshot("welcome-dark")
        compose.onNodeWithText("Get started").performClick()
        compose.runOnIdle { assertTrue(started) }
    }
    @Test fun launcherIconLoadsAndRenders() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue(icon is android.graphics.drawable.AdaptiveIconDrawable)
        val rendered = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 256, 256)
        icon.draw(android.graphics.Canvas(rendered))
        compose.setContent { NetShiftTheme(true) {
            Surface(Modifier.fillMaxSize()) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Image(rendered.asImageBitmap(), "Shifty app icon", modifier = Modifier.size(160.dp))
                }
            }
        } }
        compose.onNodeWithContentDescription("Shifty app icon").assertIsDisplayed()
        screenshot("shifty-launcher-icon")
    }
    @Test fun mascotJointsRemainConnectedAtBothWaveExtremes() {
        compose.mainClock.autoAdvance = false
        compose.setContent { NetShiftTheme(true) {
            Surface(Modifier.fillMaxSize()) {
                ShiftyMascot(Modifier.fillMaxSize(), waving = true)
            }
        } }
        // Start the infinite transition before sampling the quarter-cycle poses.
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        screenshot("shifty-wave-positive")
        compose.mainClock.advanceTimeBy(1200)
        screenshot("shifty-wave-negative")
        compose.onNodeWithContentDescription("Shifty the chameleon, Wi-Fi colors").assertIsDisplayed()
    }
    @Test fun themeSwitchAndShiftyReactToState() {
        var dark by mutableStateOf(false)
        var transport by mutableStateOf(NetworkTransport.WIFI)
        compose.setContent {
            NetShiftTheme(darkTheme = dark) {
                Surface { Column {
                    ThemeSwitch(dark, true) { dark = !dark }
                    ShiftyMascot(Modifier.fillMaxWidth().weight(1f), transport, waving = true)
                } }
            }
        }
        compose.onNodeWithContentDescription("Dark theme").assertIsOff()
        screenshot("shifty-wifi-light")
        compose.onNodeWithContentDescription("Dark theme").performClick().assertIsOn()
        compose.runOnIdle { transport = NetworkTransport.CELLULAR }
        compose.onNodeWithContentDescription("Shifty the chameleon, mobile data colors").assertIsDisplayed()
        screenshot("shifty-mobile-dark")
    }
    @Test fun savedMeasurementsAreCardsAndComparePromptIsDismissible() {
        var opened: String? = null
        compose.setContent { NetShiftTheme(false) {
            HistoryScreen(listOf(row("wifi", "WIFI")), 1, { opened = it }, {}, {})
        } }
        compose.onNodeWithText("Library").assertIsDisplayed()
        screenshot("history-light")
        compose.onNodeWithText("Compare networks").performClick()
        compose.onNodeWithText("Compare your connections").assertIsDisplayed()
        screenshot("compare-prompt-light")
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Library").performClick()
        compose.runOnIdle { assertEquals("wifi", opened) }
    }
    @Test fun comparisonShowsMeasuredPairs() {
        val wifi = row("wifi", "WIFI")
        val mobile = row("mobile", "CELLULAR").copy(downloadMbps = 4.0, latencyMedianMs = 80.0)
        val pair = LocationComparison("library", "Library", wifi, mobile, 1000, true)
        compose.setContent { NetShiftTheme(true) { CompareScreen(listOf(pair)) } }
        compose.onNodeWithText("Download", useUnmergedTree = true).assertIsDisplayed()
        screenshot("comparison-dark")
    }
    @Test fun meterShowsRealProbeCountsAndSemantics() {
        compose.setContent { NetShiftTheme(true) {
            NetworkTestMeter(MeasurementState(phase = MeasurementPhase.PROBING, probesAttempted = 7),
                "Measuring response time", "7 of 20 HTTP checks completed.")
        } }
        compose.onNodeWithText("7/20").assertIsDisplayed()
        compose.onNodeWithContentDescription("Network test stage progress")
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
                androidx.compose.ui.semantics.ProgressBarRangeInfo(.35f, 0f..1f)))
        screenshot("test-meter-dark")
    }
    @Test fun mobileConfirmationPrecedesAnyMeasurement() {
        var starts = 0
        val vm = TestViewModel(MutableStateFlow(MeasurementState()),
            { _, _ -> starts++; StartResult.Started("fixture") }, {}, { true }, { flowOf(null) },
            MutableStateFlow(NetworkSnapshot(transport = NetworkTransport.WIFI, isValidated = true, isMetered = false)))
        compose.setContent { NetShiftTheme(false) { TestScreen(vm) } }
        compose.onNodeWithText("Start test").assertIsNotEnabled()
        compose.onNodeWithText("Location name").performTextInput("Library")
        compose.onNodeWithText("Mobile data").performClick()
        compose.onNodeWithText("Start test").performClick()
        compose.onNodeWithText("Use mobile data?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, starts) }
        screenshot("mobile-consent-light")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, starts) }
    }
    @Test fun completedResultHasMetricsAndExpandableVerdicts() {
        val verdicts = VerdictEvaluator.evaluate(VerdictInput(true, 20, 20, 20, 0, 19, 42.0, 8.0, 2000.0, 12.4))
        val result = row("complete", "WIFI").copy(activityVerdictsJson = MeasurementJson.activityVerdicts(verdicts))
        compose.setContent { NetShiftTheme(true) {
            ScreenPage("Test result", "Library") { MeasurementSummary(result, false, false, {}) }
        } }
        compose.onNodeWithText("Test complete").assertIsDisplayed()
        compose.onNodeWithText("12.4 Mbps").assertIsDisplayed()
        screenshot("results-dark")
        compose.onAllNodesWithText("Why this verdict?")[0].performScrollTo().performClick()
        compose.onNodeWithText("Less detail").assertIsDisplayed()
    }
    private fun row(id: String, transport: String) = TestResultEntity(
        id = id, startedAtEpochMs = 1_795_740_000_000, locationName = "Library", locationKey = "library",
        requestedTransport = transport, measuredTransport = transport, status = "COMPLETE",
        latencyMedianMs = 42.0, jitterMs = 8.0, probeLossPercent = 0.0, downloadMbps = 12.4,
        probeCountAttempted = 20, probeCountSucceeded = 20, jitterPairCount = 19,
        downloadBodyDurationMs = 2000.0, downloadStopReason = "BYTE_LIMIT",
    )
}
