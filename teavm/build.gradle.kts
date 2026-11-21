import org.teavm.gradle.api.OptimizationLevel

plugins {
    id("com.github.xpenatan.gdx-teavm") version "1.6.1"
}

val gdxVersion: String by project

dependencies {
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation(project(":core"))
}

// ./gradlew :teavm:gdx_teavm_web_js_run   -> build + serveur local sur http://localhost:8080
// ./gradlew :teavm:gdx_teavm_web_js_build -> sortie dans teavm/build/dist/js/webapp (déployée sur GitHub Pages)
gdxTeaVM {
    assets(rootProject.file("assets"))

    webDefaults {
        mainClass = "autobattler.teavm.WebLauncher"
        htmlTitle = "Pokémon Auto Battler"
        htmlWidth = 1000
        htmlHeight = 690
    }

    js {
        optimization = OptimizationLevel.BALANCED
    }
}
