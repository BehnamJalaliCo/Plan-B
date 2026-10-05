plugins {
    alias(libs.plugins.planb.android.feature)
}

android {
    namespace = "com.behnamjalali.planb.feature.tasks"
}

dependencies {
    // Plan-B Pro #40: dictating a task title.
    implementation(projects.core.speech)
}
