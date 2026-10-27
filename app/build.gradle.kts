import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.autodj"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.autodj"
        // Jamendo API client id (free at devportal.jamendo.com), from local.properties: jamendo.clientId=…
        val localProps = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "JAMENDO_CLIENT_ID", "\"${localProps.getProperty("jamendo.clientId", "")}\"")
        // Google Cast receiver app id (Cast developer console), from local.properties: cast.appId=…
        // Empty = Google's Default Media Receiver: the mix plays on the TV, without visualizers.
        buildConfigField("String", "CAST_APP_ID", "\"${localProps.getProperty("cast.appId", "")}\"")
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
    }

    // Release key from keystore.properties (kept out of git; see docs/RELEASE_SIGNING.md).
    // Without it, release builds are signed with the local debug key, for personal installs.
    val keystoreProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    // Two apps from one codebase: FireTheDJ for phones, and FireTheDJ TV (its own package, so both
    // install side by side) with a remote-driven home screen and an auto-rotating visualizer.
    flavorDimensions += "device"
    productFlavors {
        create("phone") { dimension = "device" }
        create("tv") {
            dimension = "device"
            applicationIdSuffix = ".tv"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Casting: Google Cast (Chromecast, Google TV) and the routes picker.
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    // Media library callbacks (Android Auto's browse tree) answer with ListenableFutures.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")
    // YouTube Music: playlist listing, and the signature / `n` solving that makes stream URLs playable.
    // v0.26.3, not v0.26.4: the newer one fails to parse the current player JS's deobfuscation function.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")

    // TV only: draws the UPI code for Support, to scan with a phone (Apache-2.0).
    "tvImplementation"("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    // The real org.json for JVM tests (android.jar only has stubs).
    testImplementation("org.json:json:20240303")
}
