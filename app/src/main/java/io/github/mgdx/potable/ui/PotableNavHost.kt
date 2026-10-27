package io.github.mgdx.potable.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.alert.AlertTarget
import io.github.mgdx.potable.alert.FollowsTarget
import io.github.mgdx.potable.alert.SheetNames
import io.github.mgdx.potable.alert.SheetTarget
import io.github.mgdx.potable.alert.names
import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.ui.about.AboutScreen
import io.github.mgdx.potable.ui.follows.FollowsScreen
import io.github.mgdx.potable.ui.follows.FollowsViewModel
import io.github.mgdx.potable.ui.history.ParameterHistoryScreen
import io.github.mgdx.potable.ui.history.ParameterHistoryViewModel
import io.github.mgdx.potable.ui.history.historyCodes
import io.github.mgdx.potable.ui.sample.SampleDetailScreen
import io.github.mgdx.potable.ui.sample.SampleDetailViewModel
import io.github.mgdx.potable.ui.sample.SampleReportScreen
import io.github.mgdx.potable.ui.sample.SampleReportViewModel
import io.github.mgdx.potable.ui.search.SearchScreen
import io.github.mgdx.potable.ui.search.SearchViewModel
import io.github.mgdx.potable.ui.settings.SettingsScreen
import io.github.mgdx.potable.ui.settings.SettingsViewModel
import io.github.mgdx.potable.ui.sheet.SheetScreen
import io.github.mgdx.potable.ui.sheet.SheetViewModel
import io.github.mgdx.potable.ui.timeline.TimelineScreen
import io.github.mgdx.potable.ui.timeline.TimelineViewModel
import io.github.mgdx.potable.ui.udi.UdiScreen
import io.github.mgdx.potable.ui.udi.UdiViewModel
import kotlinx.coroutines.CancellationException

/** Navigation routes. Arguments are URL-encoded when a route is built. */
object Routes {
    const val SEARCH = "search"
    const val ABOUT = "about"
    const val SETTINGS = "settings"

    /** "My communes" (SPEC §5 F6). */
    const val FOLLOWS = "follows"

    const val ARG_COMMUNE = "commune"
    const val ARG_NAME = "name"
    const val ARG_UDI = "udi"
    const val ARG_UDI_NAME = "udiName"
    const val ARG_SAMPLE = "sample"
    const val ARG_CHOOSE = "choose"
    const val ARG_CHANGE = "change"
    const val ARG_CODES = "codes"
    const val ARG_LABEL = "label"

    /**
     * Resolution, then choice when there are several, of a commune's units;
     * [ARG_CHOOSE] always offers the choice (the user asked to change).
     */
    const val UDIS = "udis/{$ARG_COMMUNE}?$ARG_NAME={$ARG_NAME}&$ARG_CHOOSE={$ARG_CHOOSE}"

    /**
     * The "my water" sheet of a commune's unit; [ARG_CHANGE] offers to go
     * back to the choice of unit, for a commune that has several.
     */
    const val SHEET = "sheet/{$ARG_COMMUNE}/{$ARG_UDI}" +
        "?$ARG_NAME={$ARG_NAME}&$ARG_UDI_NAME={$ARG_UDI_NAME}&$ARG_CHANGE={$ARG_CHANGE}"

    /**
     * Every result of the sample the sheet below holds. The sample is not
     * passed nor loaded again: the screen reads the sheet's ViewModel, scoped
     * to the sheet's back stack entry; the code only checks it is the same.
     */
    const val SAMPLE = "sample/{$ARG_SAMPLE}"

    /** The timeline of the conclusions of a unit's samples (SPEC §5 F5). */
    const val TIMELINE = "timeline/{$ARG_COMMUNE}/{$ARG_UDI}?$ARG_NAME={$ARG_NAME}"

    /** The history of one parameter of a unit (SPEC §5 F5); see [parameterHistory]. */
    const val HISTORY =
        "history/{$ARG_COMMUNE}/{$ARG_UDI}?$ARG_CODES={$ARG_CODES}&$ARG_LABEL={$ARG_LABEL}"

