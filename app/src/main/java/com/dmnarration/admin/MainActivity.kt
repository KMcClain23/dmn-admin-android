package com.dmnarration.admin

import androidx.activity.compose.BackHandler
import com.dmnarration.admin.ui.detail.CardDetailScreen
import com.dmnarration.admin.ui.detail.CardDetailViewModel
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import com.dmnarration.admin.ui.agenda.AgendaScreen
import com.dmnarration.admin.ui.settings.SettingsScreen
import com.dmnarration.admin.ui.settings.SettingsViewModel
import com.dmnarration.admin.ui.shelf.ArchiveScreen
import com.dmnarration.admin.ui.shelf.ReleasedScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.dmnarration.admin.ui.components.DmnNavigationBar
import com.dmnarration.admin.ui.components.NavItem
import com.dmnarration.admin.ui.money.MoneyScreen
import com.dmnarration.admin.ui.shelf.ShelfScreen
import com.dmnarration.admin.ui.shelf.ShelfViewModel
import com.dmnarration.admin.domain.Capabilities
import com.dmnarration.admin.ui.money.ExpensesScreen
import com.dmnarration.admin.ui.money.MoneyViewModel
import com.dmnarration.admin.ui.money.PaymentsScreen
import com.dmnarration.admin.ui.theme.SurfaceRaised
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
import androidx.compose.foundation.layout.imePadding
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
                            // Found on the emulator the moment Settings became
                            // editable: type a rate and the Save button sits
                            // under the keyboard, with nothing to scroll because
                            // the window never resized. `adjustResize` is in the
                            // manifest and SignInScreen had its own imePadding(),
                            // so this was the only screen with an input that had
                            // no way to get out from under the IME.
                            //
                            // At the ROOT rather than on Settings: card fields
                            // are the next stage and money records after that,
                            // and every one of them is a text input on a screen
                            // that does not have this yet.
                            .imePadding()
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

            is AuthState.SignedIn -> BoardRoute(role = s.role, userId = s.userId, onSignOut = app::signOut)
        }
    }
}

/**
 * Where the app can be. Board is its home; Today is what it asks of you.
 *
 * Released and Archive sit either side of Settings because they are both
 * histories rather than work: one is what shipped, the other is what stopped.
 */
private enum class Destination(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Default.Today),
    BOARD("Board", Icons.Default.Dashboard),
    /**
     * Released and Archive together.
     *
     * NOT "Shelf", which was the obvious name and is not true of half its
     * contents: a released book is on a shelf, an abandoned one is not. "History"
     * covers both honestly — work that shipped and work that stopped are both
     * things that are no longer in front of Dean. The tabs inside say which is
     * which, so the group label only has to be a true superset.
     */
    HISTORY("History", Icons.Default.Inventory2),
    /** Payments and Expenses. The card detail screen already calls this MONEY. */
    MONEY("Money", Icons.Default.Payments),
}

/**
 * The destinations this account actually has.
 *
 * Payments and Expenses are ABSENT for a non-admin, not disabled and not empty.
 * A disabled tab advertises a room the account may not enter; an empty one says
 * there is no money, which is a claim about Dean's finances nobody made.
 *
 * This is the first time `Capabilities` hides a whole destination rather than a
 * field — the architecture it was built for in Stage 0. It is the second layer:
 * the server refuses these reads independently, and would still refuse if this
 * list were wrong.
 */
private fun destinationsFor(capabilities: Capabilities): List<Destination> =
    Destination.entries.filter {
        when (it) {
            Destination.MONEY -> capabilities.canSeeMoney
            else -> true
        }
    }

