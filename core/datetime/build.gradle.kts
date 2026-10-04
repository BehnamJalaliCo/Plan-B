plugins {
    alias(libs.plugins.planb.android.library)
}

android {
    namespace = "com.behnamjalali.planb.core.datetime"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
