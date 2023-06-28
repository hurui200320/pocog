plugins {
    kotlin("jvm") version "1.8.10"
    application
    id("me.champeau.jmh") version "0.6.8"
}

group = "info.skyblond"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://jogamp.org/deployment/maven/") }
}

val dl4jUseGPU = true

dependencies {
    implementation("org.jogamp.jocl:jocl-main:2.4.0")
    implementation("org.jogamp.gluegen:gluegen-rt-main:2.4.0")
    implementation("org.bytedeco:javacv-platform:1.5.8")
    implementation("ch.qos.logback:logback-classic:1.4.6")

    @Suppress("VulnerableLibrariesLocal")
    implementation("org.deeplearning4j:deeplearning4j-core:1.0.0-M2.1")
    if (dl4jUseGPU){
        implementation("org.nd4j:nd4j-cuda-11.6-platform:1.0.0-M2.1")
        implementation("org.bytedeco:cuda-platform:11.8-8.6-1.5.8")
        implementation("org.bytedeco:cuda-platform-redist:11.8-8.6-1.5.8")
    } else{
        implementation("org.nd4j:nd4j-native-platform:1.0.0-M2.1")
    }

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
