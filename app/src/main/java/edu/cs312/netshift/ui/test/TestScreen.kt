package edu.cs312.netshift.ui.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.cs312.netshift.ui.components.EmptyState
import edu.cs312.netshift.ui.components.ScreenPage

@Composable
fun TestScreen() {
    var mobileSelected by rememberSaveable { mutableStateOf(false) }
    var locationName by rememberSaveable { mutableStateOf("") }

    ScreenPage("Test your connection", "Compare Wi-Fi and mobile data at the places you use them.") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = !mobileSelected, onClick = { mobileSelected = false }, label = { Text("Wi-Fi") })
            FilterChip(selected = mobileSelected, onClick = { mobileSelected = true }, label = { Text("Mobile data") })
        }
        OutlinedTextField(
            value = locationName,
            onValueChange = { locationName = it.take(60) },
            label = { Text("Location name") },
            placeholder = { Text("Library, classroom, home…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        EmptyState(
            "Tests are coming next",
            "This initial build shows live connection status. Network tests are not available yet and no test data is downloaded.",
        )
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("Start test")
        }
    }
}
