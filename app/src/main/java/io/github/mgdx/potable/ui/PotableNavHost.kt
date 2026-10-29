package io.github.mgdx.potable.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.alert.AlertTarget
import io.github.mgdx.potable.alert.FollowsTarget
import io.github.mgdx.potable.alert.SheetNames
import io.github.mgdx.potable.alert.SheetTarget
import io.github.mgdx.potable.alert.names
import io.github.mgdx.potable.ui.about.AboutScreen
import io.github.mgdx.potable.ui.follows.FollowsScreen
import io.github.mgdx.potable.ui.follows.FollowsViewModel
import io.github.mgdx.potable.ui.history.ParameterHistoryScreen
import io.github.mgdx.potable.ui.history.ParameterHistoryViewModel
import io.github.mgdx.potable.ui.history.historyCodes
import io.github.mgdx.potable.ui.navigation.AboutRoute
import io.github.mgdx.potable.ui.navigation.FollowsRoute
import io.github.mgdx.potable.ui.navigation.ParameterHistoryRoute
import io.github.mgdx.potable.ui.navigation.ReportResultsRoute
import io.github.mgdx.potable.ui.navigation.ReportRoute
import io.github.mgdx.potable.ui.navigation.SampleRoute
import io.github.mgdx.potable.ui.navigation.SearchRoute
import io.github.mgdx.potable.ui.navigation.SettingsRoute
import io.github.mgdx.potable.ui.navigation.SheetRoute
import io.github.mgdx.potable.ui.navigation.TimelineRoute
import io.github.mgdx.potable.ui.navigation.UdisRoute
import io.github.mgdx.potable.ui.sample.SampleDetailScreen
import io.github.mgdx.potable.ui.sample.SampleDetailViewModel
import io.github.mgdx.potable.ui.sample.SampleReportResultsScreen
import io.github.mgdx.potable.ui.sample.SampleReportResultsViewModel
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
import kotlin.reflect.KClass

/** The sheet's ViewModel, stored in the sheet's back stack entry, whoever asks for it. */
@Composable
private fun sheetViewModel(container: AppContainer, sheetEntry: NavBackStackEntry): SheetViewModel {
    val sheet = sheetEntry.toRoute<SheetRoute>()
    return viewModel(
        viewModelStoreOwner = sheetEntry,
        factory = SheetViewModel.factory(
            container = container,
            communeCode = sheet.communeCode,
            udiCode = sheet.udiCode,
            communeName = sheet.communeName,
            udiName = sheet.udiName ?: sheet.udiCode,
        ),
    )
}

/** The entry of the sheet in the back stack, or null when there is none. */
internal fun NavController.sheetEntryOrNull(): NavBackStackEntry? = try {
    getBackStackEntry<SheetRoute>()
} catch (_: IllegalArgumentException) {
    null
}

/** The entry of the topmost report in the back stack, or null when there is none. */
internal fun NavController.reportEntryOrNull(): NavBackStackEntry? = try {
    getBackStackEntry<ReportRoute>()
} catch (_: IllegalArgumentException) {
    null
}

/**
 * Leaves a sample screen ([route]) that has no sheet, or report, below it:
 * back to the screen below, or, when it is alone, to the search in its place.
 */
internal fun NavController.leaveOrphanSample(route: KClass<*> = SampleRoute::class) {
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(SearchRoute) {
            popUpTo(route) { inclusive = true }
        }
    }
}

/** The report's ViewModel, stored in the report's back stack entry, whoever asks for it. */
@Composable
private fun reportViewModel(
    container: AppContainer,
    reportEntry: NavBackStackEntry,
): SampleReportViewModel {
    val report = reportEntry.toRoute<ReportRoute>()
    return viewModel(
        viewModelStoreOwner = reportEntry,
        factory = SampleReportViewModel.factory(
            container,
            communeCode = report.communeCode,
            udiCode = report.udiCode,
            sampleCode = report.sampleCode,
        ),
    )
}

/** Goes back to the search if it is below, or opens it otherwise. */
private fun NavHostController.openSearch() {
    if (!popBackStack<SearchRoute>(inclusive = false)) navigate(SearchRoute)
}

/** Opens the sheet of [target], named [names], unless it is the one already shown. */
private fun NavHostController.openSheet(target: SheetTarget, names: SheetNames) {
    val current = currentBackStackEntry
    val shown = current != null &&
        current.destination.hasRoute<SheetRoute>() &&
        current.toRoute<SheetRoute>().let {
            it.communeCode == target.communeCode && it.udiCode == target.udiCode
        }
    if (shown) return
    navigate(SheetRoute(target.communeCode, target.udiCode, names.communeName, names.udiName))
}

