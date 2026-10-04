plugins {
    alias(libs.plugins.planb.android.library)
    alias(libs.plugins.planb.hilt)
}

android {
    namespace = "com.behnamjalali.planb.core.notifications"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.common)
    implementation(projects.core.datetime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
