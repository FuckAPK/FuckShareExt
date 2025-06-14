package org.lyaaz.fuckshare

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.service.chooser.ChooserAction
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.lyaaz.fuckshare.utils.IntentUtils
import kotlin.text.split

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (!lpparam.isFirstApplication) {
            return
        }
        when (lpparam.packageName) {
            FUCK_SHARE_PACKAGE_NAME -> {
                return
            }

            "android" -> {
                hookSystem(lpparam)
            }

            else -> {
                hookActivity(lpparam)
            }
        }
    }

    private fun hookSystem(lpparam: LoadPackageParam) {
        val activityTaskManagerServiceClass = runCatching {
            XposedHelpers.findClass(
                "com.android.server.wm.ActivityTaskManagerService",
                lpparam.classLoader
            )
        }.onFailure {
            XposedBridge.log(it)
        }.getOrNull() ?: return

        runCatching {
            XposedHelpers.findAndHookMethod(
                activityTaskManagerServiceClass,
                "startActivityAsUser",
                "android.app.IApplicationThread",
                String::class.java,
                String::class.java,
                Intent::class.java,
                String::class.java,
                IBinder::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                "android.app.ProfilerInfo",
                Bundle::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                StartActivityAsUserHook
            )
        }.onFailure {
            XposedBridge.log(it)
        }.onSuccess {
            XposedBridge.log("FS: hooked StartActivityAsUser")
        }

        runCatching {
            XposedHelpers.findAndHookMethod(
                activityTaskManagerServiceClass,
                "startActivityIntentSender",
                "android.app.IApplicationThread",
                "android.content.IIntentSender",
                IBinder::class.java,
                Intent::class.java,
                String::class.java,
                IBinder::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Bundle::class.java,
                StartActivityIntentSenderHook
            )
        }.onFailure {
            XposedBridge.log(it)
        }.onSuccess {
            XposedBridge.log("FS: hooked StartActivityIntentSender")
        }
    }

    private fun hookActivity(lpparam: LoadPackageParam) {
        runCatching {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "startActivityForResult",
                Intent::class.java,
                Int::class.javaPrimitiveType,
                Bundle::class.java,
                StartActivityForResultHook
            )
        }.onFailure {
            XposedBridge.log(it)
        }.onSuccess {
            XposedBridge.log("FS: hooked ${lpparam.packageName}")
        }
    }

    private object StartActivityForResultHook : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            runCatching {
                val intent = param.args[0] as Intent
                process(intent, "")?.let {
                    param.args[0] = it
                }
            }.onFailure {
                XposedBridge.log(it)
            }
        }
    }

    private object StartActivityAsUserHook : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            runCatching {
                val callingPackage = param.args[1] as String
                val intent = param.args[3] as Intent
                process(intent, callingPackage)?.let {
                    param.args[3] = it
                }
            }.onFailure {
                XposedBridge.log(it)
            }
        }
    }

    private object StartActivityIntentSenderHook : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            runCatching {
                val key = XposedHelpers.getObjectField(param.args[1], "key")
                val intent = XposedHelpers.getObjectField(key, "requestIntent") as Intent
                val callingPackage =
                    XposedHelpers.getObjectField(key, "packageName") as String
                process(intent, callingPackage)?.let {
                    param.args[3] = it
                }
            }.onFailure {
                XposedBridge.log(it)
            }
        }
    }

    companion object {
        private const val FUCK_SHARE_PACKAGE_NAME = "org.lyaaz.fuckshare"
        private const val HANDLE_SHARE_ACTIVITY_NAME = "$FUCK_SHARE_PACKAGE_NAME.HandleShareActivity"
        private const val CONTENT_PROXY_ACTIVITY = "$FUCK_SHARE_PACKAGE_NAME.ContentProxyActivity"

        private val prefs: XSharedPreferences by lazy {
            XSharedPreferences(BuildConfig.APPLICATION_ID)
        }
        private val settings: Settings by lazy {
            Settings.getInstance(prefs)
        }
        private val hookedIntents = setOf(
            Intent.ACTION_CHOOSER,
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE,
            Intent.ACTION_PICK,
            Intent.ACTION_GET_CONTENT,
            Intent.ACTION_OPEN_DOCUMENT
        )
        private val actionHookEnableMap = mapOf(
            Intent.ACTION_SEND to { settings.enableForceForwardHook },
            Intent.ACTION_SEND_MULTIPLE to { settings.enableForceForwardHook },
            Intent.ACTION_PICK to { settings.enableForcePickerHook },
            Intent.ACTION_GET_CONTENT to { settings.enableForceContentHook },
            Intent.ACTION_OPEN_DOCUMENT to { settings.enableForceDocumentHook }
        )
        private val actionClassMap = mapOf(
            Intent.ACTION_SEND to HANDLE_SHARE_ACTIVITY_NAME,
            Intent.ACTION_SEND_MULTIPLE to HANDLE_SHARE_ACTIVITY_NAME,
            Intent.ACTION_PICK to CONTENT_PROXY_ACTIVITY,
            Intent.ACTION_GET_CONTENT to CONTENT_PROXY_ACTIVITY,
            Intent.ACTION_OPEN_DOCUMENT to CONTENT_PROXY_ACTIVITY
        )

        private fun actionHookEnabled(action: String?): Boolean {
            return actionHookEnableMap.getOrDefault(action) { false }.invoke()
        }

        private fun excludeRuleMatch(
            rules: Set<String>,
            packageName: String,
            mimeType: String?
        ): Boolean {
            val mimeTypeMatch = { pattern: String, type: String? ->
                pattern in setOf("*", "*/*", type)
                        || (pattern.endsWith("/*") && type?.startsWith(pattern.removeSuffix("*")) == true)
            }
            return rules.map {
                it.split(':').let {
                    val first = it[0].ifBlank { "*" }
                    val second = if (it.size == 1 || it[1].isBlank()) "*" else it[1].lowercase()
                    first to second
                }
            }.any {
                it.first in setOf("*", packageName)
                        && mimeTypeMatch(it.second.lowercase(), mimeType?.lowercase())
            }
        }

        private fun process(intent: Intent, callingPackage: String): Intent? {
            if (callingPackage == FUCK_SHARE_PACKAGE_NAME || intent.action !in hookedIntents) {
                return null
            }

            prefs.reload()
            if (!settings.enableHook || callingPackage in settings.excludePackages) {
                return null
            }
            val extraIntent = retrieveExtraIntent(Intent(intent)) ?: return null
            if (excludeRuleMatch(settings.excludePackages, callingPackage, extraIntent.type)) {
                return null
            }
            if (!actionHookEnabled(extraIntent.action)) {
                return null
            }
            val className = actionClassMap[extraIntent.action] ?: return null

            return extraIntent.apply {
                setClassName(FUCK_SHARE_PACKAGE_NAME, className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }.also {
                XposedBridge.log("FS: hooked from $callingPackage, intent: $intent, to: $this")
            }
        }

        private fun retrieveExtraIntent(intent: Intent): Intent? {
            return if (intent.action == Intent.ACTION_CHOOSER) {
                IntentUtils.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java
                )?.apply {
                    setOf(Intent.EXTRA_INITIAL_INTENTS, Intent.EXTRA_ALTERNATE_INTENTS).forEach {
                        IntentUtils.backupArrayExtras<Intent>(
                            intent,
                            this,
                            it
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        IntentUtils.backupArrayExtras<ChooserAction>(
                            intent,
                            this,
                            Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS
                        )
                    }
                } ?: return null
            } else {
                intent.component?.let {
                    if (it.packageName != "com.android.documentsui") {
                        return null
                    }
                }
                intent
            }
        }
    }
}
