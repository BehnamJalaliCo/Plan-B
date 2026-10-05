// On-device natural-language quick add (Plan-B Pro #1). Pure Kotlin rules, no network.
// An Android library (not plain JVM) so Android Lint checks every API against minSdk.
plugins {
    alias(libs.plugins.planb.android.library)
}

android {
    namespace = "com.behnamjalali.planb.core.nlp"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.datetime)
    // The Jalali engine uses Android ICU, which runs on the JVM under Robolectric.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
