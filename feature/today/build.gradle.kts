plugins {
    alias(libs.plugins.planb.android.feature)
}

android {
    namespace = "com.behnamjalali.planb.feature.today"
}

dependencies {
    // Plan-B Pro #1: on-device natural-language quick add.
    implementation(projects.core.nlp)
    // Plan-B Pro #40: Persian voice input in Quick Capture.
    implementation(projects.core.speech)
}
