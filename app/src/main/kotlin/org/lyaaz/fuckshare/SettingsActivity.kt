package org.lyaaz.fuckshare

import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import org.lyaaz.fuckshare.utils.AppUtils
import org.lyaaz.fuckshare.ui.AppTheme as Theme

class SettingsActivity : ComponentActivity() {

    private var currentUiMode: Int? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currentUiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        setContent {
            Theme {
                SettingsScreen()
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
}

@Preview
@Composable
fun SettingsScreenPreview() {
    Theme {
        SettingsScreen()
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val prefs = remember { AppUtils.getPrefs(context) }
    val settings = remember { Settings.getInstance(prefs) }
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

@Composable
fun keyboardAsState(): State<Boolean> {
    val keyboardState = remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(view) {
        val onGlobalListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            keyboardState.value = if (keypadHeight > screenHeight * 0.15) {
                true
            } else {
                false
            }
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(onGlobalListener)

        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalListener)
        }
    }
    return keyboardState
}

@Composable
fun PreferenceCategory(
    @StringRes title: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text(
            text = stringResource(id = title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
fun SwitchPreferenceItem(
    @StringRes title: Int,
    @StringRes summary: Int? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    noSwitch: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(if (enabled) 1.0f else 0.6f)
                )
                if (summary != null) {
                    Text(
                        text = stringResource(id = summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(if (enabled) 1.0f else 0.6f)
                    )
                }
            }
            if (!noSwitch) {
                Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
            }
        }
    }
}

@Composable
fun TextFieldPreference(
    @StringRes title: Int,
    @StringRes summary: Int? = null,
    @StringRes unit: Int? = null,
    enabled: Boolean = true,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(id = title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(if (enabled) 1.0f else 0.6f)
        )
        if (summary != null) {
            Text(
                text = stringResource(id = summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(if (enabled) 0.6f else 0.4f)
            )
        }
        val focusManager = LocalFocusManager.current
        OutlinedTextField(
            enabled = enabled,
            value = value,
            onValueChange = onValueChange,
            singleLine = false,
            maxLines = 3,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done
            ),
            suffix = unit?.let {
                @Composable {
                    Text(
                        text = stringResource(id = it),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary.copy(if (enabled) 1.0f else 0.6f)
                    )
                }
            },
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun DropDownPreference(
    @StringRes title: Int,
    @StringRes summary: Int? = null,
    enabled: Boolean = true,
    @StringRes selected: Int,
    content: @Composable ColumnScope.(onItemSelected: () -> Unit) -> Unit
) {
    var expended by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .height(50.dp)
            .clickable { expended = true }) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(id = title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(if (enabled) 1.0f else 0.6f)
                )
                if (summary != null) {
                    Text(
                        text = stringResource(id = summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(if (enabled) 1.0f else 0.6f)
                    )
                }
            }
            Box(
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = stringResource(selected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary.copy(if (enabled) 1.0f else 0.6f)
                )
                DropdownMenu(
                    expanded = expended,
                    onDismissRequest = { expended = false },
                ) {
                    if (enabled) {
                        content { expended = false }
                    }
                }
            }
        }
    }
}