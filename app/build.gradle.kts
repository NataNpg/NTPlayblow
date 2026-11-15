import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Chave de release fica FORA do repositório: ../keystore/keystore.properties (storeFile, storePassword, keyAlias, keyPassword)
val ksFile = rootProject.file("../keystore/keystore.properties")

android {
    signingConfigs {
        create("release") {
            if (ksFile.exists()) {
                val p = Properties().apply { ksFile.inputStream().use { load(it) } }
                storeFile = file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            if (ksFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
    namespace = "com.ntplayblow.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ntplayblow.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("com.google.zxing:core:3.5.3")
}
