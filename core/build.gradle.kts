plugins {
    `java-library`
}

val gdxVersion = providers.gradleProperty("gdxVersion").get()

dependencies {
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
}

// ./gradlew :core:simulate : un bot joue 300 parties (outil d'équilibrage)
tasks.register<JavaExec>("simulate") {
    group = "application"
    description = "Simule 300 parties avec un bot pour vérifier l'équilibrage."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("autobattler.Sim")
    args("300")
}
