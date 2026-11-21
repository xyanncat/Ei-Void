package com.eivoid.app

import android.app.Application
import com.clerk.api.Clerk
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VoidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val publishableKey = BuildConfig.CLERK_PUBLISHABLE_KEY
        if (publishableKey.startsWith("pk_")) {
            // A malformed key must not take the whole app down: the login
            // screen reports that Clerk is not ready instead.
            runCatching { Clerk.initialize(this, publishableKey) }
        }
    }
}
