import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.strawmanmobile.chat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.strawmanmobile.chat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
