plugins {
    alias(libs.plugins.planb.android.feature)
}

android {
    namespace = "com.behnamjalali.planb.feature.settings"
}

dependencies {
    implementation(projects.core.backup)
    implementation(libs.androidx.appcompat)
}
