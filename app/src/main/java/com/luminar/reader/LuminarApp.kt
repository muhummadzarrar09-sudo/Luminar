// app/src/main/java/com/luminar/reader/LuminarApp.kt
package com.luminar.reader

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LuminarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger(this).install()
    }
}