@Composable
private fun BoardRoute(
    role: com.dmnarration.admin.domain.UserRole,
    /** Whose session this is; the pickup UI needs it to know which rows are hers. */
    userId: String?,
    onSignOut: () -> Unit,
) {
    val vm: BoardViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val shelfVm: ShelfViewModel = hiltViewModel()
    val shelf by shelfVm.state.collectAsStateWithLifecycle()
    val moneyVm: MoneyViewModel = hiltViewModel()
    val money by moneyVm.state.collectAsStateWithLifecycle()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val settingsState by settingsVm.state.collectAsStateWithLifecycle()
    // An id rather than a card: Released and Archive open the same detail sheet
    // from rows that are not BoardCards, and `card_detail()` only ever needed
    // the id. Holding a whole card here would have meant three overlays, or one
    // that could only be opened from the board.
    var selectedCardId by rememberSaveable { mutableStateOf<String?>(null) }
    var where by rememberSaveable { mutableStateOf(Destination.BOARD) }

    // Hoisted here rather than inside BoardScreen so switching to Today and back
    // returns to the same tab, scrolled where it was.
    val pagerState = rememberPagerState(pageCount = { 2 })
    // One per grouped destination, hoisted for the same reason the board's is:
    // switching away and back returns to the section you were on.
    val historyPager = rememberPagerState(pageCount = { 2 })
    val moneyPager = rememberPagerState(pageCount = { 2 })
    val pipelineScroll = rememberLazyListState()
    val productionScroll = rememberLazyListState()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(role) {
        vm.start(role)
        shelfVm.start(role)
        moneyVm.start(role)
        settingsVm.start(role)
    }
    // A changed rate re-costs every card, so the board re-reads rather than
    // keeping figures derived from a number that is no longer stored.
    LaunchedEffect(Unit) { settingsVm.onSettingSaved = vm::refresh }
    // A restored card belongs on the board again, and the board is the only
    // thing that can put it there.
    LaunchedEffect(Unit) {
        shelfVm.onRestored = vm::refresh
        vm.onBoardChanged = shelfVm::markStale
    }
    // Paid on arrival rather than on every board write: releasing or archiving a
    // card changes both shelf lists, and without this the card is missing from
    // the board AND from the screen it moved to until someone pulls to refresh.
    LaunchedEffect(where) {
        if (where == Destination.HISTORY) shelfVm.onShown()
        if (where == Destination.MONEY) moneyVm.onShown()
    }

    // A destination that stops existing must not stay selected. Reachable if a
    // role is re-resolved downward while the phone is sitting on Payments —
    // without this the tab bar would show no selection and the screen would keep
    // rendering data the account may no longer see.
    val destinations = destinationsFor(state.capabilities)
    LaunchedEffect(destinations) {
        if (where !in destinations) where = Destination.BOARD
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        Box(Modifier.weight(1f)) {
            when (where) {
                Destination.BOARD -> BoardScreen(
                    state = state,
                    onRefresh = vm::refresh,
                    onToggleFilter = vm::setDateFilter,
                    onOpenCard = { selectedCardId = it.id },
                    onToggleFirst15 = vm::toggleFirst15,
                    onMoveTo = vm::moveTo,
                    onArchive = { id, reason, notes -> vm.archive(id, reason.stored, notes) },
                    onSignOut = onSignOut,
                    pagerState = pagerState,
                    pipelineScroll = pipelineScroll,
                    productionScroll = productionScroll,
                )

                Destination.HISTORY -> ShelfScreen(
                    state = shelf,
                    pagerState = historyPager,
                    onRefresh = shelfVm::refresh,
                    onUnarchive = shelfVm::unarchive,
                    onOpenCard = { selectedCardId = it },
                )

                Destination.MONEY -> MoneyScreen(
                    state = money,
                    pagerState = moneyPager,
                    onRefresh = moneyVm::refresh,
                )

                Destination.TODAY -> AgendaScreen(
                    agenda = state.agenda,
                    refreshing = state.refreshing,
                    // The refused message belongs on both screens: the agenda is the
                    // same data, so a session that may not read the board may not
                    // read today either.
                    error = state.error,
                    onRefresh = vm::refresh,
                    onOpenCard = { selectedCardId = it.id },
                )
            }
        }

        DmnNavigationBar(
            onSettings = { settingsOpen = true },
            items = destinations.map { d ->
                NavItem(
                    label = d.label,
                    icon = d.icon,
                    selected = where == d,
                    onClick = { where = d },
                )
            },
        )
    }

    // Settings, layered over whatever is underneath rather than replacing a tab.
    // It is read-only and consulted rarely, which is exactly why it stopped being
    // worth a permanent slot in a bar that had no width left.
    if (settingsOpen) {
        BackHandler { settingsOpen = false }
        Box(Modifier.fillMaxSize().background(Background)) {
            SettingsScreen(
                state = settingsState,
                onRefresh = settingsVm::refresh,
                onSave = settingsVm::save,
                onEdited = settingsVm::clearWrite,
                onBack = { settingsOpen = false },
                onSignOut = onSignOut,
            )
        }
    }

    // Layered over the board rather than replacing it, so back returns to the same
    // tab scrolled where it was — by construction rather than by restoring anything.
    selectedCardId?.let { cardId ->
        val detailVm: CardDetailViewModel = hiltViewModel(key = cardId)
        val detailState by detailVm.state.collectAsStateWithLifecycle()
        LaunchedEffect(cardId, role) { detailVm.start(cardId, role, userId) }

        BackHandler { selectedCardId = null }

        Box(Modifier.fillMaxSize().background(Background)) {
            CardDetailScreen(
                state = detailState,
                capabilities = state.capabilities,
                onBack = { selectedCardId = null },
                onRefresh = detailVm::refresh,
                onSaveField = detailVm::save,
                onEditField = detailVm::clearWrite,
                onSetProgress = detailVm::setProgress,
                onMarkComplete = detailVm::markComplete,
                onRaisePickup = detailVm::raisePickup,
                onDeletePickup = detailVm::deletePickup,
                onSendChapter = detailVm::sendChapter,
                onResolvePickup = detailVm::resolvePickup,
                onMarkReturned = detailVm::markPickupReturned,
                onAdminDeletePickup = detailVm::adminDeletePickup,
            )
        }
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
