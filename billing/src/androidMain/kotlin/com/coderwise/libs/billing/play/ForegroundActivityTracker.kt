package com.coderwise.libs.billing.play

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Remembers whichever Activity is currently resumed.
 *
 * Play's purchase sheet is launched *onto* an Activity, but nothing above this
 * library has one to give: the buy call comes from a ViewModel behind a Koin
 * singleton. Holding the Activity here — rather than making callers thread one
 * down through the paywall — keeps the shared [com.coderwise.libs.billing.Billing]
 * API free of Android types.
 *
 * Only the resumed Activity is kept, and it is dropped again on pause, so a
 * destroyed Activity is never leaked past its own lifetime.
 */
internal class ForegroundActivityTracker(application: Application) {
    @Volatile
    var current: Activity? = null
        private set

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    current = activity
                }

                override fun onActivityPaused(activity: Activity) {
                    if (current === activity) current = null
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }
}
