import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Baca konfigurasi dari local.properties -> BuildConfig (tidak di-hardcode di repo)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(key: String, default: String = "") = (localProps.getProperty(key) ?: default).trim()

android {
    namespace = "id.keuanganku.app"
    compileSdk = 34

    signingConfigs {
        create("release") {
            // Keystore dibaca dari local.properties (tidak di-commit ke git).
            // Jika tidak dikonfigurasi, build release memakai debug signing.
            val ksFile = prop("keystore.file")
            if (ksFile.isNotBlank() && rootProject.file(ksFile).exists()) {
                storeFile = rootProject.file(ksFile)
                storePassword = prop("keystore.password")
                keyAlias = prop("key.alias")
                keyPassword = prop("key.password")
            }
        }
    }

    defaultConfig {
        applicationId = "id.keuanganku.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 20
        versionName = "1.1.18"

        buildConfigField("String", "SUPABASE_URL", "\"${prop("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${prop("supabase.anonKey")}\"")
        buildConfigField("String", "NINEROUTER_URL", "\"${prop("ninerouter.url", "http://43.106.119.94:20128/v1")}\"")
        buildConfigField("String", "NINEROUTER_KEY", "\"${prop("ninerouter.key")}\"")
        buildConfigField("String", "NINEROUTER_MODEL", "\"${prop("ninerouter.model")}\"")
        // URL file version.json di GitHub (untuk cek pembaruan aplikasi).
        // Isi di local.properties: update.url=https://raw.githubusercontent.com/USER/REPO/main/version.json
        buildConfigField("String", "UPDATE_URL", "\"${prop("update.url")}\"")
        // Token GitHub (opsional, wajib jika repo private) untuk cek & unduh pembaruan.
        buildConfigField("String", "UPDATE_TOKEN", "\"${prop("update.token")}\"")

        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            // Pakai keystore release jika dikonfigurasi di local.properties
            val ksFile = prop("keystore.file")
            if (ksFile.isNotBlank() && rootProject.file(ksFile).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // --- AndroidX core ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // --- Compose ---
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // --- CameraX ---
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")

    // --- Network: Retrofit + OkHttp + kotlinx.serialization ---
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Gambar ---
    implementation("io.coil-kt:coil-compose:2.6.0")

    // --- Database lokal (cache offline + antrean) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- OCR struk (on-device, gratis) ---
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
