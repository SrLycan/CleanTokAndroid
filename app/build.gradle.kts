import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Servidor fijo de la app: el backend en AWS (IP estática 44.215.16.137). El login entra directo ahí.
// Para probar contra otro backend al compilar:  ./gradlew assembleDebug -Pcleantok.servidor=http://10.0.2.2:8080
val servidor = (findProperty("cleantok.servidor") as String?) ?: "https://44-215-16-137.sslip.io"

// Firma del APK que se comparte con el equipo: keystore.properties (no se versiona). Sin él, se firma con la clave de debug.
val firma = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use(::load) } }

android {
    namespace = "ec.cleantok.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ec.cleantok.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4-demo"
        buildConfigField("String", "SERVIDOR", "\"$servidor\"")
    }

    signingConfigs {
        if (firma != null) create("equipo") {
            storeFile = rootProject.file(firma.getProperty("storeFile"))
            storePassword = firma.getProperty("storePassword")
            keyAlias = firma.getProperty("keyAlias")
            keyPassword = firma.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("equipo") ?: signingConfigs.getByName("debug")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.12.4")
    // Escáner de QR (Apache 2.0)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // Mapa con OpenStreetMap (Apache 2.0), sin API key
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
