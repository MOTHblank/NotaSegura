plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.ksp)
}

    android {
    namespace = "com.mothblank.notasegura"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mothblank.notasegura"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.ui) // Ferramentas de UI (Box, Column, Row, etc.)
    implementation(libs.ui.graphics) // Classes de Gráficos, Cor, Pincel
    implementation(libs.ui.tooling.preview) // Para previews no Android Studio
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.google.mlkit.text.recognition)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.work.manager.ktx)

    // --- Dependências de Arquitetura ---
    // Navegação entre telas com Compose
    implementation(libs.androidx.navigation.compose)
    // ViewModel para lógica de UI
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // --- Coil para carregamento de imagens ---
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}