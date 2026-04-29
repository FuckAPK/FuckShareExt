package org.lyaaz.fuckshare

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.service.chooser.ChooserAction
import android.util.Log
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import org.lyaaz.fuckshare.utils.IntentUtils
import java.lang.reflect.Field

class MainHook : XposedModule() {

    private lateinit var prefs: SharedPreferences
    private lateinit var settings: Settings

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        prefs = getRemotePreferences("${BuildConfig.APPLICATION_ID}_preferences")
        settings = Settings.getInstance(prefs)
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        hookSystem(param.classLoader)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage()) return
        if (param.packageName == FUCK_SHARE_PACKAGE_NAME) return
        hookActivity()
    }

    private fun hookSystem(classLoader: ClassLoader) {
        val activityTaskManagerServiceClass = runCatching {
            Class.forName(
                "com.android.server.wm.ActivityTaskManagerService",
                false,
                classLoader
            )
        }.onFailure {
            log(Log.ERROR, TAG, "Failed to find ActivityTaskManagerService", it)
        }.getOrNull() ?: return

        runCatching {
            val method = activityTaskManagerServiceClass.getDeclaredMethod(
                "startActivityAsUser",
                classLoader.loadClass("android.app.IApplicationThread"),
                String::class.java,
                String::class.java,
                Intent::class.java,
                String::class.java,
                IBinder::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                classLoader.loadClass("android.app.ProfilerInfo"),
                Bundle::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            hook(method).intercept(StartActivityAsUserHooker())
        }.onFailure {
            log(Log.ERROR, TAG, "Failed to hook startActivityAsUser", it)
        }.onSuccess {
            log(Log.INFO, TAG, "FS: hooked StartActivityAsUser")
        }

        runCatching {
            val method = activityTaskManagerServiceClass.getDeclaredMethod(
                "startActivityIntentSender",
                classLoader.loadClass("android.app.IApplicationThread"),
                classLoader.loadClass("android.content.IIntentSender"),
                IBinder::class.java,
                Intent::class.java,
                String::class.java,
                IBinder::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Bundle::class.java
            )
            hook(method).intercept(StartActivityIntentSenderHooker())
        }.onFailure {
            log(Log.ERROR, TAG, "Failed to hook startActivityIntentSender", it)
        }.onSuccess {
            log(Log.INFO, TAG, "FS: hooked StartActivityIntentSender")
        }

        val pendingIntentRecordClass = runCatching {
            Class.forName(
                "com.android.server.am.PendingIntentRecord",
                false,
                classLoader
            )
        }.onFailure {
            log(Log.ERROR, TAG, "Failed to find PendingIntentRecord", it)
        }.getOrNull()

        if (pendingIntentRecordClass != null) {
            runCatching {
                var hooked = 0
                pendingIntentRecordClass.declaredMethods
                    .filter { it.name == "sendInner" }
                    .forEach { method ->
                        hook(method).intercept(PendingIntentRecordSendInnerHooker())
                        hooked++
                    }
                if (hooked == 0) error("no sendInner methods found")
            }.onFailure {
                log(Log.ERROR, TAG, "Failed to hook PendingIntentRecord.sendInner", it)
            }.onSuccess {
                log(Log.INFO, TAG, "FS: hooked PendingIntentRecord.sendInner")
            }
        }
    }

    private fun hookActivity() {
        runCatching {
            val method = Activity::class.java.getDeclaredMethod(
                "startActivityForResult",
                Intent::class.java,
                Int::class.javaPrimitiveType,
                Bundle::class.java
            )
            hook(method).intercept(StartActivityForResultHooker())
        }.onFailure {
            log(Log.ERROR, TAG, "Failed to hook startActivityForResult", it)
        }.onSuccess {
            log(Log.INFO, TAG, "FS: hooked startActivityForResult")
        }
    }

    private inner class StartActivityForResultHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            runCatching {
                val intent = chain.getArg(0) as? Intent
                if (intent != null) {
                    process(intent, "")?.let { newIntent ->
                        val args = chain.args.toMutableList()
                        args[0] = newIntent
                        return chain.proceed(args.toTypedArray())
                    }
                }
            }.onFailure {
                log(Log.ERROR, TAG, "Error in startActivityForResult hook", it)
            }
            return chain.proceed()
        }
    }

    private inner class StartActivityAsUserHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            runCatching {
                val callingPackage = chain.getArg(1) as? String ?: ""
                val intent = chain.getArg(3) as? Intent
                if (intent != null) {
                    process(intent, callingPackage)?.let { newIntent ->
                        val args = chain.args.toMutableList()
                        args[3] = newIntent
                        return chain.proceed(args.toTypedArray())
                    }
                }
            }.onFailure {
                log(Log.ERROR, TAG, "Error in startActivityAsUser hook", it)
            }
            return chain.proceed()
        }
    }

    private inner class StartActivityIntentSenderHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            runCatching {
                val key = getField(chain.getArg(1), "key")
                val intent = getField(key, "requestIntent") as? Intent
                val callingPackage = getField(key, "packageName") as? String ?: ""
                if (intent != null) {
                    process(intent, callingPackage)?.let { newIntent ->
                        setField(key, "requestIntent", newIntent)
                    }
                }
            }.onFailure {
                log(Log.ERROR, TAG, "Error in startActivityIntentSender hook", it)
            }
            return chain.proceed()
        }
    }

    private inner class PendingIntentRecordSendInnerHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            runCatching {
                val record = chain.thisObject
                val key = getField(record, "key")

                // type 2 is ActivityManager.INTENT_SENDER_ACTIVITY
                val type = getField(key, "type") as? Int ?: return chain.proceed()
                if (type != 2) return chain.proceed()

                val intent = getField(key, "requestIntent") as? Intent ?: return chain.proceed()
                val callingPackage = getField(key, "packageName") as? String ?: ""

                process(intent, callingPackage)?.let {
                    setField(key, "requestIntent", it)
                }
            }.onFailure {
                log(Log.ERROR, TAG, "Error in PendingIntentRecord.sendInner hook", it)
            }
            return chain.proceed()
        }
    }

    private fun process(intent: Intent, callingPackage: String): Intent? {
        if (callingPackage == FUCK_SHARE_PACKAGE_NAME || intent.action !in hookedIntents) {
            return null
        }

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
            log(Log.INFO, TAG, "FS: hooked from $callingPackage, intent: $intent, to: $this")
        }
    }

    private fun actionHookEnabled(action: String?): Boolean {
        return actionHookEnableMap.getOrDefault(action) { false }.invoke()
    }

    private val actionHookEnableMap = mapOf(
        Intent.ACTION_SEND to { settings.enableForceForwardHook },
        Intent.ACTION_SEND_MULTIPLE to { settings.enableForceForwardHook },
        Intent.ACTION_PICK to { settings.enableForcePickerHook },
        Intent.ACTION_GET_CONTENT to { settings.enableForceContentHook },
        Intent.ACTION_OPEN_DOCUMENT to { settings.enableForceDocumentHook }
    )

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

    companion object {
        private const val TAG = "FuckShareExt"
        private const val FUCK_SHARE_PACKAGE_NAME = "org.lyaaz.fuckshare"
        private const val HANDLE_SHARE_ACTIVITY_NAME = "$FUCK_SHARE_PACKAGE_NAME.HandleShareActivity"
        private const val CONTENT_PROXY_ACTIVITY = "$FUCK_SHARE_PACKAGE_NAME.ContentProxyActivity"

        private val hookedIntents = setOf(
            Intent.ACTION_CHOOSER,
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE,
            Intent.ACTION_PICK,
            Intent.ACTION_GET_CONTENT,
            Intent.ACTION_OPEN_DOCUMENT
        )
        private val actionClassMap = mapOf(
            Intent.ACTION_SEND to HANDLE_SHARE_ACTIVITY_NAME,
            Intent.ACTION_SEND_MULTIPLE to HANDLE_SHARE_ACTIVITY_NAME,
            Intent.ACTION_PICK to CONTENT_PROXY_ACTIVITY,
            Intent.ACTION_GET_CONTENT to CONTENT_PROXY_ACTIVITY,
            Intent.ACTION_OPEN_DOCUMENT to CONTENT_PROXY_ACTIVITY
        )

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

        private fun getField(obj: Any?, fieldName: String): Any? {
            if (obj == null) return null
            var clazz: Class<*>? = obj.javaClass
            while (clazz != null) {
                val current = clazz
                runCatching {
                    val field: Field = current.getDeclaredField(fieldName)
                    field.isAccessible = true
                    return field.get(obj)
                }
                clazz = clazz.superclass
            }
            throw NoSuchFieldException("Field $fieldName not found in ${obj.javaClass}")
        }

        private fun setField(obj: Any?, fieldName: String, value: Any?) {
            if (obj == null) return
            var clazz: Class<*>? = obj.javaClass
            while (clazz != null) {
                val current = clazz
                runCatching {
                    val field: Field = current.getDeclaredField(fieldName)
                    field.isAccessible = true
                    field.set(obj, value)
                    return
                }
                clazz = clazz.superclass
            }
            throw NoSuchFieldException("Field $fieldName not found in ${obj.javaClass}")
        }
    }
}
