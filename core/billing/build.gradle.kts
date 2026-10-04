plugins {
    alias(libs.plugins.planb.android.library)
    alias(libs.plugins.planb.hilt)
}

/**
 * The Cafe Bazaar RSA public key used to verify purchase signatures on the device. It comes
 * from the environment or a Gradle property named PLANB_BAZAAR_RSA_KEY (a CI secret, or
 * ~/.gradle/gradle.properties), never from git. Without it (open-source builds) billing reports
 * "not configured" and the purchase screen says so. Only Base64 characters are kept, so the
 * value cannot break out of the generated Java string.
 */
val bazaarRsaKey: String = (
    System.getenv("PLANB_BAZAAR_RSA_KEY")?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("PLANB_BAZAAR_RSA_KEY").orNull
        ?: ""
    ).filter { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }

android {
    namespace = "com.behnamjalali.planb.core.billing"
    buildFeatures.buildConfig = true
    defaultConfig {
        buildConfigField("String", "BAZAAR_RSA_KEY", "\"$bazaarRsaKey\"")
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.poolakey)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.dataStore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
}
