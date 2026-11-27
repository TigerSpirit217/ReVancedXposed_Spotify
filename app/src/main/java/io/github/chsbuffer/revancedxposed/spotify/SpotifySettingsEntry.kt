package io.github.chsbuffer.revancedxposed.spotify

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import io.github.chsbuffer.revancedxposed.BuildConfig
import kotlin.math.roundToInt

internal object SpotifySettingsEntry {
    fun register(app: Application) {
        val drawerId = app.resources.getIdentifier("sidedrawer_compose_view", "id", app.packageName)
        if (drawerId == 0) return

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity.javaClass.name != "com.spotify.music.SpotifyMainActivity") return
                activity.window.decorView.post {
                    if (!activity.isDestroyed) addEntry(activity, drawerId)
                }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun addEntry(activity: Activity, drawerId: Int) {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val inset = (12 * activity.resources.displayMetrics.density).roundToInt()
        val button = Button(activity).apply {
            text = "ReVanced Xposed Settings"
            isAllCaps = false
            visibility = View.INVISIBLE
            setOnClickListener {
                val intent = Intent().setComponent(
                    ComponentName(BuildConfig.APPLICATION_ID, SettingsActivity::class.java.name)
                )
                runCatching { activity.startActivity(intent) }.onFailure {
                    Toast.makeText(activity, "Could not open module settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
        content.addView(button, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT
        ))

        val bounds = Rect()
        val location = IntArray(2)
        var drawer: View? = null
        content.viewTreeObserver.addOnPreDrawListener {
            val currentDrawer = drawer?.takeIf { it.isAttachedToWindow } ?: content.findViewById<View>(drawerId)
            drawer = currentDrawer
            if (currentDrawer == null || !currentDrawer.isShown || !currentDrawer.getGlobalVisibleRect(bounds) ||
                bounds.width() < currentDrawer.width * 0.9f || bounds.width() < button.width + inset * 2 ||
                bounds.height() < button.height + inset * 2
            ) {
                button.visibility = View.INVISIBLE
            } else {
                content.getLocationInWindow(location)
                button.translationX = (bounds.left - location[0] + inset).toFloat()
                button.translationY = (bounds.bottom - location[1] - button.height - inset).toFloat()
                button.visibility = View.VISIBLE
            }
            true
        }
    }
}
