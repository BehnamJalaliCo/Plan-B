// An Android library (not plain JVM) so Android Lint checks every API against minSdk:
// shared code such as the clock runs on Android 8.0+.
plugins {
    alias(libs.plugins.planb.android.library)
    alias(libs.plugins.planb.hilt)
}

android {
    namespace = "com.behnamjalali.planb.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
}
