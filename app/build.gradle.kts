plugins {
    id("fuck.android.application")
    id("fuck.compose")
    id("fuck.xposed.modern")
}

android {
    namespace = "org.lyaaz.fuckshare"
    defaultConfig {
        applicationId = "org.lyaaz.fuckshare.ext"
        minSdk = 30
    }
    androidResources {
        generateLocaleConfig = true
        localeFilters.add("zh-rCN")
    }
    buildTypes {
        debug {
            // Must stay `org.lyaaz.fuckshare.ext`: FuckShare looks this package up.
            applicationIdSuffix = null
        }
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(libs.material)
}
