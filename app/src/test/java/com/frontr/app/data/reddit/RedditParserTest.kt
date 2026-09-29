package com.frontr.app.data.reddit

import com.frontr.app.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shapes of the pages Reddit served a logged out phone in September 2026,
 * reduced to the elements and attributes read, with neutral content.
 */
class RedditParserTest {

    @Test
    fun `reads the posts of a sub page`() {
        val feed = RedditParser.feed(LISTING, "examples", nowMillis = 1_000)
        assertEquals("examples", feed.handle)
        assertEquals("r/Examples", feed.displayName)
        assertEquals(listOf("t3_aaa111", "t3_bbb222", "t3_ccc333", "t3_ddd444"), feed.posts.map { it.id })
        val link = feed.posts[0]
        assertEquals("A title with an & and a quote's mark", link.text)
        assertEquals("someone", link.relatedHandle)
        assertEquals("examples", link.authorHandle)
        assertEquals("https://www.reddit.com/r/Examples/comments/aaa111/a_title/", link.permalink)
        assertEquals(327, link.stats?.likes)
        assertEquals(65, link.stats?.replies)
        assertEquals(1790626533123L, link.publishedAtMillis)
    }

    @Test
    fun `a link post is a card, an image a picture, a video its largest file`() {
        val posts = RedditParser.posts(LISTING)
        val card = posts[0].card!!
        assertEquals("example.org", card.destination)
        assertEquals("https://example.org/article", card.url)
        assertTrue(posts[0].media.isEmpty())

        val image = posts[1].media.single()
        assertEquals(MediaType.PHOTO, image.type)
        assertEquals("https://i.redd.it/picture.jpeg", image.downloadUrl)

        val video = posts[2].media.single()
        assertEquals(MediaType.VIDEO, video.type)
        assertEquals("https://packaged-media.redd.it/v1/pb/m2-res_720p.mp4?e=1", video.downloadUrl)
        assertEquals("https://external-preview.redd.it/poster.jpg", video.previewUrl)
    }

    @Test
    fun `a text post shows its title in the list`() {
        assertEquals("Only a title here", RedditParser.posts(LISTING)[3].text)
    }

    @Test
    fun `a sub page gives its icon, banner and description, and its posts wear the icon`() {
        val page = SUB_HEADER.replace("BANNER_STYLE", "--small-banner:url(https://styles.redditmedia.com/t5_1/styles/banner_s.png);" +
            " --large-banner:url(&quot;https://styles.redditmedia.com/t5_1/styles/banner_l.png&quot;)") + LISTING
        val feed = RedditParser.feed(page, "examples", nowMillis = 1_000)
        assertEquals("https://styles.redditmedia.com/t5_1/styles/communityIcon_x.png?width=128&frame=1", feed.avatarUrl)
        assertEquals("https://styles.redditmedia.com/t5_1/styles/banner_l.png", feed.bannerUrl)
        assertEquals("Examples & more, for everyone.", feed.bio)
        assertTrue(feed.posts.all { it.avatarUrl == feed.avatarUrl })
    }

    @Test
    fun `a sub without a banner has none, and the header picture gives the icon`() {
        val page = SUB_HEADER.replace("BANNER_STYLE", "").replace(Regex("<reddit-page-data[^>]*></reddit-page-data>"), "")
        assertNull(RedditParser.subBanner(page))
        assertEquals("https://b.thumbs.redditmedia.com/header.png", RedditParser.subIcon(page))
        assertNull(RedditParser.subIcon(LISTING))
        // A post's sub icon in a listing of many subs is not the page's.
        val listing = page.replace("community-icon-t5_1", "community-icon-t5_9")
        assertNull(RedditParser.subIcon(listing))
        assertNull(RedditParser.feed(LISTING, "examples", nowMillis = 1_000).posts.first().avatarUrl)
    }

    @Test
    fun `a banner named only in the page data is found, never another sub's`() {
        val page = SUB_HEADER.replace("BANNER_STYLE", "") +
            "<x data=\"https://styles.redditmedia.com/t5_2/styles/bannerBackgroundImage_other.png\"></x>" +
            "<x data=\"{&quot;banner&quot;:&quot;https://styles.redditmedia.com/t5_1/styles/bannerBackgroundImage_mine.png?width=4000&amp;s=1&quot;}\"></x>"
        assertEquals("https://styles.redditmedia.com/t5_1/styles/bannerBackgroundImage_mine.png", RedditParser.subBanner(page))
    }

