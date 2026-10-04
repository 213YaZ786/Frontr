package com.frontr.app.core.model

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Which of a sub's posts Reddit sends: each order has its own address, and
 * the pages that continue it keep the order. Home still shows them newest
 * first; the order chooses the posts, not where they sit.
 *
 * Best is Reddit's own default. Asked for by name, so the order Reddit keeps
 * per device in a cookie never chooses for the reader.
 */
@Serializable
enum class FeedSort(val label: String, val path: String, val period: String? = null) {
    BEST("Best", "best"),
    HOT("Hot", "hot"),
    NEW("New", "new"),
    RISING("Rising", "rising"),
    TOP_DAY("Top today", "top", "day"),
    TOP_WEEK("Top this week", "top", "week"),
    TOP_MONTH("Top this month", "top", "month"),
    TOP_YEAR("Top this year", "top", "year"),
    TOP_ALL("Top of all time", "top", "all");

    companion object {
        fun of(name: String?): FeedSort? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * The countries Reddit's Popular can be narrowed to, as its own picker lists
 * them (geo_filter, read on reddit.com/r/popular on 2026-10-04). Only Popular
 * takes one: a sub's posts are the same everywhere.
 */
object PopularCountry {

    /** The feed that takes a country. */
    const val FEED = "popular"

    const val EVERYWHERE = "global"

    val codes = listOf(
        "us", "ar", "au", "bg", "ca", "cl", "co", "hr", "cz", "fi", "fr", "de", "gr", "hu", "is", "in", "ie",
        "it", "jp", "my", "mx", "nz", "ph", "pl", "pt", "pr", "ro", "rs", "sg", "es", "se", "tw", "th", "tr", "gb"
    )

    /** The phone's own country when Reddit has it, else everywhere. */
    fun ofPhone(locale: Locale = Locale.getDefault()): String =
        locale.country.lowercase().takeIf { it in codes } ?: EVERYWHERE

    /** What a stored choice means: null follows the phone. */
    fun resolve(stored: String?): String = stored?.takeIf { it == EVERYWHERE || it in codes } ?: ofPhone()

    /** In the phone's language, from the system's own names. */
    fun label(code: String): String =
        if (code == EVERYWHERE) "Everywhere" else Locale.Builder().setRegion(code.uppercase()).build().displayCountry
}
