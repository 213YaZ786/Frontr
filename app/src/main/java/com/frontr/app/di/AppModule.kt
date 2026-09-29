package com.frontr.app.di

import com.frontr.app.data.reddit.RedditApi
import com.frontr.app.core.debug.LogExporter
import com.frontr.app.core.debug.RequestLog
import com.frontr.app.core.media.AutoMediaDownloader
import com.frontr.app.core.media.OfflineMedia
import com.frontr.app.core.media.MediaDownloader
import com.frontr.app.core.media.MediaSavingNotice
import com.frontr.app.core.network.ConnectivityMonitor
import com.frontr.app.core.network.HostThrottle
import com.frontr.app.core.network.HttpClientFactory
import com.frontr.app.core.link.LinkRouter
import com.frontr.app.data.accounts.AccountStore
import com.frontr.app.data.cache.FeedCache
import com.frontr.app.data.repository.FeedRepository
import com.frontr.app.data.repository.TimelineRepository
import com.frontr.app.data.read.ReadMarks
import com.frontr.app.data.settings.SettingsStore
import com.frontr.app.feature.accounts.AccountsViewModel
import com.frontr.app.feature.post.PostDetailViewModel
import com.frontr.app.feature.search.SearchViewModel
import com.frontr.app.feature.settings.SettingsViewModel
import com.frontr.app.feature.feed.FeedViewModel
import com.frontr.app.feature.media.SavedMediaViewModel
import com.frontr.app.feature.timeline.TimelineViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Single composition root. Layers get added here as they land:
 * step 3 sources, step 4 database.
 */
val appModule = module {

    single(named("appScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { RequestLog() }
    single { LogExporter(androidContext()) }
    single { HostThrottle() }
    single { HttpClientFactory.create() }
    single { RedditApi(get(), get(), get()) }
    single { ConnectivityMonitor(androidContext()) }
    // Reddit hands out its videos as MP4 files, saved as they are.
    single { MediaDownloader(androidContext(), get(named("appScope"))) { file -> file } }
    single { OfflineMedia(androidContext()) }
    single { ReadMarks(androidContext()) }
    single { MediaSavingNotice(androidContext(), get(named("appScope"))) }
    single { AutoMediaDownloader(get(), get(), get(), get(), get(), get()) }
    single { AccountStore(androidContext()) }
    single { LinkRouter() }
    single { FeedRepository(get()) }
    single {
        val settings: SettingsStore = get()
        FeedCache(androidContext(), get()) { settings.current.keepPostsDays }
    }
    single { SettingsStore(androidContext()) }
    single { TimelineRepository(get(), get(), get()) }

    viewModel { AccountsViewModel(get(), get(), get()) }
    viewModel { PostDetailViewModel(get(), get()) }
    viewModel { SearchViewModel(get()) }
    viewModel { FeedViewModel(get(), get(), get()) }
    viewModel { TimelineViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), androidContext()) }
    viewModel { SavedMediaViewModel(get()) }
}