    @Test
    fun `the next page is the more posts partial, not the side column`() {
        assertEquals(
            "/svc/shreddit/community-more-posts/best/?after=dDNf&t=DAY&name=Examples",
            RedditParser.nextPage(LISTING)
        )
        assertNull(RedditParser.nextPage("<faceplate-partial src=\"/svc/shreddit/feeds/subreddit-right-rail?name=Examples\"></faceplate-partial>"))
    }

    @Test
    fun `reads a post page with its text and comment chains`() {
        val conversation = RedditParser.conversation(POST_PAGE)!!
        val main = conversation.main!!
        assertEquals("A question\n\nFirst paragraph with a link.\n\nSecond paragraph.", main.text)
        assertEquals(
            listOf(listOf("t1_c1", "t1_c2"), listOf("t1_c4")),
            conversation.replies.map { chain -> chain.map { it.id } }
        )
        val first = conversation.replies[0][0]
        assertEquals("u/alpha", first.authorName)
        assertEquals("A first answer & more", first.text)
        assertEquals(listOf("https://example.org/source"), first.links)
        assertEquals(60, first.stats?.likes)
    }

    @Test
    fun `a page without a post is no conversation`() {
        assertNull(RedditParser.conversation("<html><body>blocked</body></html>"))
        assertNotNull(RedditParser.conversation(POST_PAGE))
    }

    @Test
    fun `a page without posts is described by its title and first words`() {
        val page = "<html><head><title>Check &amp; wait</title><script>var x = 1;</script></head>" +
            "<body><div>Please  wait</div><p>while we check</p></body></html>"
        assertEquals(
            "title \"Check & wait\", 0 post elements, 0 Reddit elements, text \"Check & wait Please wait while we check\"",
            RedditParser.describe(page)
        )
    }

    @Test
    fun `tells Reddit's browser check page from a sub page`() {
        val check = "<title>Reddit</title><form hidden method=\"GET\" action=\"/r/examples/\">" +
            "<input type=\"hidden\" name=\"solution\" /><input type=\"hidden\" name=\"js_challenge\" value=\"1\"/></form>"
        assertTrue(RedditParser.isCheckPage(check))
        assertTrue(!RedditParser.isCheckPage(LISTING))
    }

    @Test
    fun `reads a post whose attributes hold a raw greater than sign`() {
        val page = "<shreddit-post class=\"[&>*]:block\" is-embeddable=\"\" id=\"t3_fff666\" post-title=\"a > b\" " +
            "subreddit-name=\"Examples\" post-type=\"text\" permalink=\"/r/Examples/comments/fff666/a/\"></shreddit-post>"
        val post = RedditParser.posts(page).single()
        assertEquals("t3_fff666", post.id)
        assertEquals("a > b", post.text)
    }

