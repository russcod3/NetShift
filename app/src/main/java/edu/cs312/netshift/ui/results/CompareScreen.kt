package edu.cs312.netshift.ui.results

import androidx.compose.runtime.Composable
import edu.cs312.netshift.ui.components.EmptyState
import edu.cs312.netshift.ui.components.ScreenPage

@Composable
fun CompareScreen() {
    ScreenPage("Compare networks", "Wi-Fi and mobile data, measured at the same location.") {
        EmptyState(
            "No comparison yet",
            "Wi-Fi and mobile data results will be compared here after network testing and charts are available.",
        )
    }
}
