// Archivo de construcción raíz: declara los plugins que usan los módulos del proyecto.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    // Procesa google-services.json y configura Firebase en tiempo de compilación.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
