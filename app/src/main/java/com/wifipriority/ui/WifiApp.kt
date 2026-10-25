package com.wifipriority.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun WifiApp(
    viewModel: AppViewModel,
    onFix: (BannerId) -> Unit,
) {
    val start by viewModel.start.collectAsState()
    val destination = start
    if (destination == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val nav = rememberNavController()
    LaunchedEffect(nav) {
        viewModel.navigation.collect { request ->
            if (request != NavRequest.HOME) return@collect
            viewModel.consumeNavigation()
            val popped = nav.popBackStack(ROUTE_HOME, inclusive = false)
            if (!popped) {
                nav.navigate(ROUTE_HOME) {
                    popUpTo(ROUTE_SETTINGS) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }
    NavHost(navController = nav, startDestination = routeOf(destination)) {
        composable(ROUTE_HOME) {
            HomeScreen(
                viewModel = viewModel,
                onOpenSettings = { nav.navigate(ROUTE_SETTINGS) { launchSingleTop = true } },
                onFix = { banner ->
                    if (opensSettings(banner)) {
                        nav.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                    } else {
                        onFix(banner)
                    }
                },
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = {
                    if (!nav.popBackStack()) {
                        nav.navigate(ROUTE_HOME) {
                            popUpTo(ROUTE_SETTINGS) { inclusive = true }
                        }
                    }
                },
                onFix = { banner ->
                    if (!opensSettings(banner)) onFix(banner)
                },
            )
        }
    }
}

private fun routeOf(destination: StartDestination): String = when (destination) {
    StartDestination.HOME -> ROUTE_HOME
    StartDestination.SETTINGS -> ROUTE_SETTINGS
}

private fun opensSettings(banner: BannerId): Boolean = when (banner) {
    BannerId.SERVICE_DISABLED,
    BannerId.CANNOT_CONNECT_PHONE,
    BannerId.WRONG_PASSWORD,
    BannerId.PHONE_PASSPHRASE,
    BannerId.TRUCK_PASSPHRASE,
    -> true
    else -> false
}

const val ROUTE_HOME = "home"
const val ROUTE_SETTINGS = "settings"
