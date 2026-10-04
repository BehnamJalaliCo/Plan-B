// An Android library (not plain JVM) so Android Lint checks every API against minSdk.
plugins {
    alias(libs.plugins.planb.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.behnamjalali.planb.core.model"
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
