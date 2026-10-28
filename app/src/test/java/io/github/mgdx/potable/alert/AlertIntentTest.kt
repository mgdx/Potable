package io.github.mgdx.potable.alert

import android.content.Context
import android.content.Intent
import io.github.mgdx.potable.MainActivity
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.follow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AlertIntentTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `the notification intent opens the main activity on the unit's sheet`() {
        val follow = follow(7, communeCode = "2A004", udiCode = "020000123")
        val intent = sheetIntent(context, follow)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(context.packageName, intent.component?.packageName)
        assertEquals(
            SheetTarget("2A004", "020000123"),
            intent.sheetTarget(),
        )
    }

    @Test
    fun `an intent without the codes opens nothing`() {
        assertNull(Intent(context, MainActivity::class.java).sheetTarget())
        val onlyCommune = Intent(context, MainActivity::class.java)
            .putExtra("io.github.mgdx.potable.extra.COMMUNE", "45234")
        assertNull(onlyCommune.sheetTarget())
    }

    @Test
    fun `malformed codes from another application open nothing`() {
        val intent = sheetIntent(context, follow(1)).putExtra(
            "io.github.mgdx.potable.extra.UDI",
            "../045000123",
        )
        assertNull(intent.sheetTarget())
    }

    @Test
    fun `the notification intent carries no name`() {
        val extras = sheetIntent(context, follow(1)).extras!!.keySet()
        assertEquals(
            setOf("io.github.mgdx.potable.extra.COMMUNE", "io.github.mgdx.potable.extra.UDI"),
            extras,
        )
    }

    @Test
    fun `a followed unit is named as it was followed, whatever names the intent carries`() =
        runTest {
            val follows = FakeFollowRepository(listOf(follow(1)))
            val intent = sheetIntent(context, follow(1))
                .putExtra("io.github.mgdx.potable.extra.COMMUNE_NAME", "Paris")
                .putExtra("io.github.mgdx.potable.extra.UDI_NAME", "PARIS CENTRE")
            assertEquals(
                SheetNames("Commune 45234", "UDI 045000123"),
                intent.sheetTarget()!!.names(follows),
            )
        }

    @Test
    fun `a unit that is not followed has no names, so its sheet does not open`() = runTest {
        // The forged intent of the acceptance report (A6): Paris, unit EST.
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra("io.github.mgdx.potable.extra.COMMUNE", "75056")
            .putExtra("io.github.mgdx.potable.extra.UDI", "075000227")
            .putExtra("io.github.mgdx.potable.extra.COMMUNE_NAME", "Paris")
        val follows = FakeFollowRepository(listOf(follow(1)))
        assertNull(intent.sheetTarget()!!.names(follows))
    }

    @Test
    fun `a unit no longer followed has no names`() = runTest {
        val intent = sheetIntent(context, follow(1))
        assertNull(intent.sheetTarget()!!.names(FakeFollowRepository()))
    }

    @Test
    fun `an intent replayed from the recent apps does not open the sheet again`() {
        val intent = sheetIntent(
            context,
            follow(1),
        ).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(intent.sheetTarget())
        assertNull(intent.alertTarget())
    }

    @Test
    fun `the summary intent opens My communes in the main activity`() {
        val intent = followsIntent(context)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(FollowsTarget, intent.alertTarget())
        assertNull(intent.sheetTarget())
    }

    @Test
    fun `a unit's intent still opens its sheet`() {
        val follow = follow(3, communeCode = "13055", udiCode = "013000123")
        assertEquals(
            SheetTarget("13055", "013000123"),
            sheetIntent(context, follow).alertTarget(),
        )
    }

    @Test
    fun `the summary intent is told apart from a unit's by the system`() {
        // PendingIntents compare intents without their extras.
        assertFalse(followsIntent(context).filterEquals(sheetIntent(context, follow(1))))
    }

    @Test
    fun `a summary intent replayed from the recent apps opens nothing`() {
        val intent = followsIntent(context).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(intent.alertTarget())
    }

    @Test
    fun `an intent without a target opens nothing`() {
        assertNull(Intent(context, MainActivity::class.java).alertTarget())
    }
}
