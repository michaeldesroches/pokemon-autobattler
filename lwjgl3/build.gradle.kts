plugins {
    application
}

val gdxVersion: String by project

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
}

application {
    mainClass.set("autobattler.lwjgl3.Lwjgl3Launcher")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("assets")
    if (System.getProperty("os.name").lowercase().contains("mac")) jvmArgs("-XstartOnFirstThread")
}
