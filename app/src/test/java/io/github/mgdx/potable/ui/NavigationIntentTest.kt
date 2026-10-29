package io.github.mgdx.potable.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import androidx.savedstate.read
import io.github.mgdx.potable.MainActivity
import io.github.mgdx.potable.alert.FollowsTarget
import io.github.mgdx.potable.alert.SheetTarget
import io.github.mgdx.potable.alert.alertTarget
import io.github.mgdx.potable.alert.followsIntent
import io.github.mgdx.potable.alert.sanitized
import io.github.mgdx.potable.alert.sheetIntent
import io.github.mgdx.potable.ui.navigation.ParameterHistoryRoute
import io.github.mgdx.potable.ui.navigation.ReportResultsRoute
import io.github.mgdx.potable.ui.navigation.ReportRoute
import io.github.mgdx.potable.ui.navigation.SampleRoute
import io.github.mgdx.potable.ui.navigation.SearchRoute
import io.github.mgdx.potable.ui.navigation.SheetRoute
import io.github.mgdx.potable.ui.navigation.TimelineRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The launching intent of the exported activity must not drive the
 * navigation graph (audit V2), and a sample screen without its sheet must
 * not crash.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationIntentTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    /** The routes of the real graph that matter here, the search as start. */
    private fun navController() = NavHostController(context).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = SearchRoute) {
            composable<SearchRoute> {}
            composable<SheetRoute> {}
            composable<SampleRoute> {}
            composable<TimelineRoute> {}
            composable<ParameterHistoryRoute> {}
            composable<ReportRoute> {}
            composable<ReportResultsRoute> {}
        }
    }

    /** What another application can send: the sample screen alone, deep-linked. */
    private fun hostileIntent(controller: NavHostController): Intent {
        val graph = controller.graph
        val ids = intArrayOf(graph.id, graph.findNode<SampleRoute>()!!.id)
        val args = arrayListOf(Bundle(), Bundle().apply { putString("sampleCode", "X") })
        return Intent(context, MainActivity::class.java)
            .setData(Uri.parse("android-app://androidx.navigation/sample/X"))
            .putExtra("android-support-nav:controller:deepLinkIds", ids)
            .putParcelableArrayListExtra("android-support-nav:controller:deepLinkArgs", args)
            .putExtra("android-support-nav:controller:deepLinkExtras", Bundle())
    }

    @Test
    fun `the raw intent would open the sample screen alone`() {
        // The attack the sanitising stops: without it, Navigation obeys.
        val controller = navController()
        assertTrue(controller.handleDeepLink(hostileIntent(controller)))
        assertTrue(controller.currentDestination!!.hasRoute<SampleRoute>())
        assertNull(controller.sheetEntryOrNull())
    }

    @Test
    fun `the sanitised intent leaves the start destination alone`() {
        val controller = navController()
        val clean = hostileIntent(controller).sanitized()

        assertFalse(controller.handleDeepLink(clean))
        assertTrue(controller.currentDestination!!.hasRoute<SearchRoute>())
        assertNull(clean.data)
        assertTrue(clean.extras?.isEmpty ?: true)
        assertEquals(MainActivity::class.java.name, clean.component?.className)
    }

    @Test
    fun `the sanitised intent keeps the alert targets`() {
        val unit = sheetIntent(context, follow(1))
            .putExtra("android-support-nav:controller:deepLinkIds", intArrayOf(1, 2))
        assertEquals(SheetTarget("45234", "045000123"), unit.sanitized().alertTarget())
        assertEquals(
            setOf("io.github.mgdx.potable.extra.COMMUNE", "io.github.mgdx.potable.extra.UDI"),
            unit.sanitized().extras!!.keySet(),
        )
        assertEquals(FollowsTarget, followsIntent(context).sanitized().alertTarget())
        // Still ignored when replayed from the recent apps.
        val replayed = unit.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(replayed.sanitized().alertTarget())
    }

    @Test
    fun `an alert code that is not a string is dropped`() {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("io.github.mgdx.potable.extra.COMMUNE", 45234)
            .putExtra("io.github.mgdx.potable.extra.UDI", "045000123")
        val clean = intent.sanitized()
        assertNull(clean.alertTarget())
        assertEquals(setOf("io.github.mgdx.potable.extra.UDI"), clean.extras!!.keySet())
    }

    @Test
    fun `a sample screen alone gives way to the search`() {
        val controller = navController()
        controller.handleDeepLink(hostileIntent(controller))

        controller.leaveOrphanSample()

        assertTrue(controller.currentDestination!!.hasRoute<SearchRoute>())
        assertNull(controller.previousBackStackEntry)
    }

    @Test
    fun `a sample screen without a sheet below goes back`() {
        val controller = navController()
        controller.navigate(SampleRoute("X"))
        assertNull(controller.sheetEntryOrNull())

        controller.leaveOrphanSample()

        assertTrue(controller.currentDestination!!.hasRoute<SearchRoute>())
    }

    @Test
    fun `the sheet entry is found below a sample screen`() {
        val controller = navController()
        controller.navigate(SheetRoute("45234", "045000123", "Orléans", "ORLEANS"))
        controller.navigate(SampleRoute("X"))

        assertNotNull(controller.sheetEntryOrNull())
    }

    @Test
    fun `the report entry is found below its results`() {
        val controller = navController()
        controller.navigate(ReportRoute("45234", "045000123", "P1"))
        controller.navigate(ReportResultsRoute("45234", "045000123", "P1"))

        assertTrue(controller.currentDestination!!.hasRoute<ReportResultsRoute>())
        val report = controller.reportEntryOrNull()
        assertNotNull(report)
        assertEquals("P1", report!!.toRoute<ReportRoute>().sampleCode)
    }

    @Test
    fun `report results without their report go back`() {
        val controller = navController()
        controller.navigate(ReportResultsRoute("45234", "045000123", "P1"))
        assertNull(controller.reportEntryOrNull())

        controller.leaveOrphanSample(ReportResultsRoute::class)

        assertTrue(controller.currentDestination!!.hasRoute<SearchRoute>())
    }

    @Test
    fun `a sample of the timeline opens its report, and back returns to the timeline`() {
        val controller = navController()
        controller.navigate(SheetRoute("45234", "045000123", "Orléans", "ORLEANS"))
        controller.navigate(TimelineRoute("45234", "045000123", "Orléans"))
        controller.navigate(ReportRoute("45234", "045000123", "P1"))

        assertNotNull(controller.reportEntryOrNull())
        assertTrue(controller.popBackStack())
        assertTrue(controller.currentDestination!!.hasRoute<TimelineRoute>())
    }

    @Test
    fun `the arguments of a route survive the saved state, special characters included`() {
        // The ViewModels read their route back from their SavedStateHandle.
        val controller = navController()
        val history = ParameterHistoryRoute.of(
            "2A004",
            "02A000/1 & 2?",
            linkedSetOf("1345", "1346"),
            "Titre hydrotimétrique, TH",
        )
        controller.navigate(history)

        val arguments = controller.currentBackStackEntry!!.arguments!!
        val handle = SavedStateHandle(arguments.read { toMap() })
        assertEquals(history, handle.toRoute<ParameterHistoryRoute>())

        val sheet = SheetRoute("45234", "045000123", "Saint-Jean-de-Braye")
        controller.navigate(sheet)
        assertEquals(sheet, controller.sheetEntryOrNull()!!.toRoute<SheetRoute>())
    }
}
