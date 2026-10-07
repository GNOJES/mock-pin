import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Never commit secrets.properties. Keep the key out of source and build output logs.
val mapsSecrets = Properties().apply {
    val secretsFile = rootProject.file("secrets.properties")
    val source = if (secretsFile.exists()) secretsFile else rootProject.file("secrets.defaults.properties")
    if (source.exists()) source.inputStream().use { load(it) }
}
val mapsApiKey = mapsSecrets.getProperty("MAPS_API_KEY", "DEFAULT_API_KEY").trim()

android {
    namespace = "com.gnojes.mockpin"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.gnojes.mockpin"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"${mapsApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                // Partial package optimization caused an IllegalAccessError on device.
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // SDK 36 / existing Kotlin compatible stable Maps Compose.
    implementation("com.google.maps.android:maps-compose:6.12.2")
    implementation("com.google.android.gms:play-services-location:21.4.0")
    // 6.x removed programmatic fetchPlace; 5.3 retains minimal-field New API details.
    implementation("com.google.android.libraries.places:places:5.3.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
