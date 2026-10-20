package com.fuelgo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelgo.app.di.AppContainer
import com.fuelgo.app.ui.AppViewModel
import com.fuelgo.app.ui.HomeScreen
import com.fuelgo.app.ui.OnboardingScreen
import com.fuelgo.app.ui.SettingsScreen
import com.fuelgo.app.ui.theme.FuelGoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FuelGoApp).container
        setContent {
            FuelGoTheme {
                val vm: AppViewModel = viewModel(factory = AppViewModel.factory(container))
                AppRoot(vm)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    when {
        !state.ready -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        !state.onboardingDone -> OnboardingScreen(onSelect = vm::completeOnboarding)

        state.showSettings -> SettingsScreen(
            state = state,
            onFuelType = vm::setFuelType,
            onRefreshMinutes = vm::setWidgetRefreshMinutes,
            onBack = { vm.setShowSettings(false) },
        )

        else -> HomeScreen(state, vm)
    }
}
