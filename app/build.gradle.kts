plugins {
    alias(libs.plugins.planb.android.application)
    alias(libs.plugins.planb.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.behnamjalali.planb"

    defaultConfig {
        applicationId = "com.behnamjalali.planb"
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        localeFilters += listOf("fa", "en")
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.datetime)
    implementation(projects.core.designsystem)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
}
