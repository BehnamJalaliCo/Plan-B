plugins {
    alias(libs.plugins.planb.android.library.compose)
}

android {
    namespace = "com.behnamjalali.planb.core.designsystem"
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.core.ktx)
}
