// Configuration commune aux modules core (jeu), lwjgl3 (desktop) et teavm (web).
subprojects {
    repositories {
        mavenCentral()
        maven("https://teavm.org/maven/repository/")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
}
