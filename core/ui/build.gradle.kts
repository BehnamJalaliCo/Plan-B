plugins {
    alias(libs.plugins.planb.android.library.compose)
}

android {
    namespace = "com.behnamjalali.planb.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.datetime)
    api(projects.core.model)
    api(projects.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // App lock and locked notes (Plan-B Pro #36): the device's own lock through BiometricPrompt.
    api(libs.androidx.biometric)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
