plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Same Firebase project as the guardian app. Without google-services.json
// this app still builds and shows its real "not connected" state.
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.rockstar.orderguardian.approver"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rockstar.orderguardian.approver"
        // Lower minSdk to 26 (Android 8.0) to support older Android devices while keeping all features
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
    }

    signingConfigs {
        named("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = "orderguardian"
            keyAlias = "orderguardian"
            keyPassword = "orderguardian"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        buildConfig = true
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
    implementation(project(":shared"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-auth")
}