    /**
     * The full report of any sample of a unit, loaded by its code (SPEC §5
     * F4), opened from a parameter's history; see [report].
     */
    const val REPORT = "report/{$ARG_COMMUNE}/{$ARG_UDI}/{$ARG_SAMPLE}"

    fun udis(communeCode: String, name: String, choose: Boolean = false) =
        "udis/${Uri.encode(communeCode)}?$ARG_NAME=${Uri.encode(name)}&$ARG_CHOOSE=$choose"

    fun sheet(
        communeCode: String,
        udiCode: String,
        name: String,
        udiName: String,
        change: Boolean = false,
    ) = "sheet/${Uri.encode(communeCode)}/${Uri.encode(udiCode)}" +
        "?$ARG_NAME=${Uri.encode(name)}&$ARG_UDI_NAME=${Uri.encode(udiName)}&$ARG_CHANGE=$change"

    fun sheet(follow: Follow) =
        sheet(follow.communeCode, follow.udiCode, follow.communeName, follow.udiName)

    fun sample(sampleCode: String) = "sample/${Uri.encode(sampleCode)}"

    /** The report of the sample [sampleCode] of the unit [udiCode] serving [communeCode]. */
    fun report(communeCode: String, udiCode: String, sampleCode: String) =
        "report/${Uri.encode(communeCode)}/${Uri.encode(udiCode)}/${Uri.encode(sampleCode)}"

    fun timeline(communeCode: String, udiCode: String, name: String) =
        "timeline/${Uri.encode(communeCode)}/${Uri.encode(udiCode)}?$ARG_NAME=${Uri.encode(name)}"

    /**
     * The history screen of one parameter (SPEC §5 F5) of the unit [udiCode]
     * serving [communeCode] (the code Hub'Eau publishes the commune under).
     *
     * [parameterCodes] are every `code_parametre` the parameter is published
     * under (hardness has two: `FeaturedParameter.codes`, or
     * [io.github.mgdx.potable.ui.history.historyCodes] from a single code);
     * not empty, and no code may contain a comma. [label] is the title until
     * the series is loaded; the API label (`libelle_parametre`) then replaces
     * it. The window opens on the history setting and can be changed there.
     */
    fun parameterHistory(
        communeCode: String,
        udiCode: String,
        parameterCodes: Set<String>,
        label: String,
    ): String {
        require(parameterCodes.isNotEmpty()) { "no parameter code" }
        return "history/${Uri.encode(communeCode)}/${Uri.encode(udiCode)}" +
            "?$ARG_CODES=${Uri.encode(parameterCodes.joinToString(","))}" +
            "&$ARG_LABEL=${Uri.encode(label)}"
    }
}

private fun requiredString(name: String) = navArgument(name) {
    type = NavType.StringType
}

private fun optionalString(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

private fun flag(name: String) = navArgument(name) {
    type = NavType.BoolType
    defaultValue = false
}

private fun NavBackStackEntry.string(name: String): String? = arguments?.getString(name)

private fun NavBackStackEntry.flag(name: String): Boolean = arguments?.getBoolean(name) ?: false

/** The sheet's ViewModel, stored in the sheet's back stack entry, whoever asks for it. */
@Composable
private fun sheetViewModel(container: AppContainer, sheetEntry: NavBackStackEntry): SheetViewModel {
    val udiCode = sheetEntry.string(Routes.ARG_UDI).orEmpty()
    return viewModel(
        viewModelStoreOwner = sheetEntry,
        factory = SheetViewModel.factory(
            container = container,
            communeCode = sheetEntry.string(Routes.ARG_COMMUNE).orEmpty(),
            udiCode = udiCode,
            communeName = sheetEntry.string(Routes.ARG_NAME).orEmpty(),
            udiName = sheetEntry.string(Routes.ARG_UDI_NAME) ?: udiCode,
        ),
    )
}

/** The entry of the sheet in the back stack, or null when there is none. */
internal fun NavController.sheetEntryOrNull(): NavBackStackEntry? = try {
    getBackStackEntry(Routes.SHEET)
} catch (_: IllegalArgumentException) {
    null
}

/**
 * Leaves a sample screen that has no sheet below it: back to the screen
 * below, or, when it is alone, to the search in its place.
 */
internal fun NavController.leaveOrphanSample() {
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(Routes.SEARCH) {
            popUpTo(Routes.SAMPLE) { inclusive = true }
        }
    }
}

