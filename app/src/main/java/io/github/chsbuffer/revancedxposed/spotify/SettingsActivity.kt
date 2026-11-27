package io.github.chsbuffer.revancedxposed.spotify

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import io.github.chsbuffer.revancedxposed.PREF_ENABLE_ADBLOCK
import io.github.chsbuffer.revancedxposed.PREF_ENABLE_MONET
import io.github.chsbuffer.revancedxposed.PREF_ENABLE_PREMIUM
import io.github.chsbuffer.revancedxposed.PREF_ENABLE_ROUND_UI
import io.github.chsbuffer.revancedxposed.PREF_FILE
import io.github.chsbuffer.revancedxposed.PREF_LYRICS_BETTER_LYRICS
import io.github.chsbuffer.revancedxposed.PREF_LYRICS_NETEASE
import io.github.chsbuffer.revancedxposed.PREF_SETTINGS_LANGUAGE
import io.github.chsbuffer.revancedxposed.PREF_SIMPLIFY_CHINESE
import io.github.chsbuffer.revancedxposed.R
import io.github.chsbuffer.revancedxposed.spotify.misc.lyrics.LyricsSourceSettings
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.io.File

class SettingsActivity : ComponentActivity(), XposedServiceHelper.OnServiceListener {
    companion object {
        // The framework may not redeliver the service when a locale change recreates this activity.
        @Volatile
        private var xposedService: XposedService? = null
        private val reconnectSettings = setOf(
            PREF_SETTINGS_LANGUAGE, PREF_SIMPLIFY_CHINESE, PREF_LYRICS_BETTER_LYRICS, PREF_LYRICS_NETEASE
        )
    }

    private lateinit var language: SettingsLanguage

    private val prefs by lazy {
        getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }

    override fun onServiceBind(service: XposedService) {
        xposedService = service
        runCatching {
            val remotePrefs = service.getRemotePreferences(PREF_FILE)
            val remoteValues = remotePrefs.all
            // Seed existing switches and sync language and lyrics choices saved while disconnected.
            val editor = remotePrefs.edit()
            var changed = false
            for ((k, v) in prefs.all) {
                if ((v is Boolean && k !in remoteValues) ||
                    (k in reconnectSettings && v != remoteValues[k])
                ) {
                    editor.putSetting(k, v)
                    changed = true
                }
            }
            if (changed) editor.apply()
        }
    }

    override fun onServiceDied(service: XposedService) {
        if (xposedService === service) xposedService = null
    }

    override fun attachBaseContext(newBase: Context) {
        language = SettingsLanguage.read(newBase.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE))
        super.attachBaseContext(language.applyTo(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { XposedServiceHelper.registerListener(this) }

        makePrefsReadable()

        setContent {
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(this)
            } else {
                darkColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen()
                }
            }
        }
    }

    @Composable
    fun SettingsScreen() {
        val scrollState = rememberScrollState()
        var restarting by remember { mutableStateOf(false) }
        var lyricsSources by remember { mutableStateOf(LyricsSourceSettings.read(prefs)) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).padding(bottom = 6.dp)
                )
                TextButton(
                    enabled = !restarting,
                    onClick = {
                        restarting = true
                        SpotifyRestart.request(this@SettingsActivity) { restarting = false }
                    }
                ) {
                    Text(stringResource(R.string.settings_restart_spotify))
                }
            }

            Text(
                text = stringResource(R.string.settings_restart_hint),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            LanguageSelector()
            Spacer(Modifier.height(12.dp))
            if (language == SettingsLanguage.SIMPLIFIED_CHINESE) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    SettingsRow(
                        title = stringResource(R.string.settings_simplify_chinese),
                        subtitle = stringResource(R.string.settings_simplify_chinese_description),
                        prefKey = PREF_SIMPLIFY_CHINESE,
                        defaultValue = false
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    SettingsRow(
                        title = stringResource(R.string.settings_premium),
                        subtitle = stringResource(R.string.settings_premium_description),
                        prefKey = PREF_ENABLE_PREMIUM,
                        defaultValue = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsRow(
                        title = stringResource(R.string.settings_adblock),
                        subtitle = stringResource(R.string.settings_adblock_description),
                        prefKey = PREF_ENABLE_ADBLOCK,
                        defaultValue = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsRow(
                        title = stringResource(R.string.settings_monet),
                        subtitle = stringResource(R.string.settings_monet_description),
                        prefKey = PREF_ENABLE_MONET,
                        defaultValue = false
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsRow(
                        title = stringResource(R.string.settings_roundy),
                        subtitle = stringResource(R.string.settings_roundy_description),
                        prefKey = PREF_ENABLE_ROUND_UI,
                        defaultValue = false
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.settings_lyrics_sources), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    SettingsRow(
                        title = stringResource(R.string.settings_better_lyrics),
                        subtitle = stringResource(R.string.settings_better_lyrics_description),
                        checked = lyricsSources.betterLyrics
                    ) { enabled ->
                        savePref(PREF_LYRICS_BETTER_LYRICS, enabled)
                        lyricsSources = LyricsSourceSettings.read(prefs)
                    }
                    HorizontalDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_netease),
                        subtitle = stringResource(R.string.settings_netease_description),
                        checked = lyricsSources.netEase
                    ) { enabled ->
                        savePref(PREF_LYRICS_NETEASE, enabled)
                        lyricsSources = LyricsSourceSettings.read(prefs)
                    }
                }
            }
        }
    }

    @Composable
    private fun LanguageSelector() {
        var expanded by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_language),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            Box {
                TextButton(onClick = { expanded = true }) { Text(language.displayName) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    SettingsLanguage.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                expanded = false
                                savePref(PREF_SETTINGS_LANGUAGE, option.tag)
                                if (option != language) recreate()
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun SettingsRow(title: String, subtitle: String, prefKey: String, defaultValue: Boolean) {
        var checked by remember { mutableStateOf(prefs.getBoolean(prefKey, defaultValue)) }
        SettingsRow(title, subtitle, checked) { value ->
            checked = value
            savePref(prefKey, value)
        }
    }

    @Composable
    private fun SettingsRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }

    private fun savePref(key: String, value: Any) {
        prefs.edit(commit = true) { putSetting(key, value) }
        runCatching {
            xposedService?.getRemotePreferences(PREF_FILE)?.edit { putSetting(key, value) }
        }
        makePrefsReadable()
    }

    private fun SharedPreferences.Editor.putSetting(key: String, value: Any?) {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is String -> putString(key, value)
        }
    }

    private fun makePrefsReadable() {
        runCatching {
            val prefsFile = File(applicationInfo.dataDir, "shared_prefs/$PREF_FILE.xml")
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false)
            }
        }
    }
}
