package com.maya.agent.render

import androidx.activity.ComponentActivity
import androidx.compose.material3.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.maya.agent.permissions.PermissionCatalog
import com.maya.agent.ui.screens.PermissionsScreen
import com.maya.agent.ui.theme.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-night-xxhdpi")
class PermReach {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun run(scale: Float, page: Int) {
        RuntimeEnvironment.setFontScale(scale)
        var next = 0; var skip = 0
        rule.setContent { MayaTheme(MayaThemeMode.DARK) { Surface { PermissionsScreen(page, { next++ }, { skip++ }) } } }
        val items = if (page == 1) PermissionCatalog.page1 else PermissionCatalog.page2
        rule.onNodeWithText("Continue").assertIsDisplayed().performClick()
        rule.onNodeWithText("Not now").assertIsDisplayed().performClick()
        check(next == 1 && skip == 1)
        for (it in items) {
            rule.onNodeWithText(it.title).performScrollTo().assertIsDisplayed()
        }
        val allows = rule.onAllNodesWithText("Allow")
        val n = allows.fetchSemanticsNodes().size
        check(n == items.size) { "Allow buttons $n vs ${items.size}" }
        for (i in 0 until n) allows[i].performScrollTo().assertIsDisplayed()
        val skips = rule.onAllNodesWithText("Skip")
        for (i in 0 until skips.fetchSemanticsNodes().size) skips[i].performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Continue").assertIsDisplayed()
    }
    @Test fun p1_1x() = run(1.0f, 1)
    @Test fun p1_13() = run(1.3f, 1)
    @Test fun p1_2x() = run(2.0f, 1)
    @Test fun p2_2x() = run(2.0f, 2)
}
