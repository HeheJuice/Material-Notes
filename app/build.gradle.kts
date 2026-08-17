plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.HeheJuice.Notes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.HeheJuice.Notes"
        minSdk = 32
        targetSdk = 36
        versionCode = 11
        versionName = "V.1.1 Beta"
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = file("debug.keystore")
            storePassword = System.getenv("RELEASE_SIGNING_PASSWORD") ?: "android"
            keyAlias = System.getenv("RELEASE_SIGNING_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("RELEASE_SIGNING_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}