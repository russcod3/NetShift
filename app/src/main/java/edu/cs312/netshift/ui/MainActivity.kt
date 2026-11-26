package edu.cs312.netshift.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cs312.netshift.NetShiftApplication
import edu.cs312.netshift.ui.navigation.NetShiftNavHost
import edu.cs312.netshift.ui.theme.NetShiftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NetShiftApplication).container
        setContent {
            NetShiftTheme {
                val appViewModel: AppViewModel = viewModel(factory = AppViewModel.factory(container))
                NetShiftNavHost(appViewModel, container)
            }
        }
    }
}
