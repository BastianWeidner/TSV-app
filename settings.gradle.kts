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

rootProject.name = "tsv-app"

include(":app")
include(":core:module-api")
include(":core:abgleich")
include(":core:scanner")

include(":features:schwimmkurs:logic")
include(":features:schwimmkurs:ui")
include(":features:schwimmgruppen:logic")
include(":features:schwimmgruppen:ui")
include(":features:schwimmleitung:logic")
include(":features:schwimmleitung:ui")
include(":features:vereinsmeisterschaft:logic")
include(":features:vereinsmeisterschaft:ui")
