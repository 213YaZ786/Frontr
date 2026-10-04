package com.frontr.app.feature.accounts

import com.frontr.app.core.link.RedditLink
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import com.frontr.app.core.common.Outcome
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frontr.app.core.model.FeedSort
import com.frontr.app.core.model.FollowedAccount
import com.frontr.app.data.accounts.AccountStore
import com.frontr.app.data.cache.FeedCache
import com.frontr.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One followed account as the list shows it, enriched from the local cache. */
data class AccountRow(
    val handle: String,
    val name: String?,
    val avatarUrl: String?,
    val lastPostMillis: Long?,
    val folder: String = FollowedAccount.MAIN,
    val sort: FeedSort = FeedSort.BEST,
    /** Popular only: the country chosen, null for the phone's. */
    val country: String? = null
)

/**
 * Accounts and search in one place. The query filters the accounts you follow,
 * and when it is a valid handle you do not follow yet, the screen offers to
 * open that profile. Nothing is ever followed without an explicit tap.
 */
class AccountsViewModel(
    private val store: AccountStore,
    private val cache: FeedCache,
    private val settings: SettingsStore
) : ViewModel() {

    private data class Summary(val name: String?, val avatarUrl: String?, val lastPostMillis: Long?)

    private val summaries = MutableStateFlow<Map<String, Summary>>(emptyMap())

    /** Sorted by name, because this list is for finding an account, not for reading. */
    val rows: StateFlow<List<AccountRow>> = combine(store.accounts, summaries) { accounts, known ->
        accounts.map { account ->
            val summary = known[account.handle.lowercase()]
            AccountRow(
                handle = account.handle,
                name = summary?.name ?: account.displayName,
                avatarUrl = summary?.avatarUrl,
                lastPostMillis = summary?.lastPostMillis,
                folder = account.folder,
                sort = account.sort,
                country = account.country
            )
        }.sortedBy { (it.name ?: it.handle).lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        store.accounts.onEach { refresh() }.launchIn(viewModelScope)
        // A sub fetched again, after a new order for instance, shows its newest post.
        cache.written.onEach { refresh() }.launchIn(viewModelScope)
    }

    /** Re-reads avatars and last post dates. Cheap: local files only, no network. */
    fun refresh() {
        viewModelScope.launch {
            summaries.value = store.accounts.value.associate { account ->
                val feed = cache.read(account.handle)
                account.handle.lowercase() to Summary(
                    name = feed?.displayName?.takeIf { it.isNotBlank() && it != account.handle },
                    avatarUrl = feed?.avatarUrl,
                    lastPostMillis = feed?.posts
                        ?.maxOfOrNull { it.publishedAtMillis }
                        ?.takeIf { it > 0L }
                )
            }
        }
    }

    fun isFollowed(handle: String): Boolean =
        store.accounts.value.any { it.handle.equals(handle, ignoreCase = true) }

    fun follow(handle: String, sort: FeedSort, country: String?) {
        store.add(handle, sort, country)
    }

    /**
     * The posts stored in the old order go, so the sub shows the new one,
     * not a mix; Home fetches it again as soon as the order is saved.
     */
    fun setSort(handle: String, sort: FeedSort) {
        viewModelScope.launch {
            if (store.accounts.value.firstOrNull { it.handle.equals(handle, ignoreCase = true) }?.sort == sort) return@launch
            cache.restart(handle)
            store.setSort(handle, sort)
        }
    }

    /** Popular's country, null for the phone's; its posts change as an order would. */
    fun setCountry(handle: String, country: String?) {
        viewModelScope.launch {
            cache.restart(handle)
            store.setCountry(handle, country)
        }
    }

    /** Every folder, Main first. */
    val folders: StateFlow<List<String>> = store.folders

    /** Returns the folder to open, the existing one when the name is taken. */
    fun createFolder(name: String): String? = store.createFolder(name)

    fun setFolder(handle: String, folder: String) = store.setFolder(handle, folder)

    fun deleteFolder(name: String) = store.deleteFolder(name)

    /**
     * Returns the folder's new name. When Home was showing it, Home moves to
     * the new name with it rather than falling back to every account.
     */
    fun renameFolder(from: String, to: String): String? {
        val renamed = store.renameFolder(from, to) ?: return null
        if (settings.current.homeFolder == from) settings.update { it.copy(homeFolder = renamed) }
        return renamed
    }

    companion object {
        /**
         * The query as a sub, or null when it cannot be one: a name, android
         * or r/android, or a pasted link, a sub's or a post's, with or
         * without the scheme.
         */
        fun asHandle(query: String): String? {
            val trimmed = query.trim()
            if ('.' !in trimmed) return FollowedAccount.normalise(trimmed)
            val url = if ("://" in trimmed) trimmed else "https://$trimmed"
            return when (val link = RedditLink.parse(url)) {
                is RedditLink.Sub -> link.name.lowercase()
                is RedditLink.Post -> link.sub?.lowercase()
                null -> null
            }
        }
    }
}