/** [target]'s names from its follow, or null (not followed, or unreadable). */
private suspend fun sheetNames(container: AppContainer, target: SheetTarget): SheetNames? = try {
    target.names(container.follows)
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/** Opens "My communes", unless it is already shown. */
private fun NavHostController.openFollows() {
    if (currentBackStackEntry?.destination?.hasRoute<FollowsRoute>() == true) return
    navigate(FollowsRoute)
}

/**
 * The application's navigation. It always opens on the search, whatever is
 * followed (SPEC §5 F6).
 *
 * [alertTarget], from an alert notification (a sheet, or "My communes" for
 * the summary of several), is opened on top of the search as soon as the
 * graph is there, a sheet only for a followed unit ("My communes" otherwise);
 * [onAlertTargetOpened] is then called, once.
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
            // Only a followed unit's sheet opens; else "My communes", where a
            // unit unfollowed since its notification is visibly missing.
            is SheetTarget -> sheetNames(container, alertTarget)
                ?.let { navController.openSheet(alertTarget, it) }
                ?: navController.openFollows()
            FollowsTarget -> navController.openFollows()
        }
        onAlertTargetOpened()
    }
    NavHost(navController = navController, startDestination = SearchRoute) {
        composable<SearchRoute> {
            val searchViewModel: SearchViewModel =
                viewModel(factory = SearchViewModel.factory(container))
            SearchScreen(
                viewModel = searchViewModel,
                onCommuneSelected = { commune, choose ->
                    // Hub'Eau is queried under the code it publishes the place under.
                    // With several known units, the full choice opens (choose).
                    navController.navigate(UdisRoute(commune.queryCode, commune.name, choose))
                },
                onKnownUdiSelected = { commune, communeName, udi ->
                    // Like a remembered unit: its sheet offers to change it.
                    navController.navigate(
                        SheetRoute(
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
                        SheetRoute(
                            unit.communeCode,
                            unit.udiCode,
                            unit.communeName,
                            unit.udiName,
                            // Only a commune known to have several units
                            // offers to change it: the others had nothing
                            // to choose from.
                            change = searchViewModel.offersUnitChoice(unit.communeCode),
                        ),
                    )
                },
                onMyCommunes = { navController.navigate(FollowsRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
                onAbout = { navController.navigate(AboutRoute) },
            )
        }
        composable<AboutRoute> {
            AboutScreen(
                onBack = { navController.navigateUp() },
                onSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                viewModel = viewModel(factory = SettingsViewModel.factory(container)),
                onBack = { navController.navigateUp() },
                onSearch = { navController.openSearch() },
            )
        }
        composable<FollowsRoute> {
            FollowsScreen(
                viewModel = viewModel(factory = FollowsViewModel.factory(container)),
                onBack = { navController.navigateUp() },
                onOpen = { follow -> navController.navigate(SheetRoute.of(follow)) },
                onSearch = { navController.openSearch() },
            )
        }
        composable<UdisRoute> { entry ->
            val route = entry.toRoute<UdisRoute>()
            val communeCode = route.communeCode
            val name = route.communeName
            UdiScreen(
                viewModel = viewModel(
                    factory = UdiViewModel.factory(container, communeCode, route.choose),
                ),
                communeName = name,
                onBack = { navController.navigateUp() },
                onUdiResolved = { udi ->
                    navController.navigate(SheetRoute(communeCode, udi.code, name, udi.name)) {
                        // Back from the sheet returns to the search, not to a
                        // resolution screen that would forward again at once.
                        popUpTo<UdisRoute> { inclusive = true }
                    }
                },
                onUdiRemembered = { udi ->
                    // Same as above, but the sheet offers to change the unit.
                    // A unit just picked goes this way too: back from its
                    // sheet does not offer the choice again (SPEC §5 F2).
                    val sheet = SheetRoute(communeCode, udi.code, name, udi.name, change = true)
                    navController.navigate(sheet) {
                        popUpTo<UdisRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<SheetRoute> { entry ->
            val route = entry.toRoute<SheetRoute>()
            val communeCode = route.communeCode
            val communeName = route.communeName
            val udiCode = route.udiCode
            // The search is always below a sheet. Should a sheet ever be
            // alone (a back stack restored oddly), it shows no back arrow and
            // offers the search in its top bar instead, rather than a dead end.
            val isRoot = remember(entry) { navController.previousBackStackEntry == null }
            val fromFollows = remember(entry) {
                navController.previousBackStackEntry?.destination?.hasRoute<FollowsRoute>() == true
            }
            val onChangeUdi: (() -> Unit)? = if (route.change) {
                {
                    navController.navigate(UdisRoute(communeCode, communeName, choose = true)) {
                        // Back from the choice returns where this sheet did.
                        popUpTo<SheetRoute> { inclusive = true }
                    }
                }
            } else {
                null
            }
            SheetScreen(
                viewModel = sheetViewModel(container, entry),
                communeName = communeName,
                udiName = route.udiName,
                onBack = if (isRoot) null else ({ navController.navigateUp() }),
                onShowAllResults = { sample -> navController.navigate(SampleRoute(sample.code)) },
                onShowHistory = {
                    navController.navigate(TimelineRoute(communeCode, udiCode, communeName))
                },
                onParameterHistory = { codes, label ->
                    navController.navigate(
                        ParameterHistoryRoute.of(communeCode, udiCode, codes, label),
                    )
                },
                // Opened from My communes: going there again would stack a
                // second copy, so the bar offers the overflow menu instead.
                onMyCommunes = if (fromFollows) {
                    null
                } else {
                    { navController.navigate(FollowsRoute) }
                },
                onSettings = { navController.navigate(SettingsRoute) },
                onAbout = { navController.navigate(AboutRoute) },
                onSearch = if (isRoot) ({ navController.openSearch() }) else null,
                onChangeUdi = onChangeUdi,
            )
        }
        composable<SampleRoute> { entry ->
            // The sheet's entry is below this one, the only way here; were it
            // missing, the screen goes away instead of crashing.
            val sheetEntry = remember(entry) { navController.sheetEntryOrNull() }
            if (sheetEntry == null) {
                LaunchedEffect(entry) { navController.leaveOrphanSample() }
                return@composable
            }
            val sheetRoute = sheetEntry.toRoute<SheetRoute>()
            val sheet = sheetViewModel(container, sheetEntry)
            SampleDetailScreen(
                viewModel = viewModel(
                    factory = SampleDetailViewModel.factory(
                        entry.toRoute<SampleRoute>().sampleCode,
                        sheet.state,
                    ),
                ),
                onRetry = sheet::retry,
                onBack = { navController.navigateUp() },
                onOpenHistory = { result ->
                    navController.navigate(
                        ParameterHistoryRoute.of(
                            communeCode = sheetRoute.communeCode,
                            udiCode = sheetRoute.udiCode,
                            parameterCodes = historyCodes(result.parameterCode),
                            label = result.parameterLabel,
                        ),
                    )
                },
            )
        }
        composable<ParameterHistoryRoute> { entry ->
            val route = entry.toRoute<ParameterHistoryRoute>()
            ParameterHistoryScreen(
                // The ViewModel reads its arguments from the entry's saved state.
                viewModel = viewModel(factory = ParameterHistoryViewModel.factory(container)),
                label = route.label,
                onBack = { navController.navigateUp() },
                onOpenSample = { sampleCode ->
                    navController.navigate(
                        ReportRoute(route.communeCode, route.udiCode, sampleCode),
                    )
                },
            )
        }
        composable<ReportRoute> { entry ->
            val route = entry.toRoute<ReportRoute>()
            SampleReportScreen(
                viewModel = reportViewModel(container, entry),
                onBack = { navController.navigateUp() },
                onShowAllResults = {
                    navController.navigate(
                        ReportResultsRoute(route.communeCode, route.udiCode, route.sampleCode),
                    )
                },
                onParameterHistory = { codes, label ->
                    navController.navigate(
                        ParameterHistoryRoute.of(route.communeCode, route.udiCode, codes, label),
                    )
                },
            )
        }
        composable<ReportResultsRoute> { entry ->
            val route = entry.toRoute<ReportResultsRoute>()
            // The report's entry is right below this one, the only way here;
            // were it missing, the screen goes away instead of crashing.
            val reportEntry = remember(entry) {
                navController.reportEntryOrNull()?.takeIf {
                    it.toRoute<ReportRoute>().sampleCode == route.sampleCode
                }
            }
            if (reportEntry == null) {
                LaunchedEffect(entry) {
                    navController.leaveOrphanSample(ReportResultsRoute::class)
                }
                return@composable
            }
            val report = reportViewModel(container, reportEntry)
            SampleReportResultsScreen(
                viewModel = viewModel(factory = SampleReportResultsViewModel.factory(report.state)),
                onRetry = report::retry,
                onBack = { navController.navigateUp() },
                onOpenHistory = { result ->
                    navController.navigate(
                        ParameterHistoryRoute.of(
                            communeCode = route.communeCode,
                            udiCode = route.udiCode,
                            parameterCodes = historyCodes(result.parameterCode),
                            label = result.parameterLabel,
                        ),
                    )
                },
            )
        }
        composable<TimelineRoute> { entry ->
            val route = entry.toRoute<TimelineRoute>()
            TimelineScreen(
                // The ViewModel reads its arguments from the entry's saved state.
                viewModel = viewModel(factory = TimelineViewModel.factory(container)),
                communeName = route.communeName,
                onBack = { navController.navigateUp() },
                // Like a long press on a point of a parameter's history.
                onOpenSample = { sampleCode ->
                    navController.navigate(
                        ReportRoute(route.communeCode, route.udiCode, sampleCode),
                    )
                },
            )
        }
    }
}
