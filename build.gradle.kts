plugins {
    kotlin("jvm") version "1.9.22"
    application
    id("org.jetbrains.compose") version "1.5.12"
    id("org.sonarqube") version "6.3.1.5724"
}

group = "com.marstech.graphvizviewer"
version = "0.1.0"

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:3.5.2") // CLI
    implementation(compose.desktop.currentOs) // Compose Desktop (BOM-managed)
    implementation("io.ktor:ktor-server-netty:2.3.7") // Embedded web server
    implementation("me.friwi:jcefmaven:122.1.10") // JCEF WebView (Maven Wrapper)
    implementation("org.jetbrains.compose.material3:material3-desktop:1.5.12") // Compose Material3 for Desktop
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // JSON serialization
}

application {
    mainClass.set("com.marstech.graphvizviewer.MainKt")
    applicationDefaultJvmArgs = listOf(
        "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.lwawt=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
        // Also open internal packages for reflection at runtime (helps on newer JDKs on macOS)
        "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED"
    )
}

// Make sure `./gradlew run` uses the same JVM args as the application plugin
tasks.named<JavaExec>("run") {
    jvmArgs = application.applicationDefaultJvmArgs.toList()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
}

sonar {
    properties {
        property("sonar.projectKey", "alkaphreak_MarsTech-Graphviz-Viewer")
        property("sonar.organization", "alkaphreak")
    }
}