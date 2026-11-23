plugins {
    `java-library`
}

val gdxVersion = providers.gradleProperty("gdxVersion").get()

dependencies {
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// ./gradlew :core:simulate : un bot joue 300 parties (outil d'équilibrage)
tasks.register<JavaExec>("simulate") {
    group = "application"
    description = "Simule 300 parties avec un bot pour vérifier l'équilibrage."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("autobattler.Sim")
    args("300")
}

// ./gradlew :core:simulateLobby : 200 lobbys de 8 bots (classement du bot naïf, durée des parties)
tasks.register<JavaExec>("simulateLobby") {
    group = "application"
    description = "Simule 200 lobbys de 8 joueurs (le bot naïf contre 7 bots à personnalité)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("autobattler.Sim")
    args("lobby", "200")
}
