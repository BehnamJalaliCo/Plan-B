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
    api(libs.androidx.compose.material.iconsExtended)
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
}
