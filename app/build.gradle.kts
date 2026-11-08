plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.rota.copiloto"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.rota.copiloto"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "2.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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
    // Leitura por imagem (OCR) 100% no aparelho, modelo embutido no app.
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
