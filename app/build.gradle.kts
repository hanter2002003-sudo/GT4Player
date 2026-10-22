plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gt4.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gt4.player"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"

        // محركات PS2 الحديثة تعمل على 64 بت فقط
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
