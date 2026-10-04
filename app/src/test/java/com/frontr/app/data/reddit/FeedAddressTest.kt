package com.frontr.app.data.reddit

import com.frontr.app.core.model.FeedSort
import com.frontr.app.core.model.PopularCountry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FeedAddressTest {

    @Test
    fun `each order has its address, top with its period`() {
        assertEquals("https://www.reddit.com/r/GrapheneOS/best/", FeedAddress.firstPage("GrapheneOS", FeedSort.BEST))
        assertEquals("https://www.reddit.com/r/worldnews/new/", FeedAddress.firstPage("worldnews", FeedSort.NEW))
        assertEquals("https://www.reddit.com/r/worldnews/top/?t=week", FeedAddress.firstPage("worldnews", FeedSort.TOP_WEEK))
    }

    @Test
    fun `Popular carries its country`() {
        assertEquals("https://www.reddit.com/r/popular/hot/?geo_filter=fr", FeedAddress.firstPage("popular", FeedSort.HOT, "fr"))
        assertEquals("https://www.reddit.com/r/popular/top/?t=day&geo_filter=global", FeedAddress.firstPage("popular", FeedSort.TOP_DAY, "global"))
    }

    @Test
    fun `the phone's country when Reddit has it, else everywhere`() {
        assertEquals("fr", PopularCountry.ofPhone(Locale.FRANCE))
        assertEquals("global", PopularCountry.ofPhone(Locale.Builder().setRegion("DZ").build()))
        assertEquals("de", PopularCountry.resolve("de"))
        assertEquals("global", PopularCountry.resolve("global"))
    }
}
