plugins {
    alias(libs.plugins.planb.android.library)
}

android {
    namespace = "com.behnamjalali.planb.core.testing"
}

dependencies {
    api(libs.androidx.lifecycle.viewModelCompose)
    api(projects.core.model)
    api(projects.core.common)
    api(projects.core.data)
    api(projects.core.database)
    api(projects.core.datastore)
    api(libs.androidx.room.runtime)
    api(libs.androidx.dataStore.preferences)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.truth)
    api(libs.turbine)
    api(libs.androidx.test.core)
}
