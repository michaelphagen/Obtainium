plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.imranr.obtainium.wearhelper"
    compileSdk = 35
    defaultConfig {
        // Must match the phone app's applicationId (and signing key): the
        // Wear OS data layer only connects an app to its own twin.
        applicationId = (project.findProperty("phoneApplicationId") as String?) ?: "dev.imranr.obtainium.wear"
        minSdk = 30
        targetSdk = 34
        versionCode = (project.findProperty("helperVersionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("helperVersionName") as String?) ?: "dev"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
