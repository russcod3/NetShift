package edu.cs312.netshift.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import edu.cs312.netshift.AppContainer
import edu.cs312.netshift.R
import edu.cs312.netshift.ui.AppViewModel
import edu.cs312.netshift.ui.components.*
import edu.cs312.netshift.ui.live.LiveScreen
import edu.cs312.netshift.ui.test.*
import edu.cs312.netshift.ui.history.HistoryScreen
import edu.cs312.netshift.ui.handoff.HandoffLogScreen
import edu.cs312.netshift.ui.results.*

private val destinations = listOf(
    NavItem("live", "Live", R.drawable.ic_live),
    NavItem("test", "Test", R.drawable.ic_test),
    NavItem("history", "History", R.drawable.ic_history),
    NavItem("handoffs", "Handoffs", R.drawable.ic_handoff),
)
@Composable
fun NetShiftNavHost(viewModel: AppViewModel, container: AppContainer,
    dark: Boolean = true, themeEnabled: Boolean = true, onThemeToggle: () -> Unit = {}) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val network by viewModel.network.collectAsStateWithLifecycle()
    val tests by viewModel.tests.collectAsStateWithLifecycle()
    val handoffs by viewModel.handoffs.collectAsStateWithLifecycle()
    val comparisons by viewModel.comparisons.collectAsStateWithLifecycle()
    val selected = if (route == "compare" || route == "results/{id}") 2
        else destinations.indexOfFirst { it.route == route }.coerceAtLeast(0)
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ShiftyMascot(Modifier.size(54.dp), network.transport)
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text("NetShift", style = MaterialTheme.typography.titleLarge)
                    Text("Know your connection", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ThemeSwitch(dark, themeEnabled, onThemeToggle)
            }
        },
        bottomBar = {
            GooeyNavigation(destinations, selected) { destination ->
                navController.navigate(destination.route) {
                    popUpTo("live") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = "live", modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(330)) + slideInHorizontally(tween(430)) { it/12 } },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(330)) },
            popExitTransition = { fadeOut(tween(180)) }) {
            composable("live") { LiveScreen(network) }
            composable("test") {
                val vm: TestViewModel = viewModel(factory = TestViewModel.factory(container, viewModel.network))
                TestScreen(vm)
            }
            composable("history") {
                HistoryScreen(tests, comparisons.size,
                    onResult = { navController.navigate("results/$it") },
                    onCompare = { navController.navigate("compare") },
                    onTest = { navController.navigate("test") })
            }
            composable("handoffs") { HandoffLogScreen(handoffs) }
            composable("compare") { CompareScreen(comparisons, onTest = { navController.navigate("test") }) }
            composable("results/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { resultEntry ->
                val id = requireNotNull(resultEntry.arguments?.getString("id"))
                val flow = remember(id) { viewModel.observeTest(id) }
                val result by flow.collectAsStateWithLifecycle(initialValue = null)
                ResultsScreen(result, onCompare = { navController.navigate("compare") })
            }
        }
    }
}
