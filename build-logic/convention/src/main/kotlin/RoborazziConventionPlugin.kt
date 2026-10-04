import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Screenshot testing with Roborazzi on Robolectric (JVM, no emulator needed). */
class RoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")
        dependencies {
            add("testImplementation", libs.lib("robolectric"))
            add("testImplementation", libs.lib("roborazzi"))
            add("testImplementation", libs.lib("roborazzi-compose"))
            add("testImplementation", libs.lib("roborazzi-junit-rule"))
            add("testImplementation", libs.lib("androidx-compose-ui-test"))
            add("debugImplementation", libs.lib("androidx-compose-ui-testManifest"))
        }
    }
}
