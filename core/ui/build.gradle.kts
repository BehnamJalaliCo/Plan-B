plugins {
    alias(libs.plugins.planb.android.library.compose)
}

android {
    namespace = "com.behnamjalali.planb.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.datetime)
    api(projects.core.model)
    api(projects.core.common)
    implementation(libs.androidx.core.ktx)
}
