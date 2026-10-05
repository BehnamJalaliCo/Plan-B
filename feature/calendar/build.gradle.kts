plugins {
    alias(libs.plugins.planb.android.feature)
}

android {
    namespace = "com.behnamjalali.planb.feature.calendar"
}

dependencies {
    // Plan-B Pro #3: device calendar sync (read device events, settings).
    implementation(projects.core.calendarsync)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    testImplementation(projects.core.database)
    testImplementation(projects.core.datastore)
}
