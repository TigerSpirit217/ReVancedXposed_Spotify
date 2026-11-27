package io.github.chsbuffer.revancedxposed.spotify.misc.widgets

import android.content.pm.PackageManager
import io.github.chsbuffer.revancedxposed.HookParam
import io.github.chsbuffer.revancedxposed.XC_MethodHook
import io.github.chsbuffer.revancedxposed.XposedBridge
import io.github.chsbuffer.revancedxposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook

fun SpotifyHook.FixThirdPartyLaunchersWidgets() {
    // MediaProvider.openFile grants access based on the caller's BIND_APPWIDGET
    // permission. Grant it inside the Spotify process so third-party launchers
    // are allowed to bind Spotify widgets.
    runCatching {
        XposedHelpers.findAndHookMethod(
            "android.app.ApplicationPackageManager",
            classLoader,
            "checkPermission",
            String::class.java,
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: HookParam) {
                    if (param.args[0] == "android.permission.BIND_APPWIDGET") {
                        param.result = PackageManager.PERMISSION_GRANTED
                    }
                }
            })
    }.onFailure {
        XposedBridge.log("FixThirdPartyLaunchersWidgets hook failed: ${it.message}")
    }
}
