plugins {
    alias(libs.plugins.planb.android.library.compose)
}

android {
    namespace = "com.behnamjalali.planb.core.designsystem"
}

/**
 * The app typeface (Anjoman Max) is licensed and must not be published, so its files are never
 * committed in plain form. They are read from PLANB_FONTS_DIR or <root>/private-fonts (CI decrypts
 * fonts/planb-fonts.tar.gz.gpg there with tools/fonts.sh) and copied into generated resources
 * as R.font.planb_*.
 * Without them (e.g. a fresh public checkout) the open Vazirmatn fallback is used so the project
 * still builds; -Pplanb.requirePrivateFonts=true or PLANB_REQUIRE_PRIVATE_FONTS=true (release and
 * screenshot checks) forbids that.
 */
abstract class PlanBFontsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val fonts: ListProperty<File>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val fontDir = outputDir.get().asFile.resolve("font")
        fontDir.deleteRecursively()
        fontDir.mkdirs()
        WEIGHTS.zip(fonts.get()).forEach { (weight, file) -> file.copyTo(fontDir.resolve("planb_$weight.ttf")) }
    }

    companion object {
        val WEIGHTS = listOf("regular", "medium", "semibold", "bold")
    }
}

val privateFontDir: File = (System.getenv("PLANB_FONTS_DIR")?.takeIf { it.isNotBlank() }?.let(::File))
    ?: rootProject.file("private-fonts")
val privateFonts = listOf("Regular", "Medium", "SemiBold", "Bold").map { privateFontDir.resolve("AnjomanMax-$it.ttf") }
val usePrivateFonts = privateFonts.all { it.isFile }
val requirePrivateFonts = providers.gradleProperty("planb.requirePrivateFonts")
    .orElse(providers.environmentVariable("PLANB_REQUIRE_PRIVATE_FONTS")).orNull == "true"
if (!usePrivateFonts && requirePrivateFonts) {
    throw GradleException(
        "Licensed fonts not found in $privateFontDir (expected AnjomanMax-{Regular,Medium,SemiBold,Bold}.ttf). " +
            "Run tools/fonts.sh decrypt with PLANB_FONTS_PASSPHRASE set; see docs/FONTS.md.",
    )
}
val fontFiles = if (usePrivateFonts) privateFonts else PlanBFontsTask.WEIGHTS.map { file("fonts-fallback/vazirmatn_$it.ttf") }

val generateFonts = tasks.register<PlanBFontsTask>("generatePlanBFonts") {
    fonts.set(fontFiles)
    outputDir.set(layout.buildDirectory.dir("generated/planbFonts/res"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(generateFonts, PlanBFontsTask::outputDir)
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.animation)
    api(libs.androidx.compose.material.iconsExtended)
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
}
