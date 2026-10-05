// Speech to text through the device's speech service: live dictation for Plan-B Pro voice input
// (#40) and the recognizer plumbing shared with voice-note transcription (#19). No network of its
// own; whether audio leaves the device is up to the speech service the user installed.
plugins {
    alias(libs.plugins.planb.android.library.compose)
    alias(libs.plugins.planb.hilt)
}

android {
    namespace = "com.behnamjalali.planb.core.speech"
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
