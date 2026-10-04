package com.frontr.app.core.model


import com.frontr.app.core.link.RedditLink
import kotlinx.serialization.Serializable

@Serializable
data class FollowedAccount(
    val handle: String,
    val displayName: String? = null,
    /**
     * The folder this account is filed in. Never blank: an account put
     * nowhere is in [MAIN], which is also how a file written before folders
     * existed loads. The list of folders, empty ones included, is kept by
     * [com.frontr.app.data.accounts.AccountStore].
     */
    val folder: String = MAIN,
    val addedAtMillis: Long = 0L,
    /** Which of its posts Reddit sends, see [FeedSort]. */
    val sort: FeedSort = FeedSort.BEST,
    /** Popular only: Reddit's country code, or null for the phone's country. */
    val country: String? = null
) {
    companion object {
        /** Where an account goes when it has been put nowhere. Cannot be renamed or deleted. */
        const val MAIN = "Main"

        /**
         * A followed sub, by its name: android, from "android", "r/android" or
         * "/r/Android". Lowercased, as Reddit treats sub names. Validated
         * locally so a typo fails at once instead of costing a request.
         */
        fun normalise(raw: String): String? {
            val value = raw.trim().removePrefix("/").removePrefix("r/").removePrefix("R/").trimEnd('/', '.')
            return value.takeIf { RedditLink.isSubreddit(it) }?.lowercase()
        }
    }
}
