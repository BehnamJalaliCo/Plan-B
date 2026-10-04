import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

internal fun Project.configureCompose() {
    dependencies {
        val bom = libs.lib("androidx-compose-bom")
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))
        add("testImplementation", platform(bom))
        add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
        add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
    }
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        val metricsEnabled = providers.gradleProperty("planb.composeCompilerReports").isPresent
        if (metricsEnabled) {
            reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
            metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
        }
        stabilityConfigurationFiles.add(
            rootProject.layout.projectDirectory.file("config/compose/stability.conf"),
        )
    }
}
