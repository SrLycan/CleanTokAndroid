import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Servidor que trae la app al instalarla (se puede cambiar en el login):
//   ./gradlew assembleRelease -Pcleantok.servidor=https://1-2-3-4.sslip.io
val servidor = (findProperty("cleantok.servidor") as String?) ?: "http://10.0.2.2:8080"

// Firma del APK que se comparte con el equipo: keystore.properties (no se versiona). Sin él, se firma con la clave de debug.
val firma = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use(::load) } }

android {
    namespace = "ec.cleantok.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ec.cleantok.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2-demo"
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
