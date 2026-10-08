import android.content.FakePreferences
import org.lyaaz.fuckshare.Settings as ExtSettings

fun main() {
    val rules = FakePreferences()
    val settings = ExtSettings.getInstance(rules)
    check(settings.excludePackages == ExtSettings.DEFAULT_EXCLUDE_PACKAGES)
    rules.values[ExtSettings.PREF_EXCLUDE_PACKAGES] = "one, two\nthree"
    val first = settings.excludePackages
    check(first == setOf("one", "two", "three"))
    check(settings.excludePackages === first)
    rules.values[ExtSettings.PREF_EXCLUDE_PACKAGES] = "new"
    check(settings.excludePackages == setOf("new"))
    rules.values.remove(ExtSettings.PREF_EXCLUDE_PACKAGES)
    check(settings.excludePackages == ExtSettings.DEFAULT_EXCLUDE_PACKAGES)
    check(ExtSettings.getInstance(FakePreferences()).excludePackages == ExtSettings.DEFAULT_EXCLUDE_PACKAGES)
    val rebound = FakePreferences().apply { values[ExtSettings.PREF_EXCLUDE_PACKAGES] = "rebound" }
    check(ExtSettings.getInstance(rebound).excludePackages == setOf("rebound"))
    println("Settings regressions: rebinding; rule cache reuse and invalidation")
}
