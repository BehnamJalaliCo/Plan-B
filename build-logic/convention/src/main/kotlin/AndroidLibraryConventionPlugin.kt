import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            // Resource names are namespaced per module through the R class.
            androidResources.enable = true
        }
        dependencies {
            add("testImplementation", libs.lib("junit4"))
            add("testImplementation", libs.lib("truth"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            add("testImplementation", libs.lib("turbine"))
        }
    }
}
