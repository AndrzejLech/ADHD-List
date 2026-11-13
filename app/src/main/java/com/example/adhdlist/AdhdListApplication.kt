package com.example.adhdlist

import android.app.Application
import com.example.adhdlist.data.sync.SharedListsSync
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import javax.inject.Inject

@HiltAndroidApp
class AdhdListApplication: Application() {
    @Inject
    lateinit var sharedListsSync: SharedListsSync

    override fun onCreate() {
        super.onCreate()
        // App-wide: shared lists keep syncing on whichever screen is open.
        sharedListsSync.start(MainScope())
    }
}
