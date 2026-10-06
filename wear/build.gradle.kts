/**
 * Plan-B for Wear OS (Plan-B Pro #35): today's tasks and habit check-ins, synced with the phone
 * app through the Wearable Data Layer. It uses the phone app's application id (required by the
 * Data Layer) and is distributed separately; the Cafe Bazaar packaging ships only :app.
 */
plugins {
    alias(libs.plugins.planb.android.application)
}

android {
    namespace = "com.behnamjalali.planb.wear"

    defaultConfig {
        applicationId = "com.behnamjalali.planb"
        // Wear OS 3+ (Compose for Wear OS).
        minSdk = 30
        // Same application id as the phone app: the watch build keeps its own version-code range
        // (1,000,000 + the phone's versionCode) so the two never collide in a store.
        versionCode = 1_000_003
        versionName = "1.1.0"
    }

    androidResources {
        localeFilters += listOf("fa", "en")
    }

    buildTypes {
        debug {
            // Must match the phone app's debug id, or the two never see each other.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Signed like the phone app by whoever distributes it (same key is required).
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
}
