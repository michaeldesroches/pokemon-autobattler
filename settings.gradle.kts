pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://teavm.org/maven/repository/")
    }
}

rootProject.name = "pokemon-autobattler"

include("core", "lwjgl3", "teavm")
