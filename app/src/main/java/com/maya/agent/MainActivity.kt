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
import com.maya.agent.ui.screens.WelcomeScreen
import com.maya.agent.ui.theme.MayaTheme
import com.maya.agent.ui.theme.MayaThemeMode

object Routes {
    const val WELCOME = "welcome"
    const val PERMISSIONS_1 = "permissions1"
    const val PERMISSIONS_2 = "permissions2"
    const val SIGNIN = "signin"
    const val HOME = "home"
    const val CHAT = "chat"
    const val CHAT_WITH_TEXT = "chat/{text}"
    const val MEMORY = "memory"
    const val VAULT = "vault"
    const val SETTINGS = "settings"
}

class MainActivity : FragmentActivity() {

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthRedirect(intent)
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(MayaThemeMode.DARK.name) }
            var pendingChatText by rememberSaveable { mutableStateOf<String?>(null) }
            val nav = rememberNavController()
            navController = nav

            MayaTheme(mode = MayaThemeMode.valueOf(themeMode)) {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val start = when {
                        !onboardingDone() -> Routes.WELCOME
                        !MayaApp.from(this).auth.isSignedIn -> Routes.SIGNIN
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
                                onSkip = { nav.navigate(Routes.HOME) { popUpTo(0) } },
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable(Routes.HOME) {
                            MainScaffold(MayaTab.HOME, nav) {
                                HomeScreen(onNavigateToChat = { text ->
                                    pendingChatText = text
                                    nav.navigate("chat/$text".replace(" ", "%20"))
                                })
                            }
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
                                    onThemeMode = { themeMode = it.name },
                                    onSignedOut = { nav.navigate(Routes.SIGNIN) { popUpTo(0) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthRedirect(intent)
    }

    private fun handleAuthRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "maya" && uri.host == "auth") {
            MayaApp.from(this).auth.handleAuthRedirect(uri)
            navController?.navigate(Routes.HOME) { popUpTo(0) }
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
