package com.maya.agent

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.maya.agent.ui.components.MayaBottomNav
import com.maya.agent.ui.components.MayaTab
import com.maya.agent.ui.screens.ChatScreen
import com.maya.agent.ui.screens.HomeScreen
import com.maya.agent.ui.screens.MemoryScreen
import com.maya.agent.ui.screens.PermissionsScreen
import com.maya.agent.ui.screens.SettingsScreen
import com.maya.agent.ui.screens.SignInScreen
import com.maya.agent.ui.screens.VaultScreen
import com.maya.agent.ui.screens.VoiceConversationScreen
import com.maya.agent.ui.screens.WelcomeScreen
import com.maya.agent.ui.theme.MayaTheme
import com.maya.agent.ui.theme.MayaThemeMode

object Routes {
    const val WELCOME = "welcome"
    const val PERMISSIONS_1 = "permissions1"
    const val PERMISSIONS_2 = "permissions2"
    const val SIGNIN = "signin"
    const val RESET_PASSWORD = "reset_password"
    const val HOME = "home"
    const val CHAT = "chat"
    const val CHAT_WITH_TEXT = "chat/{text}"
    const val VOICE = "voice"
    const val MEMORY = "memory"
    const val VAULT = "vault"
    const val SETTINGS = "settings"
}

class MainActivity : FragmentActivity() {

