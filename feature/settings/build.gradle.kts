plugins {
    alias(libs.plugins.planb.android.feature)
}

android {
    namespace = "com.behnamjalali.planb.feature.settings"
}

dependencies {
    implementation(projects.core.backup)
    implementation(projects.core.billing)
    implementation(libs.androidx.appcompat)
}
