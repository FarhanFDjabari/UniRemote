pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "uni-remote"

include(":app")

include(":core:model")
include(":core:ui")
include(":core:common")

include(":transport:api")
include(":transport:bthid")
include(":transport:network")

include(":data:session")

include(":feature:pairing")
include(":feature:remote")
include(":feature:touchpad")
include(":feature:keyboard")
