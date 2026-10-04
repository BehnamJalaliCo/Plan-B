plugins {
    alias(libs.plugins.planb.android.feature)
}

android {
    namespace = "com.behnamjalali.planb.feature.pro"
}

dependencies {
    implementation(projects.core.billing)
    implementation(libs.androidx.activity.compose)
    testImplementation(libs.androidx.dataStore.preferences)
}
