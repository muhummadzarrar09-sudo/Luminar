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

rootProject.name = "Recto"

// Phase 0 is deliberately a single module. Once this compiles green on your
// machine we split into :core:* and :feature:* as planned in BRAINSTORM.md
// section 4.2. Getting a verified baseline first is worth more than a tidy
// module graph that has never built.
include(":app")
