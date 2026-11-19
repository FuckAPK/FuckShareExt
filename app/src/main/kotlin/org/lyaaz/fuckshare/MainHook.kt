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

        // Hook the per-send output, not sendInner's input or the shared PendingIntent key.
        // AOSP 16/17 use a boolean tail; 14/15 use BackgroundStartPrivileges.
        runCatching {
            val controller = classLoader.loadClass("com.android.server.wm.ActivityStartController")
            val intType = Int::class.javaPrimitiveType!!
            val boolType = Boolean::class.javaPrimitiveType!!
            val optionsType = classLoader.loadClass("com.android.server.wm.SafeActivityOptions")
            val recordType = classLoader.loadClass("com.android.server.am.PendingIntentRecord")
            val singlePrefix = arrayOf(
                intType, intType, intType, String::class.java, String::class.java,
                Intent::class.java, String::class.java, IBinder::class.java, String::class.java,
                intType, intType, optionsType, intType,
                classLoader.loadClass("com.android.server.wm.Task"), String::class.java,
                boolType, recordType
            )
            val multiplePrefix = arrayOf(
                intType, intType, intType, String::class.java, String::class.java,
                Array<Intent>::class.java, Array<String>::class.java, IBinder::class.java,
                optionsType, intType, boolType, recordType
            )
            for ((name, prefix, multiple) in listOf(
                Triple("startActivityInPackage", singlePrefix, false),
                Triple("startActivitiesInPackage", multiplePrefix, true)
            )) {
                runCatching {
                    val method = runCatching {
                        controller.getDeclaredMethod(name, *prefix, boolType)
                    }.getOrElse {
                        controller.getDeclaredMethod(
                            name, *prefix, classLoader.loadClass("android.app.BackgroundStartPrivileges")
                        )
                    }
                    check(method.returnType == intType) { "Unexpected return type: $method" }
                    hook(method).intercept(PendingIntentActivityStartHooker(multiple))
                }.onFailure {
                    log(Log.ERROR, TAG, "Unsupported $name signature; leaving original path unchanged", it)
                }.onSuccess {
                    log(Log.INFO, TAG, "FS: hooked ActivityStartController.$name")
                }
            }
        }.onFailure {
            log(Log.ERROR, TAG, "PendingIntent forwarding unavailable; leaving original path unchanged", it)
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
            val args = runCatching {
                val callingPackage = ""
                val intent = chain.getArg(0) as? Intent
                intent?.let { process(it, callingPackage) }?.let { newIntent ->
                    chain.args.toTypedArray().also { it[0] = newIntent }
                }
            }.onFailure {
                log(Log.ERROR, TAG, "Error in startActivityForResult hook", it)
            }.getOrNull()
            return if (args == null) chain.proceed() else chain.proceed(args)
        }
    }

    private inner class StartActivityAsUserHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val args = runCatching {
                val callingPackage = chain.getArg(1) as? String ?: ""
                val intent = chain.getArg(3) as? Intent
                intent?.let { process(it, callingPackage) }?.let { newIntent ->
                    chain.args.toTypedArray().also { it[3] = newIntent }
                }
            }.onFailure {
                log(Log.ERROR, TAG, "Error in startActivityAsUser hook", it)
            }.getOrNull()
            return if (args == null) chain.proceed() else chain.proceed(args)
        }
    }

    private inner class PendingIntentActivityStartHooker(private val multiple: Boolean) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val args = runCatching {
                // This argument is set by sendInner only after cancellation, one-shot, immutable
                // and fill-in handling. Do not alter non-PendingIntent package starts.
                if (chain.getArg(if (multiple) 11 else 16) == null) return@runCatching null
                val callingPackage = chain.getArg(3) as? String
                    ?: error("Missing PendingIntent creator package")
                if (multiple) {
                    val intents = chain.getArg(5) as? Array<*> ?: error("Missing intents")
                    val types = chain.getArg(6) as? Array<*> ?: error("Missing resolved types")
                    check(intents.isNotEmpty() && intents.size == types.size) {
                        "Unexpected PendingIntent activity stack"
                    }
                    check(intents.all { it is Intent } && types.all { it == null || it is String }) {
                        "Unexpected PendingIntent activity stack elements"
                    }
                    val last = intents.last() as Intent
                    process(last, callingPackage)?.let { forwarded ->
                        val newIntents = Array(intents.size) { intents[it] as Intent }
                        val newTypes = Array(types.size) { types[it] as String? }
                        newIntents[newIntents.lastIndex] = forwarded
                        newTypes[newTypes.lastIndex] = forwarded.type
                            ?: if (last.action == Intent.ACTION_CHOOSER) null else types.last() as String?
                        chain.args.toTypedArray().also {
                            it[5] = newIntents
                            it[6] = newTypes
                        }
                    }
                } else {
                    val intent = chain.getArg(5) as? Intent ?: error("Missing final Intent")
                    process(intent, callingPackage)?.let { forwarded ->
                        chain.args.toTypedArray().also {
                            it[5] = forwarded
                            it[6] = forwarded.type
                                ?: if (intent.action == Intent.ACTION_CHOOSER) null else chain.getArg(6)
                        }
                    }
                }
            }.onFailure {
                log(Log.ERROR, TAG, "Cannot forward PendingIntent activity; leaving original path unchanged", it)
            }.getOrNull()
            return if (args == null) chain.proceed() else chain.proceed(args)
        }
    }

    private fun process(intent: Intent, callingPackage: String): Intent? {
        // Never remove a descriptor-bearing chooser wrapper before the framework can reject it.
        if (intent.hasFileDescriptors()) return null
        if (callingPackage == FUCK_SHARE_PACKAGE_NAME || intent.action !in hookedIntents) {
            return null
        }

        if (!settings.enableHook) return null
        val rules = settings.excludePackages
        if (callingPackage in rules) return null
        val extraIntent = retrieveExtraIntent(Intent(intent)) ?: return null
        if (excludeRuleMatch(rules, callingPackage, extraIntent.type)) {
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
            )?.let { Intent(it) }?.apply {
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
            val lowerMimeType = mimeType?.lowercase()
            return rules.any { rule ->
                val colonIndex = rule.indexOf(':')
                val targetPkg = if (colonIndex == -1) rule else rule.substring(0, colonIndex)
                val targetMime = if (colonIndex == -1) "*" else rule.substring(colonIndex + 1).lowercase()

                val pkgMatch = targetPkg.isBlank() || targetPkg == "*" || targetPkg == packageName
                if (!pkgMatch) return@any false

                val mimePattern = if (targetMime.isBlank()) "*" else targetMime
                mimePattern == "*" || mimePattern == "*/*" || mimePattern == lowerMimeType ||
                        (mimePattern.endsWith("/*") && lowerMimeType?.startsWith(mimePattern.removeSuffix("*")) == true)
            }
        }

    }
}
