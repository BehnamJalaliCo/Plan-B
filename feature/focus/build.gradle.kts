plugins {
    alias(libs.plugins.planb.android.feature)
}

android {
    namespace = "com.behnamjalali.planb.feature.focus"
}

dependencies {
    // Focus Pro (#26): ambient sounds and strict mode.
    implementation(projects.core.focus)
}
