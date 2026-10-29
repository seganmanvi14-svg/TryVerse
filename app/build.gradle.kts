// =====================================================================
// build.gradle.kts = the BUILD SETTINGS of the app.
// Gradle (the build tool) reads this file to know: which Android version we target, which libraries
// to download (camera, ML Kit, Google Maps, Google Pay...), and where our secret keys come from.
// =====================================================================
plugins {
    // This project builds an Android APP
    id("com.android.application")
    // Our code is written in Kotlin
    id("org.jetbrains.kotlin.android")
    // We draw the screens with Jetpack Compose (screens written as Kotlin functions, no XML layouts)
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase is switched on only when the project's google-services.json has been added
// (so the app still builds and runs without it — AI try-on then offers the quick preview).
// *** FIREBASE IS SWITCHED ON HERE (only if the google-services.json file exists) ***
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Keys are read from local.properties (that file is never committed to GitHub):
//   MAPS_API_KEY=...   Google Maps SDK for Android
//   RAZORPAY_KEY=...   optional Razorpay test key id
// Reads one value (e.g. MAPS_API_KEY) from local.properties.
// local.properties stays only on our laptop and is NOT uploaded to GitHub, so our API keys stay secret.
// Returns "" (empty) if the file or the key is missing.
fun localProperty(name: String): String = rootProject.file("local.properties").let { f ->
    if (f.exists()) f.readLines().firstOrNull { it.trim().startsWith("$name=") }?.substringAfter("=")?.trim() ?: ""
    else ""
}

// Settings for the Android app
android {
    // The package name of our code (all our Kotlin files start with package com.example.styletryon)
    namespace = "com.example.styletryon"
    // Build with the Android 15 (API 35) tools
    compileSdk = 35

    // Basic facts about the app
    defaultConfig {
        // The unique ID of the app on a phone / Play Store
        applicationId = "com.example.styletryon"
        // Oldest Android version the app runs on: Android 8.0 (API 26)
        minSdk = 26
        // The Android version the app is designed and tested for
        targetSdk = 35
        // Version number for updates (must go up every release) and the version name users see
        versionCode = 12
        versionName = "3.0"
        // Copies the optional Razorpay TEST key from local.properties into the code as BuildConfig.RAZORPAY_KEY
        buildConfigField("String", "RAZORPAY_KEY", "\"${localProperty("RAZORPAY_KEY")}\"")
        // *** GOOGLE MAPS KEY: put into AndroidManifest.xml as ${MAPS_API_KEY}, read from local.properties ***
        manifestPlaceholders["MAPS_API_KEY"] = localProperty("MAPS_API_KEY")
    }

    // Our Kotlin files sit in a folder called " java" (with a space) in the repo, so we tell Gradle to compile that folder too
    sourceSets {
        getByName("main") {
            java.srcDir("src/main/ java")
        }
    }

    // Settings for the final (release) version of the app
    buildTypes {
        release {
            // Shrink and obfuscate the code so the app is much harder to read or modify
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Java and Kotlin version used to compile the code
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    // compose = use Jetpack Compose. buildConfig = create the BuildConfig class (holds RAZORPAY_KEY)
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Skips duplicate licence files from libraries so the APK builds without errors
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "/META-INF/LGPL2.1"
    }
}

// LIBRARIES the app uses (Gradle downloads them from the internet when we build)
dependencies {
    // BOM = one version number for all Compose libraries, so they always match each other
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    // Android basics: Kotlin helpers, Compose inside an Activity, lifecycle
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // Jetpack Compose: drawing UI, Material 3 design (buttons, cards, dialogs), icons
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // Extra icon set (camera, cart, map pin...)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime-saveable")

    // Live camera
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // ML Kit: body joints (pose) and the person's outline (segmentation)
    // Pose detection finds 33 body points (shoulders, hips, ankles...) in a photo or camera frame
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    // Selfie segmentation finds which pixels are the person (their outline), used to measure body width
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    // Lets us wait for Google / ML Kit results with coroutines (background work that doesn't freeze the screen)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Firebase: Gemini AI try-on (Firebase AI Logic) protected by App Check
    // Firebase BOM keeps all Firebase libraries on matching versions
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")

    // Online payment (test mode): Google Pay API, or Razorpay Standard Checkout when a test key is set
    // *** GOOGLE PAY LIBRARY: shows Google's payment sheet (we use its TEST mode, no real money) ***
    implementation("com.google.android.gms:play-services-wallet:19.4.0")
    // Razorpay library (only used if a Razorpay test key is added to local.properties)
    implementation("com.razorpay:checkout:1.6.40")

    // Maps: Google Maps SDK for Android + Maps Compose (delivery address picker)
    // *** GOOGLE MAPS LIBRARY: shows the map in the address picker ***
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    // Maps Compose: lets us put the Google map inside a Compose screen
    implementation("com.google.maps.android:maps-compose:6.2.1")

    // Only in debug builds: tools that let Android Studio preview our screens
    debugImplementation("androidx.compose.ui:ui-tooling")
}
