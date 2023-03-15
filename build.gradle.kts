plugins {
    kotlin("jvm") version "1.8.0"
    application
    id("me.champeau.jmh") version "0.6.8"
}

group = "info.skyblond"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://jogamp.org/deployment/maven/") }
}

dependencies {
    implementation("org.jogamp.jocl:jocl-main:2.4.0")
    implementation("org.jogamp.gluegen:gluegen-rt-main:2.4.0")
    implementation("org.bytedeco:javacv-platform:1.5.8")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}
