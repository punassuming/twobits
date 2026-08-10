package com.shelfsnap.app

import android.app.Application
import com.shelfsnap.app.data.local.DebugLogStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ShelfSnapApplication : Application() {
    @Inject lateinit var debugLogStore: DebugLogStore

    override fun onCreate() {
        super.onCreate()
        debugLogStore.install()
    }
}
