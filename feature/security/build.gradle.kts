plugins {
    alias(libs.plugins.planb.android.feature)
}

android {
    namespace = "com.behnamjalali.planb.feature.security"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
