package com.dmnarration.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmnarration.admin.ui.AuthState
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.ui.AppViewModel
import com.dmnarration.admin.ui.auth.SignInScreen
import com.dmnarration.admin.ui.board.BoardScreen
import com.dmnarration.admin.ui.board.BoardViewModel
import com.dmnarration.admin.ui.board.CardDetailSheet
import com.dmnarration.admin.ui.theme.Background
import com.dmnarration.admin.ui.theme.DmnAdminTheme
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DmnAdminTheme {
                Surface(color = Background, modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        Modifier
                            .windowInsetsPadding(WindowInsets.systemBars)
                            .consumeWindowInsets(WindowInsets.systemBars)
                    )
                }
            }
        }
    }
}

/**
 * The launch decision, in one place: a valid session AND a known role gets the
 * board; anything else gets sign-in or an explanation. There is deliberately no
 * fourth branch that lets an unresolved role through.
 */
@Composable
private fun AppRoot(modifier: Modifier = Modifier) {
    val app: AppViewModel = hiltViewModel()
    val auth by app.state.collectAsStateWithLifecycle()
    val signingIn by app.signingIn.collectAsStateWithLifecycle()
    val signInError by app.signInError.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize()) {
        when (val s = auth) {
            // Labelled even when it is fast. A spinner that says nothing is
            // indistinguishable from a hang, and this is the first thing the
            // app does — the one moment a user has no other evidence that it
            // is working.
            is AuthState.Loading -> Centered {
                CircularProgressIndicator(color = DmnTheme.colors.accentAmber)
                Text(
                    "Restoring session…",
                    style = DmnType.Small,
                    color = DmnTheme.colors.textMuted,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            is AuthState.SignedOut -> SignInScreen(
                signingIn = signingIn,
                error = signInError,
                onSignIn = app::signIn,
            )

            // The account's permissions cannot be established, so no data is
            // shown. The session is left alone — signing out is offered as a
            // choice, not taken on the user's behalf.
            is AuthState.RoleUnavailable -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        s.reason,
                        style = DmnType.Body,
                        color = DmnTheme.colors.alertRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    TextButton(onClick = app::retry) {
                        Text("Try again", color = DmnTheme.colors.accentAmber)
                    }
                    TextButton(onClick = app::signOut) {
                        Text("Sign out", color = DmnTheme.colors.textMuted)
                    }
                }
            }

            // Session intact, just unconfirmable. Nobody is signed out for being
            // offline.
            is AuthState.Unreachable -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        s.reason,
                        style = DmnType.Body,
                        color = DmnTheme.colors.alertRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Text(
                        "You are still signed in.",
                        style = DmnType.Small,
                        color = DmnTheme.colors.textMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = app::retry) {
                        Text("Try again", color = DmnTheme.colors.accentAmber)
                    }
                    TextButton(onClick = app::signOut) {
                        Text("Sign out", color = DmnTheme.colors.textMuted)
                    }
                }
            }

            is AuthState.SignedIn -> BoardRoute(role = s.role, onSignOut = app::signOut)
        }
    }
}

@Composable
private fun BoardRoute(
    role: com.dmnarration.admin.domain.UserRole,
    onSignOut: () -> Unit,
) {
    val vm: BoardViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<BoardCard?>(null) }

    LaunchedEffect(role) { vm.start(role) }

    BoardScreen(
        state = state,
        onRefresh = vm::refresh,
        onToggleFilter = vm::setDateFilter,
        onOpenCard = { selected = it },
        onToggleFirst15 = vm::toggleFirst15,
        onSignOut = onSignOut,
    )

    selected?.let { card ->
        CardDetailSheet(
            card = card,
            capabilities = state.capabilities,
            settings = state.settings,
            today = state.today,
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { content() }
    }
}