    companion object {
        /** Fired when the wake word summons Maya from the background. */
        const val ACTION_WAKE = "com.maya.agent.action.WAKE"
        /** Fired when a voice cart request needs the confirm dialog. */
        const val ACTION_SHOW_CART = "com.maya.agent.action.SHOW_CART"
    }

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // DEBUG-only render hook for the credential-free emulator smoke:
        // treats the session as onboarded so page renders can run. Release
        // builds ignore this extra; there is no guest/no-account mode.
        if (SmokeDemoGate.allowed(applicationInfo, intent)) {
            getSharedPreferences("maya_state", MODE_PRIVATE).edit()
                .putBoolean("onboarding_done", true)
                .putBoolean(SmokeDemoGate.PREF, true).apply()
        }
        handleServiceIntent(intent)
        handleAuthRedirect(intent)
        setContent {
            // Default theme is LIGHT (owner's 2026-10-24 choice). A manual
            // Settings choice persists; only a fresh/no-choice install sees Light.
            var themeMode by rememberSaveable {
                mutableStateOf(
                    getSharedPreferences("maya_state", MODE_PRIVATE)
                        .getString("theme_mode", null) ?: MayaThemeMode.LIGHT.name
                )
            }
            var pendingChatText by rememberSaveable { mutableStateOf<String?>(null) }
            val nav = rememberNavController()
            navController = nav

            MayaTheme(mode = MayaThemeMode.valueOf(themeMode)) {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val smokeDemo = SmokeDemoGate.allowed(applicationInfo, null) ||
                        ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
                            getSharedPreferences("maya_state", MODE_PRIVATE).getBoolean(SmokeDemoGate.PREF, false))
                    val start = when {
                        !onboardingDone() -> Routes.WELCOME
                        !MayaApp.from(this).auth.isSignedIn && !smokeDemo -> Routes.SIGNIN
                        else -> Routes.HOME
                    }
                    NavHost(navController = nav, startDestination = start) {
                        composable(Routes.WELCOME) {
                            WelcomeScreen(onStart = { nav.navigate(Routes.PERMISSIONS_1) })
                        }
                        composable(Routes.PERMISSIONS_1) {
                            PermissionsScreen(
                                page = 1,
                                onNext = { nav.navigate(Routes.PERMISSIONS_2) },
                                onSkipAll = { finishOnboarding(nav) },
                            )
                        }
                        composable(Routes.PERMISSIONS_2) {
                            PermissionsScreen(
                                page = 2,
                                onNext = { finishOnboarding(nav) },
                                onSkipAll = { finishOnboarding(nav) },
                            )
                        }
                        composable(Routes.SIGNIN) {
                            SignInScreen(
                                onSignedIn = { nav.navigate(Routes.HOME) { popUpTo(0) } },
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable(Routes.RESET_PASSWORD) {
                            com.maya.agent.ui.screens.ResetPasswordScreen(
                                onDone = { nav.navigate(Routes.SIGNIN) { popUpTo(0) } },
                                onCancel = { nav.navigate(Routes.SIGNIN) { popUpTo(0) } },
                            )
                        }
                        composable(Routes.HOME) {
                            MainScaffold(MayaTab.HOME, nav) {
                                HomeScreen(
                                    onNavigateToChat = { text ->
                                        pendingChatText = text
                                        nav.navigate("chat/$text".replace(" ", "%20"))
                                    },
                                    onNavigateToVoice = { nav.navigate(Routes.VOICE) },
                                )
                            }
                        }
                        composable(Routes.VOICE) {
                            // Approved 2026-10-24: full-screen Free talk, no scaffold/bottom nav.
                            VoiceConversationScreen(onEnd = { nav.popBackStack() })
                        }
                        composable(Routes.CHAT) {
                            MainScaffold(MayaTab.CHAT, nav) { ChatScreen(initialText = null) }
                        }
                        composable(Routes.CHAT_WITH_TEXT) { backStack ->
                            MainScaffold(MayaTab.CHAT, nav) {
                                ChatScreen(
                                    initialText = backStack.arguments?.getString("text")
                                        ?.replace("%20", " ")
                                )
                            }
                        }
                        composable(Routes.MEMORY) {
                            MainScaffold(MayaTab.MEMORY, nav) { MemoryScreen() }
                        }
                        composable(Routes.VAULT) {
                            MainScaffold(MayaTab.VAULT, nav) { VaultScreen() }
                        }
                        composable(Routes.SETTINGS) {
                            MainScaffold(MayaTab.SETTINGS, nav) {
                                SettingsScreen(
                                    themeMode = MayaThemeMode.valueOf(themeMode),
                                    onThemeMode = { mode ->
                                        themeMode = mode.name
                                        getSharedPreferences("maya_state", MODE_PRIVATE).edit()
                                            .putString("theme_mode", mode.name).apply()
                                    },
                                    onSignedOut = { nav.navigate(Routes.SIGNIN) { popUpTo(0) } },
                                )
                            }
                        }
                    }

                    // Cart-confirm gate: voice cart requests wait here for an
                    // explicit yes. No payment in v3 - confirm only records.
                    com.maya.agent.data.CartStore.pending.value?.let { draft ->
                        com.maya.agent.ui.screens.CartConfirmDialog(
                            title = "Cart confirm karo",
                            lines = listOf(com.maya.agent.ui.screens.CartLine(draft.item, "Price store pe")),
                            total = "Checkout pe final hoga",
                            warning = "Maya payment nahi karti - ye sirf order note hai.",
                            onConfirm = {
                                com.maya.agent.data.CartStore.confirm()
                                com.maya.agent.data.ChatStore.addMaya("${'$'}{draft.item} ka order note save kar liya")
                            },
                            onCancel = { com.maya.agent.data.CartStore.dismiss() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleServiceIntent(intent)
        handleAuthRedirect(intent)
    }

    private fun handleServiceIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_WAKE -> {
                // Summoned by the wake word while locked/screen off.
                if (android.os.Build.VERSION.SDK_INT >= 27) {
                    setShowWhenLocked(true)
                    setTurnScreenOn(true)
                } else {
                    @Suppress("DEPRECATION")
                    window.addFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                    )
                }
                if (MayaApp.from(this).auth.isSignedIn) {
                    navController?.navigate(Routes.HOME) { popUpTo(0) }
                }
            }
            ACTION_SHOW_CART -> {
                // CartStore.pending drives the dialog; nothing else needed here.
            }
        }
    }

    private fun handleAuthRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "maya" && uri.host == "auth") {
            when (MayaApp.from(this).auth.handleAuthRedirect(uri)) {
                com.maya.agent.data.auth.AuthRepository.Redirect.SIGNED_IN -> navController?.navigate(Routes.HOME) { popUpTo(0) }
                com.maya.agent.data.auth.AuthRepository.Redirect.RECOVERY -> navController?.navigate(Routes.RESET_PASSWORD) { popUpTo(0) }
                com.maya.agent.data.auth.AuthRepository.Redirect.LINK_ERROR -> navController?.navigate(Routes.SIGNIN) { popUpTo(0) }
                com.maya.agent.data.auth.AuthRepository.Redirect.IGNORED -> {}
            }
        }
    }

    private fun onboardingDone(): Boolean =
        getSharedPreferences("maya_state", MODE_PRIVATE).getBoolean("onboarding_done", false)

    private fun finishOnboarding(nav: NavHostController) {
        getSharedPreferences("maya_state", MODE_PRIVATE).edit()
            .putBoolean("onboarding_done", true).apply()
        if (MayaApp.from(this).auth.isSignedIn) {
            nav.navigate(Routes.HOME) { popUpTo(0) }
        } else {
            nav.navigate(Routes.SIGNIN) { popUpTo(0) }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MainScaffold(
    current: MayaTab,
    nav: NavHostController,
    content: @androidx.compose.runtime.Composable () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            MayaBottomNav(current = current, onSelect = { tab ->
                val route = when (tab) {
                    MayaTab.HOME -> Routes.HOME
                    MayaTab.CHAT -> Routes.CHAT
                    MayaTab.MEMORY -> Routes.MEMORY
                    MayaTab.VAULT -> Routes.VAULT
                    MayaTab.SETTINGS -> Routes.SETTINGS
                }
                nav.navigate(route) {
                    popUpTo(Routes.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            })
        },
    ) { padding ->
        Column(androidx.compose.ui.Modifier.padding(padding)) { content() }
    }
}
