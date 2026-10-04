plugins {
    alias(libs.plugins.planb.android.application)
    alias(libs.plugins.planb.roborazzi)
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
    implementation(projects.core.ui)
    implementation(projects.core.data)
    implementation(projects.core.notifications)
    implementation(projects.feature.today)
    implementation(projects.feature.tasks)
    implementation(projects.feature.calendar)
    implementation(projects.feature.projects)
    implementation(projects.feature.notebooks)
    implementation(projects.feature.habits)
    implementation(projects.feature.goals)
    implementation(projects.feature.focus)
    implementation(projects.feature.search)
    implementation(projects.feature.templates)
    implementation(projects.feature.review)
    implementation(projects.feature.settings)
    implementation(projects.core.backup)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(projects.core.database)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.test.ext.junit)
}

// Screenshots and end-to-end flows use a frozen Tehran clock; align the JVM zone with it so
// formatting that relies on the system zone is deterministic on every machine.
tasks.withType<Test>().configureEach {
    systemProperty("user.timezone", "Asia/Tehran")
}
