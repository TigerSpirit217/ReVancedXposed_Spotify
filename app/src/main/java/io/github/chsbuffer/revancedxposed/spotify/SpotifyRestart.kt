package io.github.chsbuffer.revancedxposed.spotify

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.RemoteException
import android.os.ResultReceiver
import android.os.SystemClock
import android.widget.Toast
import io.github.chsbuffer.revancedxposed.BuildConfig
import io.github.chsbuffer.revancedxposed.R

internal object SpotifyRestart {
    private const val SPOTIFY_PACKAGE = "com.spotify.music"
    private const val ACTION = BuildConfig.APPLICATION_ID + ".action.RESTART_SPOTIFY"
    private const val PERMISSION = BuildConfig.APPLICATION_ID + ".permission.RESTART_SPOTIFY"
    private const val REPLY = "reply"
    private const val PROCESS = "process"
    private const val DEADLINE = "deadline"

    fun register(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (SystemClock.elapsedRealtime() > intent.getLongExtra(DEADLINE, 0)) return
                @Suppress("DEPRECATION")
                val reply = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(REPLY, ResultReceiver::class.java)
                } else {
                    intent.getParcelableExtra<ResultReceiver>(REPLY)
                } ?: return
                reply.send(Activity.RESULT_OK, Bundle().apply { putBinder(PROCESS, Binder()) })
                Process.killProcess(Process.myPid())
            }
        }
        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, PERMISSION, null, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter, PERMISSION, null)
        }
    }

    fun request(activity: Activity, onFinished: () -> Unit) {
        val launch = activity.packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)
        if (launch == null) {
            Toast.makeText(activity, R.string.settings_spotify_unavailable, Toast.LENGTH_SHORT).show()
            onFinished()
            return
        }
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        fun openSpotify() {
            if (finished) return
            finished = true
            onFinished()
            if (!activity.isFinishing && !activity.isDestroyed) {
                runCatching {
                    activity.startActivity(launch)
                    activity.finish()
                }.onFailure {
                    Toast.makeText(activity, R.string.settings_spotify_open_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
        // Launch directly if Spotify is already stopped or the hook cannot reply.
        val timeout = Runnable { openSpotify() }
        val reply = object : ResultReceiver(handler) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (finished || resultCode != Activity.RESULT_OK) return
                val process = resultData?.getBinder(PROCESS) ?: return
                try {
                    process.linkToDeath({
                        handler.post {
                            handler.removeCallbacks(timeout)
                            openSpotify()
                        }
                    }, 0)
                } catch (_: RemoteException) {
                    handler.removeCallbacks(timeout)
                    openSpotify()
                }
            }
        }
        handler.postDelayed(timeout, 1_500)
        activity.sendBroadcast(
            Intent(ACTION).setPackage(SPOTIFY_PACKAGE)
                .putExtra(REPLY, reply)
                .putExtra(DEADLINE, SystemClock.elapsedRealtime() + 1_000)
        )
    }
}
