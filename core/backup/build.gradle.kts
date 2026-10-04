plugins {
    alias(libs.plugins.planb.android.library)
    alias(libs.plugins.planb.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.behnamjalali.planb.core.backup"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.data)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
