plugins {
    alias(libs.plugins.planb.android.library)
    alias(libs.plugins.planb.hilt)
}

android {
    namespace = "com.behnamjalali.planb.core.health"
}

/**
 * Health Connect (Plan-B Pro #27): read-only daily totals for habits that check themselves off.
 * Health Connect works on Android 9+ (as an app until Android 13, built into Android 14+);
 * everywhere else the app reports it as unavailable and nothing is read.
 */
dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.data)
    api(libs.androidx.health.connect.client)
    implementation(libs.kotlinx.coroutines.android)
}
