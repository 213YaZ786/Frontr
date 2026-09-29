package com.frontr.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import com.frontr.app.ui.glass.rememberGlassLook
import com.frontr.app.ui.glass.glassGround
import com.frontr.app.ui.glass.LocalGlass
import android.content.Intent
import android.graphics.Color
import android.widget.Toast
import com.frontr.app.core.link.LinkRouter
import org.koin.android.ext.android.inject
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.frontr.app.data.settings.SettingsStore
import com.frontr.app.data.settings.ThemeMode
import org.koin.compose.koinInject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.frontr.app.navigation.FrontrApp
import com.frontr.app.ui.theme.DisplayPrefs
import com.frontr.app.ui.theme.FrontrTheme

class MainActivity : ComponentActivity() {

    private val links: LinkRouter by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a real launch. A recreated activity already showed its link.
        if (savedInstanceState == null) receive(intent)
        setContent {
            val store: SettingsStore = koinInject()
            val settings by store.settings.collectAsState()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Status and navigation bar icons follow the app's theme, not only
            // the system's, so a forced light theme keeps dark icons.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                )
                onDispose { }
            }

            FrontrTheme(
                darkTheme = dark,
                pureBlack = settings.pureBlack,
                textScale = settings.textScale,
                display = DisplayPrefs(
                    compact = settings.compactPosts,
                    squareAvatars = settings.squareAvatars
                )
            ) {
                // Glass over Material You: the look for this theme, or none
                // when the reader turned it off, and the page's ground with
                // its ambient light under everything.
                val look = rememberGlassLook(MaterialTheme.colorScheme, settings.glass)
                CompositionLocalProvider(LocalGlass provides look) {
                    Box(Modifier.fillMaxSize().glassGround(look, MaterialTheme.colorScheme.background)) {
                        FrontrApp()
                    }
                }
            }
        }
    }

    /** singleTask: a link tapped elsewhere while Frontr runs arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) return
        if (links.offer(intent)) return
        val url = intent.dataString
        if (action == Intent.ACTION_VIEW && url != null) {
            // A Reddit page Frontr cannot show, a user or a share link. Hand it on
            // rather than leaving the reader stuck here.
            LinkRouter.openOutside(this, url)
        } else {
            Toast.makeText(this, "No subreddit or Reddit post in what was shared", Toast.LENGTH_SHORT).show()
        }
    }
}
