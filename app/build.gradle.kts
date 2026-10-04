import java.util.Properties

plugins {
    alias(libs.plugins.planb.android.application)
    alias(libs.plugins.planb.roborazzi)
    alias(libs.plugins.planb.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

/**
 * Release signing comes only from the environment (CI secrets decoded to a temporary file)
 * or from an untracked keystore.properties for local builds. Nothing secret lives in git.
 * Without credentials the release build is produced unsigned.
 */
data class ReleaseSigning(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

val releaseSigning: ReleaseSigning? = run {
    val props = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
    }
    fun value(env: String, prop: String): String? =
        (System.getenv(env)?.trim()?.takeIf { it.isNotEmpty() } ?: props.getProperty(prop)?.trim())
            ?.takeIf { it.isNotEmpty() }
    val path = value("PLANB_KEYSTORE_PATH", "storeFile") ?: return@run null
    val file = rootProject.file(path).takeIf { it.isFile } ?: file(path).takeIf { it.isFile } ?: return@run null
    ReleaseSigning(
        storeFile = file,
        storePassword = value("PLANB_KEYSTORE_PASSWORD", "storePassword") ?: return@run null,
        keyAlias = value("PLANB_KEY_ALIAS", "keyAlias") ?: return@run null,
        keyPassword = value("PLANB_KEY_PASSWORD", "keyPassword") ?: return@run null,
    )
}

android {
    namespace = "com.behnamjalali.planb"

    defaultConfig {
        applicationId = "com.behnamjalali.planb"
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "com.behnamjalali.planb.HiltTestRunner"
        // Release builds buy through Cafe Bazaar; debug builds use an in-memory store.
        buildConfigField("boolean", "FAKE_BILLING", "false")
    }

    androidResources {
        localeFilters += listOf("fa", "en")
        generateLocaleConfig = true
    }

    signingConfigs {
        releaseSigning?.let { signing ->
            create("release") {
                storeFile = signing.storeFile
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Pro can be toggled in a hidden developer section (long-press the version in About).
            buildConfigField("boolean", "FAKE_BILLING", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    bundle {
        // The language can be switched inside the app, so every install needs both
        // Persian and English resources rather than only the device language split.
        language { enableSplit = false }
    }

    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }
}

// Benchmark and baseline-profile variants are only installed on test devices; sign them with
// the debug key so they never need production credentials.
android.buildTypes.matching { it.name.startsWith("benchmark") || it.name.startsWith("nonMinified") }.configureEach {
    signingConfig = android.signingConfigs.getByName("debug")
}

baselineProfile {
    // Profiles are generated on a device/emulator by :baselineprofile and committed under
    // src/main/generated/baselineProfiles, so regular builds never need a device.
    automaticGenerationDuringBuild = false
    saveInSrc = true
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
    implementation(projects.core.billing)

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
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.baselineprofile)

    testImplementation(projects.core.testing)
    testImplementation(projects.core.database)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.test.ext.junit)

    androidTestImplementation(projects.core.database)
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.truth)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test)
}

// Screenshots and end-to-end flows use a frozen Tehran clock; align the JVM zone with it so
// formatting that relies on the system zone is deterministic on every machine.
tasks.withType<Test>().configureEach {
    systemProperty("user.timezone", "Asia/Tehran")
}