/** Goes back to the search if it is below, or opens it otherwise. */
private fun NavHostController.openSearch() {
    if (!popBackStack(Routes.SEARCH, inclusive = false)) navigate(Routes.SEARCH)
}

/** Opens the sheet of [target], named [names], unless it is the one already shown. */
private fun NavHostController.openSheet(target: SheetTarget, names: SheetNames) {
    val current = currentBackStackEntry
    val shown = current?.destination?.route == Routes.SHEET &&
        current.string(Routes.ARG_COMMUNE) == target.communeCode &&
        current.string(Routes.ARG_UDI) == target.udiCode
    if (shown) return
    navigate(Routes.sheet(target.communeCode, target.udiCode, names.communeName, names.udiName))
}

/** [target]'s names from Potable's own data, or null (unknown, or unreadable). */
private suspend fun sheetNames(container: AppContainer, target: SheetTarget): SheetNames? = try {
    target.names(container.follows, container.communes)
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/** Opens "My communes", unless it is already shown. */
private fun NavHostController.openFollows() {
    if (currentBackStackEntry?.destination?.route == Routes.FOLLOWS) return
    navigate(Routes.FOLLOWS)
}

/**
 * The application's navigation. It always opens on the search, whatever is
 * followed (SPEC §5 F6).
 *
 * [alertTarget], from an alert notification (a sheet, or "My communes" for
 * the summary of several), is opened on top of the search as soon as the
 * graph is there; [onAlertTargetOpened] is then called, once.
 */
@Composable
fun PotableNavHost(
    container: AppContainer,
    alertTarget: AlertTarget? = null,
    onAlertTargetOpened: () -> Unit = {},
) {
    val navController = rememberNavController()
    LaunchedEffect(alertTarget) {
        when (alertTarget) {
            null -> return@LaunchedEffect
            is SheetTarget -> sheetNames(container, alertTarget)
                ?.let { navController.openSheet(alertTarget, it) }
            FollowsTarget -> navController.openFollows()
        }
        onAlertTargetOpened()
    }
    NavHost(navController = navController, startDestination = Routes.SEARCH) {
        composable(Routes.SEARCH) {
            SearchScreen(
                viewModel = viewModel(factory = SearchViewModel.factory(container)),
                onCommuneSelected = { commune, choose ->
                    // Hub'Eau is queried under the code it publishes the place under.
                    // With several known units, the full choice opens (choose).
                    navController.navigate(Routes.udis(commune.queryCode, commune.name, choose))
                },
                onKnownUdiSelected = { commune, communeName, udi ->
                    // Like a remembered unit: its sheet offers to change it.
                    navController.navigate(
                        Routes.sheet(
                            commune.queryCode,
                            udi.code,
                            communeName,
                            udi.name,
                            change = true,
                        ),
                    )
                },
                onRecentSelected = { unit ->
                    navController.navigate(
                        Routes.sheet(
                            unit.communeCode,
                            unit.udiCode,
                            unit.communeName,
                            unit.udiName,
                            change = true,
                        ),
                    )
                },
                onMyCommunes = { navController.navigate(Routes.FOLLOWS) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.navigateUp() },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel(factory = SettingsViewModel.factory(container)),
                onBack = { navController.navigateUp() },
            )
        }
        composable(Routes.FOLLOWS) {
            FollowsScreen(
                viewModel = viewModel(factory = FollowsViewModel.factory(container)),
                onBack = { navController.navigateUp() },
                onOpen = { follow -> navController.navigate(Routes.sheet(follow)) },
                onSearch = { navController.openSearch() },
            )
        }
        composable(
            route = Routes.UDIS,
            arguments = listOf(
                requiredString(Routes.ARG_COMMUNE),
                optionalString(Routes.ARG_NAME),
                flag(Routes.ARG_CHOOSE),
            ),
        ) { entry ->
            val communeCode = entry.string(Routes.ARG_COMMUNE).orEmpty()
            val name = entry.string(Routes.ARG_NAME).orEmpty()
            val forceChoice = entry.flag(Routes.ARG_CHOOSE)
            UdiScreen(
                viewModel = viewModel(
                    factory = UdiViewModel.factory(container, communeCode, forceChoice),
                ),
                communeName = name,
                onBack = { navController.navigateUp() },
                onUdiResolved = { udi ->
                    navController.navigate(Routes.sheet(communeCode, udi.code, name, udi.name)) {
                        // Back from the sheet returns to the search, not to a
                        // resolution screen that would forward again at once.
                        popUpTo(Routes.UDIS) { inclusive = true }
                    }
                },
                onUdiRemembered = { udi ->
                    // Same as above, but the sheet offers to change the unit.
                    // A unit just picked goes this way too: back from its
                    // sheet does not offer the choice again (SPEC §5 F2).
                    val route = Routes.sheet(communeCode, udi.code, name, udi.name, change = true)
                    navController.navigate(route) {
                        popUpTo(Routes.UDIS) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.SHEET,
            arguments = listOf(
                requiredString(Routes.ARG_COMMUNE),
                requiredString(Routes.ARG_UDI),
                optionalString(Routes.ARG_NAME),
                optionalString(Routes.ARG_UDI_NAME),
                flag(Routes.ARG_CHANGE),
            ),
        ) { entry ->
            val communeCode = entry.string(Routes.ARG_COMMUNE).orEmpty()
            val communeName = entry.string(Routes.ARG_NAME).orEmpty()
            val udiCode = entry.string(Routes.ARG_UDI).orEmpty()
            // The search is always below a sheet. Should a sheet ever be
            // alone (a back stack restored oddly), it shows no back arrow and
            // offers the search in its top bar instead, rather than a dead end.
            val isRoot = remember(entry) { navController.previousBackStackEntry == null }
            val fromFollows = remember(entry) {
                navController.previousBackStackEntry?.destination?.route == Routes.FOLLOWS
            }
            val onChangeUdi: (() -> Unit)? = if (entry.flag(Routes.ARG_CHANGE)) {
                {
                    val route = Routes.udis(communeCode, communeName, choose = true)
                    navController.navigate(route) {
                        // Back from the choice returns where this sheet did.
                        popUpTo(Routes.SHEET) { inclusive = true }
                    }
                }
            } else {
                null
            }
            SheetScreen(
                viewModel = sheetViewModel(container, entry),
                communeName = communeName,
                udiName = entry.string(Routes.ARG_UDI_NAME),
                onBack = if (isRoot) null else ({ navController.navigateUp() }),
                onShowAllResults = { sample -> navController.navigate(Routes.sample(sample.code)) },
                onShowHistory = {
                    navController.navigate(Routes.timeline(communeCode, udiCode, communeName))
                },
                onParameterHistory = { codes, label ->
                    navController.navigate(
                        Routes.parameterHistory(communeCode, udiCode, codes, label),
                    )
                },
                // Opened from My communes: going there again would stack a
                // second copy, so the bar offers the overflow menu instead.
                onMyCommunes = if (fromFollows) {
                    null
                } else {
                    { navController.navigate(Routes.FOLLOWS) }
                },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onAbout = { navController.navigate(Routes.ABOUT) },
                onSearch = if (isRoot) ({ navController.openSearch() }) else null,
                onChangeUdi = onChangeUdi,
            )
        }
        composable(
            route = Routes.SAMPLE,
            arguments = listOf(navArgument(Routes.ARG_SAMPLE) { type = NavType.StringType }),
        ) { entry ->
            // The sheet's entry is below this one, the only way here; were it
            // missing, the screen goes away instead of crashing.
            val sheetEntry = remember(entry) { navController.sheetEntryOrNull() }
            if (sheetEntry == null) {
                LaunchedEffect(entry) { navController.leaveOrphanSample() }
                return@composable
            }
            val sheet = sheetViewModel(container, sheetEntry)
            SampleDetailScreen(
                viewModel = viewModel(
                    factory = SampleDetailViewModel.factory(
                        entry.string(Routes.ARG_SAMPLE).orEmpty(),
                        sheet.state,
                    ),
                ),
                onRetry = sheet::retry,
                onBack = { navController.navigateUp() },
                onOpenHistory = { result ->
                    navController.navigate(
                        Routes.parameterHistory(
                            communeCode = sheetEntry.string(Routes.ARG_COMMUNE).orEmpty(),
                            udiCode = sheetEntry.string(Routes.ARG_UDI).orEmpty(),
                            parameterCodes = historyCodes(result.parameterCode),
                            label = result.parameterLabel,
                        ),
                    )
                },
            )
        }
        composable(
            route = Routes.HISTORY,
            arguments = listOf(
                requiredString(Routes.ARG_COMMUNE),
                requiredString(Routes.ARG_UDI),
                optionalString(Routes.ARG_CODES),
                optionalString(Routes.ARG_LABEL),
            ),
        ) { entry ->
            val codes = entry.string(Routes.ARG_CODES).orEmpty()
                .split(',')
                .filter { it.isNotBlank() }
                .toSet()
            val communeCode = entry.string(Routes.ARG_COMMUNE).orEmpty()
            val udiCode = entry.string(Routes.ARG_UDI).orEmpty()
            ParameterHistoryScreen(
                viewModel = viewModel(
                    factory = ParameterHistoryViewModel.factory(
                        container,
                        communeCode = communeCode,
                        udiCode = udiCode,
                        parameterCodes = codes,
                    ),
                ),
                label = entry.string(Routes.ARG_LABEL).orEmpty(),
                onBack = { navController.navigateUp() },
                onOpenSample = { sampleCode ->
                    navController.navigate(Routes.report(communeCode, udiCode, sampleCode))
                },
            )
        }
        composable(
            route = Routes.REPORT,
            arguments = listOf(
                requiredString(Routes.ARG_COMMUNE),
                requiredString(Routes.ARG_UDI),
                requiredString(Routes.ARG_SAMPLE),
            ),
        ) { entry ->
            val communeCode = entry.string(Routes.ARG_COMMUNE).orEmpty()
            val udiCode = entry.string(Routes.ARG_UDI).orEmpty()
            SampleReportScreen(
                viewModel = viewModel(
                    factory = SampleReportViewModel.factory(
                        container,
                        communeCode = communeCode,
                        udiCode = udiCode,
                        sampleCode = entry.string(Routes.ARG_SAMPLE).orEmpty(),
                    ),
                ),
                onBack = { navController.navigateUp() },
                onOpenHistory = { result ->
                    navController.navigate(
                        Routes.parameterHistory(
                            communeCode = communeCode,
                            udiCode = udiCode,
                            parameterCodes = historyCodes(result.parameterCode),
                            label = result.parameterLabel,
                        ),
                    )
                },
            )
        }
        composable(
            route = Routes.TIMELINE,
            arguments = listOf(
                requiredString(Routes.ARG_COMMUNE),
                requiredString(Routes.ARG_UDI),
                optionalString(Routes.ARG_NAME),
            ),
        ) { entry ->
            TimelineScreen(
                viewModel = viewModel(
                    factory = TimelineViewModel.factory(
                        container,
                        entry.string(Routes.ARG_COMMUNE).orEmpty(),
                        entry.string(Routes.ARG_UDI).orEmpty(),
                    ),
                ),
                communeName = entry.string(Routes.ARG_NAME).orEmpty(),
                onBack = { navController.navigateUp() },
            )
        }
    }
}
