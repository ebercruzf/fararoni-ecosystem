plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.2"
    kotlin("jvm") version "1.9.20"
    id("com.diffplug.spotless") version "6.25.0"
}

group = "dev.fararoni"
version = "1.0.0"

repositories {
    mavenCentral()
}

// Forzar uso de JDK 21 (compatible con plugin Gradle IntelliJ)
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Java 17 target compatibility (requerido por IntelliJ Platform)
// Se puede compilar con JDK 21 pero el target debe ser 17

dependencies {
    // Cliente HTTP Tactico (Robusto y rapido)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Manejo de JSON seguro
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

// Configuracion del entorno de destino (Target Platform)
intellij {
    version.set("2023.3.4") // Version base estable de IntelliJ IDEA Community
    type.set("IC") // 'IC' = IntelliJ Community, 'IU' = Ultimate

    // Plugins base necesarios (Java es mandatorio para PSI de codigo)
    plugins.set(listOf("com.intellij.java"))
}

// Spotless: Licencia automatica en archivos Java
spotless {
    java {
        target("src/**/*.java")
        licenseHeaderFile("LICENSE_HEADER.txt")
    }
}

tasks {
    // Configurar compatibilidad con Java 17 (requerido por IntelliJ Platform)
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    patchPluginXml {
        sinceBuild.set("233") // Compatible desde la version 2023.3
        untilBuild.set("253.*") // Compatible hasta la version 2025.1
    }

    signPlugin {
        certificateChain.set("")
        privateKey.set("")
        password.set("")
    }

    publishPlugin {
        token.set("")
    }
}
