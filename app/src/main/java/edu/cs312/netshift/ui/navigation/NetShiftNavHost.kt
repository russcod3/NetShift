package edu.cs312.netshift.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import edu.cs312.netshift.R
import edu.cs312.netshift.ui.AppViewModel
import edu.cs312.netshift.ui.handoff.HandoffLogScreen
import edu.cs312.netshift.ui.history.HistoryScreen
import edu.cs312.netshift.ui.live.LiveScreen
import edu.cs312.netshift.ui.results.CompareScreen
import edu.cs312.netshift.ui.results.ResultsScreen
import edu.cs312.netshift.ui.test.TestScreen
import edu.cs312.netshift.ui.test.TestViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cs312.netshift.AppContainer

private data class Destination(val route: String, val label: String, val icon: Int)
private val destinations = listOf(
    Destination("live", "Live", R.drawable.ic_live),
    Destination("test", "Test", R.drawable.ic_test),
    Destination("history", "History", R.drawable.ic_history),
    Destination("handoffs", "Handoffs", R.drawable.ic_handoff),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetShiftNavHost(viewModel: AppViewModel, container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val network by viewModel.network.collectAsStateWithLifecycle()
    val tests by viewModel.tests.collectAsStateWithLifecycle()
    val handoffs by viewModel.handoffs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("NetShift") }) },
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    val selected = route == destination.route ||
                        (destination.route == "history" && (route == "results/{id}" || route == "compare"))
                    NavigationBarItem(
                        selected = selected,
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(destination.label) },
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo("live") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = "live", modifier = Modifier.padding(padding)) {
            composable("live") { LiveScreen(network) }
            composable("test") {
                val testViewModel: TestViewModel = viewModel(factory = TestViewModel.factory(container, viewModel.network))
                TestScreen(testViewModel)
            }
            composable("history") {
                HistoryScreen(
                    tests,
                    onResult = { navController.navigate("results/$it") },
                    onCompare = { navController.navigate("compare") },
                )
            }
            composable("handoffs") { HandoffLogScreen(handoffs) }
            composable("compare") { CompareScreen() }
            composable("results/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = requireNotNull(entry.arguments?.getString("id"))
                val resultFlow = remember(id) { viewModel.observeTest(id) }
                val result by resultFlow.collectAsStateWithLifecycle(initialValue = null)
                ResultsScreen(result, onCompare = { navController.navigate("compare") })
            }
        }
    }
}
