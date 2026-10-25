package io.github.mgdx.potable.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import io.github.mgdx.potable.MainActivity
import io.github.mgdx.potable.alert.FollowsTarget
import io.github.mgdx.potable.alert.SheetTarget
import io.github.mgdx.potable.alert.alertTarget
import io.github.mgdx.potable.alert.followsIntent
import io.github.mgdx.potable.alert.sanitized
import io.github.mgdx.potable.alert.sheetIntent
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

    private fun string(name: String) = navArgument(name) {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    }

    /** The routes of the real graph that matter here, the search as start. */
    private fun navController() = NavHostController(context).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = Routes.SEARCH) {
            composable(Routes.SEARCH) {}
            composable(
                Routes.SHEET,
                arguments = listOf(
                    navArgument(Routes.ARG_COMMUNE) { type = NavType.StringType },
                    navArgument(Routes.ARG_UDI) { type = NavType.StringType },
                    string(Routes.ARG_NAME),
                    string(Routes.ARG_UDI_NAME),
                    navArgument(Routes.ARG_CHANGE) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) {}
            composable(
                Routes.SAMPLE,
                arguments = listOf(navArgument(Routes.ARG_SAMPLE) { type = NavType.StringType }),
            ) {}
        }
    }

    /** What another application can send: the sample screen alone, deep-linked. */
    private fun hostileIntent(controller: NavHostController): Intent {
        val graph = controller.graph
        val ids = intArrayOf(graph.id, graph.findNode(Routes.SAMPLE)!!.id)
        val args = arrayListOf(Bundle(), Bundle().apply { putString(Routes.ARG_SAMPLE, "X") })
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
        assertEquals(Routes.SAMPLE, controller.currentDestination?.route)
        assertNull(controller.sheetEntryOrNull())
    }

    @Test
    fun `the sanitised intent leaves the start destination alone`() {
        val controller = navController()
        val clean = hostileIntent(controller).sanitized()

        assertFalse(controller.handleDeepLink(clean))
        assertEquals(Routes.SEARCH, controller.currentDestination?.route)
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
    fun `a sample screen alone gives way to the start destination`() {
        val controller = navController()
        controller.handleDeepLink(hostileIntent(controller))

        controller.leaveOrphanSample(primary = null)

        assertEquals(Routes.SEARCH, controller.currentDestination?.route)
        assertNull(controller.previousBackStackEntry)
    }

    @Test
    fun `a sample screen alone gives way to the primary sheet`() {
        val controller = navController()
        controller.handleDeepLink(hostileIntent(controller))

        controller.leaveOrphanSample(primary = follow(1, isPrimary = true))

        assertEquals(Routes.SHEET, controller.currentDestination?.route)
        assertEquals("45234", controller.currentBackStackEntry?.arguments?.getString("commune"))
    }

    @Test
    fun `a sample screen without a sheet below goes back`() {
        val controller = navController()
        controller.navigate(Routes.sample("X"))
        assertNull(controller.sheetEntryOrNull())

        controller.leaveOrphanSample(primary = null)

        assertEquals(Routes.SEARCH, controller.currentDestination?.route)
    }

    @Test
    fun `the sheet entry is found below a sample screen`() {
        val controller = navController()
        controller.navigate(Routes.sheet("45234", "045000123", "Orléans", "ORLEANS"))
        controller.navigate(Routes.sample("X"))

        assertNotNull(controller.sheetEntryOrNull())
    }
}
