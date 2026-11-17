package com.twchat.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twchat.app.ui.screens.AuthScreen
import com.twchat.app.ui.screens.ChatScreen
import com.twchat.app.ui.screens.PairScreen
import com.twchat.app.ui.screens.SettingsScreen
import com.twchat.app.ui.theme.TwoChatTheme

@Composable
fun TwoChatApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TwoChatTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val route = when {
                !state.isLoggedIn -> "auth"
                !state.isPaired -> "pair"
                state.extras.screen == Screen.Settings -> "settings"
                else -> "chat"
            }
            Crossfade(targetState = route, animationSpec = tween(220), label = "screen") { screen ->
                when (screen) {
                    "auth" -> AuthScreen(state = state, vm = viewModel)
                    "pair" -> PairScreen(state = state, vm = viewModel)
                    "settings" -> SettingsScreen(state = state, vm = viewModel)
                    else -> ChatScreen(state = state, vm = viewModel)
                }
            }
        }
    }
}
