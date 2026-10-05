plugins {
    alias(libs.plugins.planb.android.feature)
}

android {
    namespace = "com.behnamjalali.planb.feature.journal"
}

dependencies {
    // Plan-B Pro #30: comparing the mood with sleep asks Health Connect for sleep in context.
    implementation(projects.core.health)
    implementation(libs.androidx.activity.compose)
}
