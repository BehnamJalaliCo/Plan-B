// Plan-B Pro #39: the AI assistant (chat about your plan, plan my day/week, contextual actions
// in the editors) and its settings. Talks to the provider only through core:ai.
plugins {
    alias(libs.plugins.planb.android.feature)
}

android {
    namespace = "com.behnamjalali.planb.feature.assistant"
}

dependencies {
    implementation(projects.core.ai)
    // Dictating a question (Plan-B Pro #40).
    implementation(projects.core.speech)

    testImplementation(projects.core.database)
    testImplementation(projects.core.datastore)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.dataStore.preferences)
}
