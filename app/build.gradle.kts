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
        versionCode = 2
        versionName = "3.0.2"

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

    // Lo scatto foto delega alla fotocamera di sistema (Intent standard, vedi CameraCaptureScreen):
    // niente più CameraX, il produttore del telefono gestisce già meglio zoom/lenti/focus/qualità.
    // ExifInterface: rete di sicurezza per normalizzare l'orientamento del JPEG restituito, nel caso
    // l'app fotocamera di sistema si affidi solo al tag EXIF invece di ruotare fisicamente i pixel.
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

    // ML Kit Text Recognition for OCR (PDF scansionati e immagini)
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // Coroutines integration with Google Play Services Tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // WorkManager (background notification when app in background)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // AWS Amplify for Cognito authentication
    implementation("com.amplifyframework:core:2.13.2")
    implementation("com.amplifyframework:aws-auth-cognito:2.13.2")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
