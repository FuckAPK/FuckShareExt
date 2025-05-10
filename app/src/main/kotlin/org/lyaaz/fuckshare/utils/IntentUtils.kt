package org.lyaaz.fuckshare.utils

import android.content.Intent
import android.os.Build
import android.os.Parcelable

/**
 * Utility class for working with Intents and extracting URIs from them.
 */
object IntentUtils {

    /**
     * Gets a Parcelable extra from the Intent.
     *
     * @param intent The Intent to retrieve the extra from.
     * @param name The name of the extra.
     * @param clazz The class type of the Parcelable.
     * @return The Parcelable extra or null if not found.
     */
    fun <T : Parcelable?> getParcelableExtra(
        intent: Intent,
        name: String?,
        clazz: Class<T>
    ): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(name, clazz)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(name)
        }
    }

    /**
     * Gets a Parcelable Array extra from the Intent.
     *
     * @param intent The Intent to retrieve the extra from.
     * @param name The name of the extra.
     * @return The Parcelable Array extra or null if not found.
     */
    inline fun <reified T : Parcelable?> getParcelableArrayExtra(
        intent: Intent,
        name: String?
    ): Array<T>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(name, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(name)
        }?.map { it as T }?.toTypedArray()
    }

    /**
     * Backs up a Parcelable array extra from one Intent to another.
     *
     * This function tries to retrieve a Parcelable array extra from the `from` Intent using a key
     * with the specified suffix and then puts it into the `to` Intent using the original key.
     *
     * @param from The source Intent from which to get the Parcelable array extra.
     * @param to The target Intent into which to put the Parcelable array extra.
     * @param key The key used to retrieve and store the Parcelable array extra.
     * @param T The type of Parcelable contained in the array.
     */
    inline fun <reified T : Parcelable> backupArrayExtras(
        from: Intent,
        to: Intent,
        key: String
    ) {
        getParcelableArrayExtra<T>(from, key)?.let {
            to.putExtra("$key$EXTRAS_KEY_SUFFIX", it)
        }
    }

    const val EXTRAS_KEY_SUFFIX = "_FS"
}
