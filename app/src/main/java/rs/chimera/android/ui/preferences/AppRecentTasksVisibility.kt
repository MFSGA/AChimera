package rs.chimera.android.ui.preferences

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Bundle

object AppRecentTasksVisibility {
    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    apply(activity)
                }

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) {
                    activity.window.decorView.post { apply(activity) }
                }

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    fun apply(context: Context, excludeFromRecents: Boolean = AppPreferences.hideFromRecents(context)) {
        context.getSystemService(ActivityManager::class.java)
            ?.appTasks
            ?.forEach { task -> task.setExcludeFromRecents(excludeFromRecents) }
    }
}
