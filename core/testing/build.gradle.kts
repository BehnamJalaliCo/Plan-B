plugins {
    alias(libs.plugins.planb.android.library)
}

android {
    namespace = "com.behnamjalali.planb.core.testing"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.truth)
    api(libs.turbine)
    api(libs.androidx.test.core)
}
