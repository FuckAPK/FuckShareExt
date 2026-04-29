package org.lyaaz.fuckshare

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.content.edit
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import org.lyaaz.ui.PreferenceCategory
import org.lyaaz.ui.SwitchPreferenceItem
import org.lyaaz.ui.TextFieldPreference
import org.lyaaz.ui.keyboardAsState
import org.lyaaz.ui.theme.AppTheme as Theme

class SettingsActivity : ComponentActivity(),XposedServiceHelper.OnServiceListener{

    companion object {
        private var mService: XposedService? = null
    }
    private lateinit var setting: Settings
    private lateinit var prefs: SharedPreferences
    private var isReady by mutableStateOf(false)
    private var currentUiMode: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mService?.let { onServiceBind(it) }
        XposedServiceHelper.registerListener(this)
        enableEdgeToEdge()
        currentUiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        setContent {
            Theme {
                if (isReady) {
                    SettingsScreen(prefs, setting)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newUiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (newUiMode != currentUiMode) {
            recreate()
        }
    }

    override fun onServiceBind(service: XposedService) {
        mService = service
        prefs = service.getRemotePreferences("${BuildConfig.APPLICATION_ID}_preferences")
        setting = Settings.getInstance(prefs)
        isReady = true
    }

    override fun onServiceDied(service: XposedService) {
        mService = null
        isReady = false
    }
}

@Composable
fun SettingsScreen(prefs: SharedPreferences, settings: Settings) {
    val focusManager = LocalFocusManager.current
    val prefs = remember { prefs }
    val settings = remember { settings }
    val isKeyboardOpen by keyboardAsState()

    LaunchedEffect(isKeyboardOpen) {
        if (!isKeyboardOpen) {
            focusManager.clearFocus()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        item {
            HookCategory(settings, prefs)
        }
        item {
            Spacer(
                modifier = Modifier.windowInsetsBottomHeight(
                    WindowInsets.systemBars
                )
            )
        }
    }
}

@Composable
fun HookCategory(settings: Settings, prefs: SharedPreferences) {
    val focusManager = LocalFocusManager.current
    var enableHook by remember {
        mutableStateOf(
            settings.enableHook
        )
    }
    var excludePackages by remember {
        mutableStateOf(
            settings.excludePackages.joinToString(", ")
        )
    }
    var enableForceForwardHook by remember {
        mutableStateOf(
            settings.enableForceForwardHook
        )
    }
    var enableForcePickerHook by remember {
        mutableStateOf(
            settings.enableForcePickerHook
        )
    }
    var enableForceContentHook by remember {
        mutableStateOf(
            settings.enableForceContentHook
        )
    }
    var enableForceDocumentHook by remember {
        mutableStateOf(
            settings.enableForceDocumentHook
        )
    }
    PreferenceCategory(title = R.string.title_hook) {
        SwitchPreferenceItem(
            title = R.string.title_enable_hook,
            summary = R.string.desc_enable_hook,
            checked = enableHook,
            onCheckedChange = {
                enableHook = it
                prefs.edit { putBoolean(Settings.PREF_ENABLE_HOOK, it) }
            }
        )
        AnimatedVisibility(
            visible = enableHook
        ) {
            Column {
                TextFieldPreference(
                    title = R.string.title_exclude_packages,
                    summary = R.string.desc_exclude_packages,
                    value = excludePackages,
                    onValueChange = {
                        if (it.contains('\n')) {
                            focusManager.clearFocus()
                        }
                        // filter ascii chars
                        excludePackages = it
                            .filter { c -> c in ('a'..'z') + ('A'..'Z') + ('0'..'9') || c in " ,._:/*" }
                        prefs.edit { putString(Settings.PREF_EXCLUDE_PACKAGES, excludePackages) }
                    }
                )
                SwitchPreferenceItem(
                    title = R.string.title_enable_force_forward_hook,
                    summary = R.string.desc_enable_force_forward_hook,
                    checked = enableForceForwardHook,
                    enabled = enableHook,
                    onCheckedChange = {
                        enableForceForwardHook = it
                        prefs.edit { putBoolean(Settings.PREF_ENABLE_FORCE_FORWARD_HOOK, it) }
                    }
                )
                SwitchPreferenceItem(
                    title = R.string.title_enable_force_picker_hook,
                    summary = R.string.desc_enable_force_picker_hook,
                    checked = enableForcePickerHook,
                    enabled = enableHook,
                    onCheckedChange = {
                        enableForcePickerHook = it
                        prefs.edit { putBoolean(Settings.PREF_ENABLE_FORCE_PICKER_HOOK, it) }
                    }
                )
                SwitchPreferenceItem(
                    title = R.string.title_enable_force_content_hook,
                    summary = R.string.desc_enable_force_content_hook,
                    checked = enableForceContentHook,
                    enabled = enableHook,
                    onCheckedChange = {
                        enableForceContentHook = it
                        prefs.edit { putBoolean(Settings.PREF_ENABLE_FORCE_CONTENT_HOOK, it) }
                    }
                )
                SwitchPreferenceItem(
                    title = R.string.title_enable_force_document_hook,
                    summary = R.string.desc_enable_force_document_hook,
                    checked = enableForceDocumentHook,
                    enabled = enableHook,
                    onCheckedChange = {
                        enableForceDocumentHook = it
                        prefs.edit { putBoolean(Settings.PREF_ENABLE_FORCE_DOCUMENT_HOOK, it) }
                    }
                )
            }
        }

    }
}