package org.lyaaz.fuckshare

import android.content.SharedPreferences

class Settings private constructor(private val prefs: SharedPreferences) {

    private fun String.toSet(): Set<String> {
        return this.takeIf { it.isNotBlank() }
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: setOf()
    }
    val enableHook: Boolean
        get() {
            return prefs.getBoolean(PREF_ENABLE_HOOK, DEFAULT_ENABLE_HOOK)
        }
    private var cachedRulesText: String? = null
    private var cachedRules: Set<String> = DEFAULT_EXCLUDE_PACKAGES

    val excludePackages: Set<String>
        @Synchronized get() {
            val text = prefs.getString(PREF_EXCLUDE_PACKAGES, null)
            if (text != cachedRulesText) {
                cachedRules = text?.toSet() ?: DEFAULT_EXCLUDE_PACKAGES
                cachedRulesText = text
            }
            return cachedRules
        }
    val enableForceForwardHook: Boolean
        get() {
            return prefs.getBoolean(
                PREF_ENABLE_FORCE_FORWARD_HOOK,
                DEFAULT_ENABLE_FORCE_FORWARD_HOOK
            )
        }
    val enableForceContentHook: Boolean
        get() {
            return prefs.getBoolean(
                PREF_ENABLE_FORCE_CONTENT_HOOK,
                DEFAULT_ENABLE_FORCE_CONTENT_HOOK
            )
        }
    val enableForceDocumentHook: Boolean
        get() {
            return prefs.getBoolean(
                PREF_ENABLE_FORCE_DOCUMENT_HOOK,
                DEFAULT_ENABLE_FORCE_DOCUMENT_HOOK
            )
        }
    val enableForcePickerHook: Boolean
        get() {
            return prefs.getBoolean(PREF_ENABLE_FORCE_PICKER_HOOK, DEFAULT_ENABLE_FORCE_PICKER_HOOK)
        }

    companion object {
        private val SEPARATOR = "[,\\s]+".toRegex()
        const val PREF_ENABLE_HOOK = "enable_hook"
        const val PREF_EXCLUDE_PACKAGES = "exclude_packages"
        const val PREF_ENABLE_FORCE_FORWARD_HOOK = "enable_force_forward_hook"
        const val PREF_ENABLE_FORCE_CONTENT_HOOK = "enable_force_content_hook"
        const val PREF_ENABLE_FORCE_DOCUMENT_HOOK = "enable_force_document_hook"
        const val PREF_ENABLE_FORCE_PICKER_HOOK = "enable_force_picker_hook"

        const val DEFAULT_ENABLE_HOOK = false
        val DEFAULT_EXCLUDE_PACKAGES = setOf(
            "com.android.providers.media.module"
        )
        const val DEFAULT_ENABLE_FORCE_FORWARD_HOOK = false
        const val DEFAULT_ENABLE_FORCE_CONTENT_HOOK = false
        const val DEFAULT_ENABLE_FORCE_DOCUMENT_HOOK = false
        const val DEFAULT_ENABLE_FORCE_PICKER_HOOK = false

        fun getInstance(prefs: SharedPreferences): Settings = Settings(prefs)
    }
}
