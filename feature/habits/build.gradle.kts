plugins {
    alias(libs.plugins.planb.android.feature)
}

android {
    namespace = "com.behnamjalali.planb.feature.habits"
}

dependencies {
    // Plan-B Pro #27: Health Connect's permission screen and app links.
    implementation(projects.core.health)
    implementation(libs.androidx.activity.compose)
}
