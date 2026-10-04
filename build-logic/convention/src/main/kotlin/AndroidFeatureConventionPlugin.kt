import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Feature modules: Compose UI + Hilt ViewModels + shared core dependencies. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("planb.android.library.compose")
        pluginManager.apply("planb.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            add("implementation", project(":core:common"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:datetime"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))
            add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-lifecycle-runtimeCompose"))
            add("implementation", libs.lib("androidx-lifecycle-viewModelCompose"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("kotlinx-serialization-json"))
            add("implementation", libs.lib("androidx-compose-material-iconsExtended"))
            add("testImplementation", project(":core:testing"))
            add("testImplementation", libs.lib("robolectric"))
            add("testImplementation", libs.lib("androidx-compose-ui-test"))
            add("testImplementation", libs.lib("androidx-test-core"))
            add("debugImplementation", libs.lib("androidx-compose-ui-testManifest"))
        }
    }
}
