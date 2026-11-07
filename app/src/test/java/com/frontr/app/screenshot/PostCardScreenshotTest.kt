package com.frontr.app.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.core.model.MediaItem
import com.frontr.app.core.model.MediaType
import com.frontr.app.core.model.Post
import com.frontr.app.core.model.PostKind
import com.frontr.app.core.model.PostStats
import com.frontr.app.di.appModule
import com.frontr.app.ui.component.PostCard
import com.frontr.app.ui.component.ScreenBanner
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home's banner and post cards as drawn on a phone and a tablet, by day and by night, with fixed posts: a sub's post
 * with its title, body and picture that arrived since the last visit (the dot at the end of its name line), a text
 * post, and a comment. Ported from LinkedOut's PostCardScreenshotTest (its local checkout, app/src/test/java/com/
 * linkedout/app/screenshot/PostCardScreenshotTest.kt, commit 6f8bbce and after), itself written as Language's
 * https://github.com/213YaZ786/Language/blob/main/app/src/test/java/com/yaz/language/screenshot/WelcomeScreenshotTest.kt#WelcomeScreenshotTest
 * (Koin started with the app's module, since a card asks for the post marks; fixed data handed to the screen, the
 * capture itself ScreenshotBase's).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class PostCardScreenshotTest : ScreenshotBase() {

    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    // The card writes the age against the clock, so the posts are dated from it: the words stay "2h" and "1d".
    private val now = System.currentTimeMillis()

    // The parser's text for a post: the title, a blank line, the body.
    private val unread = Post(
        id = "t3_1abcde",
        authorHandle = "android",
        authorName = "r/Android",
        relatedHandle = "pixelfan",
        text = "Android 17 beta 2 is out\n\nThe second beta brings the new lock screen clock and fixes the battery drain " +
            "some of you reported on the first one.",
        publishedAtMillis = now - 2 * 60 * 60 * 1000,
        permalink = "https://www.reddit.com/r/Android/comments/1abcde/android_17_beta_2_is_out/",
        media = listOf(MediaItem("https://preview.redd.it/example.jpg", "https://i.redd.it/example.jpg", MediaType.PHOTO)),
        stats = PostStats(replies = 128, likes = 9_300)
    )

    private val textPost = Post(
        id = "t3_1fghij",
        authorHandle = "grapheneos",
        authorName = "r/GrapheneOS",
        relatedHandle = "strcat",
        text = "Release notes for the latest build",
        publishedAtMillis = now - 26 * 60 * 60 * 1000,
        permalink = "https://www.reddit.com/r/GrapheneOS/comments/1fghij/release_notes_for_the_latest_build/",
        stats = PostStats(replies = 41, likes = 2_100)
    )

    // A comment has no title, no sub: its author is the name, only the time sits under it.
    private val comment = Post(
        id = "t1_kl1mno",
        authorHandle = "pixelfan",
        authorName = "pixelfan",
        text = "Same here, the clock is the first thing I noticed.",
        publishedAtMillis = now - 3 * 60 * 60 * 1000,
        permalink = "https://www.reddit.com/r/Android/comments/1abcde/android_17_beta_2_is_out/kl1mno/",
        kind = PostKind.REPLY,
        stats = PostStats(likes = 57)
    )

    @Test
    fun cards() = capture("home_cards") {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenBanner(title = "Home", subtitle = "All subreddits")
            Spacer(Modifier.height(8.dp))
            PostCard(post = unread, onClick = {}, onOpenLink = {}, onDownload = {}, unread = true)
            PostCard(post = textPost, onClick = {}, onOpenLink = {}, onDownload = {})
            PostCard(post = comment, onClick = {}, onOpenLink = {}, onDownload = {})
        }
    }
}
