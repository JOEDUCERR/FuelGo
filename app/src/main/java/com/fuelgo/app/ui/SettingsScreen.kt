package com.fuelgo.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fuelgo.app.domain.model.FuelType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppState,
    onFuelType: (FuelType) -> Unit,
    onRefreshMinutes: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                actions = {
                    TextButton(onClick = onBack) { Text("Done") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text("Fuel type", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FuelType.entries.forEach { fuelType ->
                    FilterChip(
                        selected = state.fuelType == fuelType,
                        onClick = { onFuelType(fuelType) },
                        label = { Text(fuelType.displayName) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Widget refresh interval",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { minutes ->
                    FilterChip(
                        selected = state.widgetRefreshMinutes == minutes,
                        onClick = { onRefreshMinutes(minutes) },
                        label = { Text("$minutes min") },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Open/closed status comes from published hours plus " +
                    "reports from FuelGo users. Reports older than an hour expire.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }
}