    private companion object {
        val LISTING = """
            <shreddit-subreddit-header name="Examples" prefixed-name="r/Examples"></shreddit-subreddit-header>
            <shreddit-post permalink="/r/Examples/comments/aaa111/a_title/" content-href="https://example.org/article"
              comment-count="65" created-timestamp="2026-09-28T20:15:33.123000+0000" domain="example.org" id="t3_aaa111"
              post-title="A title with an &amp; and a quote&#39;s mark" post-type="link" score="327"
              subreddit-prefixed-name="r/Examples" author="someone" subreddit-name="Examples">
            </shreddit-post>
            <shreddit-post permalink="/r/Examples/comments/bbb222/a_picture/" content-href="https://i.redd.it/picture.jpeg"
              comment-count="3" created-timestamp="2026-09-28T13:45:00.198000+0000" domain="i.redd.it" id="t3_bbb222"
              post-title="A picture" post-type="image" score="11" subreddit-prefixed-name="r/Examples"
              author="other" subreddit-name="Examples">
            </shreddit-post>
            <shreddit-post permalink="/r/Examples/comments/ccc333/a_video/" content-href="https://v.redd.it/v1"
              comment-count="0" created-timestamp="2026-09-28T12:00:00.000000+0000" domain="v.redd.it" id="t3_ccc333"
              post-title="A video" post-type="video" score="4" subreddit-prefixed-name="r/Examples"
              author="third" subreddit-name="Examples">
              <shreddit-player src="https://v.redd.it/v1/HLSPlaylist.m3u8?f=hd&amp;v=1" post-id="t3_ccc333"
                poster="https://external-preview.redd.it/poster.jpg"
                packaged-media-json="{&quot;playbackMp4s&quot;:{&quot;permutations&quot;:[{&quot;source&quot;:{&quot;dimensions&quot;:{&quot;height&quot;:220},&quot;url&quot;:&quot;https://packaged-media.redd.it/v1/pb/m2-res_220p.mp4?e=1&quot;}},{&quot;source&quot;:{&quot;dimensions&quot;:{&quot;height&quot;:720},&quot;url&quot;:&quot;https://packaged-media.redd.it/v1/pb/m2-res_720p.mp4?e=1&quot;}}]}}"></shreddit-player>
            </shreddit-post>
            <shreddit-post permalink="/r/Examples/comments/ddd444/only_a_title/" comment-count="9"
              created-timestamp="2026-09-27T08:00:00.000000+0000" id="t3_ddd444" post-title="Only a title here"
              post-type="text" score="2" subreddit-prefixed-name="r/Examples" author="fourth" subreddit-name="Examples">
            </shreddit-post>
            <faceplate-partial src="/svc/shreddit/feeds/subreddit-right-rail?name=Examples&amp;refresh=1"></faceplate-partial>
            <faceplate-partial src="/svc/shreddit/community-more-posts/best/?after=dDNf&amp;t=DAY&amp;name=Examples"></faceplate-partial>
        """.trimIndent()

        val POST_PAGE = """
            <shreddit-post permalink="/r/Examples/comments/eee555/a_question/" comment-count="3"
              created-timestamp="2026-09-28T09:00:00.000000+0000" id="t3_eee555" post-title="A question" post-type="text"
              score="12" subreddit-prefixed-name="r/Examples" author="asker" subreddit-name="Examples">
              <div slot="text-body"><div id="t3_eee555-post-rtjson-content" class="md">
                <p>First paragraph with <a href="https://example.org">a link</a>.</p>
                <p>Second paragraph.</p>
              </div></div>
            </shreddit-post>
            <shreddit-comment created="2026-09-28T09:30:00.000000+0000" author="alpha" depth="0"
              permalink="/r/Examples/comments/eee555/comment/c1/" score="60">
              <div slot="commentMeta">alpha</div>
              <div id="t1_c1-comment-rtjson-content" slot="comment"><div id="t1_c1-post-rtjson-content">
                <p>A first answer &amp; <a href="https://example.org/source">more</a></p>
              </div></div>
              <div slot="actionRow"></div>
              <shreddit-comment created="2026-09-28T09:40:00.000000+0000" author="beta" depth="1"
                permalink="/r/Examples/comments/eee555/comment/c2/" score="5">
                <div id="t1_c2-comment-rtjson-content" slot="comment"><div><p>A reply</p></div></div>
                <shreddit-comment created="2026-09-28T09:50:00.000000+0000" author="gamma" depth="2"
                  permalink="/r/Examples/comments/eee555/comment/c3/" score="1">
                  <div id="t1_c3-comment-rtjson-content" slot="comment"><div><p>Deeper</p></div></div>
                </shreddit-comment>
              </shreddit-comment>
            </shreddit-comment>
            <shreddit-comment created="2026-09-28T10:00:00.000000+0000" author="delta" depth="0"
              permalink="/r/Examples/comments/eee555/comment/c4/" score="2">
              <div id="t1_c4-comment-rtjson-content" slot="comment"><div><p>Another answer</p></div></div>
            </shreddit-comment>
        """.trimIndent()
    }
}

private val SUB_HEADER = """
<reddit-page-data data="{&quot;subreddit&quot;:{&quot;id&quot;:&quot;t5_1&quot;,&quot;prefixedName&quot;:&quot;r/Examples&quot;,&quot;name&quot;:&quot;Examples&quot;,&quot;communityIcon&quot;:&quot;https://styles.redditmedia.com/t5_1/styles/communityIcon_x.png?width=128&amp;frame=1&quot;}}"></reddit-page-data>
<shreddit-subreddit-header name="Examples" subreddit-id="t5_1" display-name="Examples" prefixed-name="r/Examples" description="Examples &amp; more, for everyone. " weekly-active-users="4"></shreddit-subreddit-header>
<div class="masthead"><div class="@container"><div id="subreddit-banner-img" class="community-banner relative bg-center" style="BANNER_STYLE"></div></div></div>
<span rpl id="subreddit-icon-img"><img src="https://b.thumbs.redditmedia.com/header.png" alt="" class="mb-0 shreddit-subreddit-icon__icon rounded-full community-icon-t5_1 w-full h-full" width="40" style="color: #11483C;" loading="lazy"></span>
"""
