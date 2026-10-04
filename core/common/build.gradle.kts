plugins {
    alias(libs.plugins.planb.jvm.library)
    alias(libs.plugins.planb.hilt)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
}
