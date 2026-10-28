package io.github.mgdx.potable.alert

import android.content.Context
import android.content.Intent
import io.github.mgdx.potable.MainActivity
import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.data.follow.FollowRepository

/** Where a touched notification leads. */
sealed interface AlertTarget

/**
 * The sheet a notification opens, by its codes only: the main activity is
 * exported, so the names shown come from Potable's own data ([names]), never
 * from the intent.
 */
data class SheetTarget(val communeCode: String, val udiCode: String) : AlertTarget

/** The names a [SheetTarget] is shown under. */
data class SheetNames(val communeName: String, val udiName: String)

/** "My communes", opened by the summary of several notifications. */
data object FollowsTarget : AlertTarget

/**
 * Action of the summary's intent: [Intent.filterEquals] tells it apart from
 * the notifications of the units, whatever their request codes.
 */
private const val ACTION_OPEN_FOLLOWS = "io.github.mgdx.potable.action.OPEN_FOLLOWS"

private const val EXTRA_COMMUNE = "io.github.mgdx.potable.extra.COMMUNE"
private const val EXTRA_UDI = "io.github.mgdx.potable.extra.UDI"

/** INSEE and SANDRE codes: letters and digits only (Corsica's start with 2A, 2B). */
private val CODE = Regex("[0-9A-Za-z]{1,20}")

/**
 * The explicit intent of a notification about [follow]: it opens
 * [MainActivity] on the unit's sheet, the running one if there is one
 * (`onNewIntent`) rather than a second instance.
 */
fun sheetIntent(context: Context, follow: Follow): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
        .putExtra(EXTRA_COMMUNE, follow.communeCode)
        .putExtra(EXTRA_UDI, follow.udiCode)

/**
 * The explicit intent of the summary of several notifications: it opens
 * [MainActivity] on "My communes", the running one if there is one.
 */
fun followsIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
    .setAction(ACTION_OPEN_FOLLOWS)
    .addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP,
    )

/**
 * A copy of this intent reduced to what Potable reads of it: component,
 * action, categories, flags, and the alert codes when they are strings.
 *
 * The main activity is exported, and its intent is also read by Navigation:
 * at the creation of the graph, `NavController.handleDeepLink` builds a back
 * stack from the reserved extras `android-support-nav:controller:deepLinkIds`,
 * `…deepLinkArgs`, `…deepLinkExtras` and `…deepLinkIntent`, and matches the
 * data URI against the graph's deep links. None of these may reach it: any
 * application could open any route with any argument. Everything else, data
 * included, is dropped. An extra that cannot be read is dropped too.
 */
fun Intent.sanitized(): Intent {
    val clean = Intent(action).setComponent(component).setFlags(flags)
    categories?.forEach { clean.addCategory(it) }
    for (key in listOf(EXTRA_COMMUNE, EXTRA_UDI)) {
        val value = try {
            getStringExtra(key)
        } catch (_: RuntimeException) {
            // A hostile bundle that does not unparcel (BadParcelableException).
            null
        }
        value?.let { clean.putExtra(key, it) }
    }
    return clean
}

/**
 * Where an intent asks to go: "My communes" for the summary's intent
 * ([followsIntent]), else its sheet ([sheetTarget]); null when neither, or
 * when it is replayed from the recent apps.
 */
fun Intent.alertTarget(): AlertTarget? {
    if (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
    return if (action == ACTION_OPEN_FOLLOWS) FollowsTarget else sheetTarget()
}

/**
 * The sheet an intent asks for, or null: no or malformed codes (the main
 * activity is exported, so anyone can start it with any extra), or an intent
 * replayed from the recent apps, which must not open the sheet a second time.
 * Any other extra, a name especially, is ignored.
 */
fun Intent.sheetTarget(): SheetTarget? {
    if (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
    val commune = getStringExtra(EXTRA_COMMUNE)?.takeIf { CODE.matches(it) } ?: return null
    val udi = getStringExtra(EXTRA_UDI)?.takeIf { CODE.matches(it) } ?: return null
    return SheetTarget(communeCode = commune, udiCode = udi)
}

/**
 * The names the sheet is shown under: the follow's, a notification being only
 * ever about a followed unit; null for a unit that is not followed, whose
 * sheet does not open. The main activity is exported: any application can
 * send these codes, and only a followed unit may open its sheet (and enter the
 * recent units) this way. A unit no longer followed when its notification is
 * touched leads to "My communes" instead.
 */
suspend fun SheetTarget.names(follows: FollowRepository): SheetNames? =
    follows.find(communeCode, udiCode)?.let { SheetNames(it.communeName, it.udiName) }
