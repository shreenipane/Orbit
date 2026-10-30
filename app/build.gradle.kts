plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.shreenipane.orbit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shreenipane.orbit"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "0.9.1"
    }

    // A permanent signing key, if CI provides one (see README). Without it the release build is
    // signed with the build machine's debug key, which changes between CI runs.
    signingConfigs {
        create("release") {
            val ks = System.getenv("ORBIT_KEYSTORE")
            if (!ks.isNullOrBlank() && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("ORBIT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ORBIT_KEY_ALIAS")
                keyPassword = System.getenv("ORBIT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // What CI ships: not debuggable, so app data can't be read over USB with `run-as`.
        release {
            isDebuggable = false
            // R8 shrinking stays off until a build has been checked on a phone; it can break reflection-based code.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val permanent = signingConfigs.getByName("release")
            signingConfig = if (permanent.storeFile != null) permanent else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // Origin-restricted JS → Kotlin messages for the VTOP sign-in page.
    implementation("androidx.webkit:webkit:1.12.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // QR codes for swapping timetables with friends (Apache-2.0).
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
