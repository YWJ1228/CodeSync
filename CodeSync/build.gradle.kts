plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "com.ssafy"
version = "1.6"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2024.2.1")
    type.set("IU") // Target IDE Platform

    plugins.set(listOf(/* Plugin Dependencies */))
}

dependencies {
    implementation("org.graalvm.sdk:graal-sdk:23.1.2")
    implementation("org.graalvm.truffle:truffle-api:24.1.1")
    implementation("org.graalvm.js:js:24.1.1")
    implementation("org.graalvm.compiler:compiler:24.1.1")
    implementation("com.github.mwiede:jsch:0.2.20")
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
}

tasks {
    runIde {
        // JCEF 활성화를 위한 JVM 옵션 추가
        jvmArgs = listOf("-Xmx2g", "-Dide.browser.jcef.enabled=true")
    }

    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("242.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
