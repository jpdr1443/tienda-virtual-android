import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

// Las llaves de ePayco y el código de registro de administradores se leen de
// local.properties (no se sube a GitHub) para no exponerlas en el repositorio.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.tiendavirtual.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tiendavirtual.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"

        // Si no hay llave propia se usa la llave de ejemplo de la documentación de ePayco (solo modo pruebas).
        buildConfigField("String", "EPAYCO_PUBLIC_KEY", "\"${localProps.getProperty("epayco.publicKey", "45b960805ced5c27ce34b1600b4b9f54")}\"")
        buildConfigField("boolean", "EPAYCO_TEST", localProps.getProperty("epayco.test", "true"))
        buildConfigField("String", "ADMIN_REGISTRATION_CODE", "\"${localProps.getProperty("admin.code", "TV-ADMIN-2026")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
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
    // AndroidX y Material Design
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Biometría (huella dactilar)
    implementation("androidx.biometric:biometric:1.1.0")

    // Firebase: autenticación y base de datos Firestore (también guarda las imágenes comprimidas)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Lectura de la orientación EXIF de las fotos de cámara
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Corrutinas: permite usar await() sobre las Task de Firebase y Play Services
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Google Play Services: ubicación (Fused Location Provider)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Carga de imágenes (data URI en base64) en ImageView con caché
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
