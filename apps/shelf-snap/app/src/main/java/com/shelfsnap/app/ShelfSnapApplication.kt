package com.shelfsnap.app

import android.app.Application
import com.shelfsnap.app.data.local.CrashLogStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ShelfSnapApplication : Application() {
    @Inject lateinit var crashLogStore: CrashLogStore

    override fun onCreate() {
        super.onCreate()
        crashLogStore.install()
    }
}
