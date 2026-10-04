plugins {
    alias(libs.plugins.planb.android.library)
    alias(libs.plugins.planb.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.behnamjalali.planb.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(projects.core.datetime)
    implementation(projects.core.database)
    api(projects.core.datastore)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
