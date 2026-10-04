plugins {
    alias(libs.plugins.planb.android.library)
    alias(libs.plugins.planb.hilt)
}

android {
    namespace = "com.behnamjalali.planb.core.datastore"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    api(libs.androidx.dataStore.preferences)

    testImplementation(projects.core.testing)
}
