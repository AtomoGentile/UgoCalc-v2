import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

android {
    namespace = "com.ugocalc.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ugocalc.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "4.5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable += "RemoveWorkManagerInitializer"
        // Avoid AGP 8.7.3 + Kotlin 2.0.21 K2-UAST FIR crash in lintAnalyzeDebugUnitTest
        // while keeping full Android Lint checks on main sources.
        ignoreTestSources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
        }
        // Required for 16KB page-size alignment on Android 15+ devices.
        // Without this, the linker packs .so files with 4KB alignment inside the APK,
        // causing installation failure on kernels with 16KB pages.
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Cartella dove Room esporta lo schema del database ad ogni build (utile per verificare le
// migrazioni manuali in Migrations.kt): senza questo, ksp avvisa che exportSchema non può
// scrivere da nessuna parte.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // CameraX Pro Studio (pure Kotlin/Java, nessuna .so nativa — 16KB page-size safe):
    // preview in-app con livella 60fps, telemetria ottica Camera2 (distanza, FoV, AF/OIS, ISO/otturatore)
    // e fallback opzionale alla fotocamera di sistema.
    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ExifInterface: rete di sicurezza per normalizzare l'orientamento del JPEG restituito e leggere i tag EXIF.
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    // File parsing: Office formats (doc/docx/xlsx)
    implementation("org.apache.poi:poi:5.5.1")
    implementation("org.apache.poi:poi-ooxml:5.5.1") {
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
    }
    implementation("org.apache.poi:poi-scratchpad:5.5.1")
    implementation("org.apache.xmlbeans:xmlbeans:5.3.0")
    // PDF text extraction
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Security (encrypted storage for API keys)
    implementation("androidx.security:security-crypto:1.1.0")

    // AppCompat (necessario per BiometricPrompt — FragmentActivity)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Biometric authentication (approvazione inviti)
    implementation("androidx.biometric:biometric:1.1.0")

    // Firma JWT RS256 per il flusso OAuth2 "JWT Bearer" dei service account Google Cloud
    // (autenticazione Vertex AI per Gemini, vedi VertexAiTokenProvider). Libreria pura Java,
    // nessuna dipendenza pesante aggiuntiva: usa java.security nativo per la firma RSA.
    implementation("com.nimbusds:nimbus-jose-jwt:10.9.1")

    // DataStore for simple preferences (versione aggiornata: libdatastore_shared_counter.so
    // nelle build meno recenti non è allineata a 16KB)
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Scanner codici a barre (§ richiesta utente): UI di scansione pronta fornita da Google Play
    // Services, niente CameraX da integrare a mano — coerente con la scelta già fatta per lo
    // scatto foto di delegare alla fotocamera di sistema invece di una preview custom.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // ML Kit Text Recognition — variante Play Services (unbundled): nessuna .so nell'APK,
    // risolve la non-compatibilità 16KB di libmlkit_google_ocr_pipeline.so presente nella
    // variante bundled (com.google.mlkit:text-recognition). Il codice nativo vive in GMS.
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-chinese:16.0.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-devanagari:16.0.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-japanese:16.0.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-korean:16.0.1")

    // Coroutines integration with Google Play Services Tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // WorkManager (background notification when app in background)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")


    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
