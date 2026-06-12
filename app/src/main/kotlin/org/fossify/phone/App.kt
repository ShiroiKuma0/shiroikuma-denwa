package org.fossify.phone

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import org.fossify.commons.FossifyApp
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.migrateToPureYellowIfNeeded
import org.fossify.phone.extensions.seedBlackYellowThemeIfNeeded
import org.fossify.phone.extensions.themeColor

class App : FossifyApp() {
    override fun onCreate() {
        super.onCreate()
        // Apply the default black/yellow look once, before any activity themes itself.
        seedBlackYellowThemeIfNeeded()
        // Rewrite colors persisted with the legacy material yellow to pure yellow, once.
        migrateToPureYellowIfNeeded()
        // Recolor every screen's top-bar foreground from the header slots, so it propagates even to
        // sub-screens we don't own (e.g. the commons Settings → About page).
        registerActivityLifecycleCallbacks(TopBarColorizer())
    }
}

// Tints a screen's toolbar title text, back arrow and overflow icon from the HEADER_TITLE / HEADER_ARROW
// slots after it resumes. The main screen is skipped — it paints its own search bar + overflow chrome.
private class TopBarColorizer : Application.ActivityLifecycleCallbacks {
    override fun onActivityResumed(activity: Activity) {
        if (activity is MainActivity) {
            return
        }

        val content = activity.findViewById<View>(android.R.id.content) ?: return
        content.post {
            val toolbar = content.findToolbar() ?: return@post
            val titleColor = activity.themeColor(ThemeSlot.HEADER_TITLE)
            val arrowColor = activity.themeColor(ThemeSlot.HEADER_ARROW)
            toolbar.setTitleTextColor(titleColor)
            toolbar.navigationIcon?.applyColorFilter(arrowColor)
            toolbar.overflowIcon?.applyColorFilter(titleColor)
        }
    }

    private fun View.findToolbar(): Toolbar? {
        if (this is Toolbar) {
            return this
        }
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                getChildAt(i).findToolbar()?.let { return it }
            }
        }
        return null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
