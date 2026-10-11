import java.util.UUID

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.jarvis"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.jarvis"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
}

tasks.register("genUUID") {
    doLast {
        val dir = file("src/main/assets/model")
        dir.mkdirs()
        File(dir, "uuid").writeText(UUID.randomUUID().toString())
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn("genUUID") }
