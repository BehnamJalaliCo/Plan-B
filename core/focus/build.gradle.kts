plugins {
    alias(libs.plugins.planb.android.library)
    alias(libs.plugins.planb.hilt)
}

android {
    namespace = "com.behnamjalali.planb.core.focus"
}

/**
 * Focus Pro (Plan-B Pro #26): ambient sounds generated on the device (no audio files), played by
 * a media-playback foreground service while a session runs, and strict mode's Do Not Disturb.
 */
dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
