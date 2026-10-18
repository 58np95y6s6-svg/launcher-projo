plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Numéro de version : incrémenté automatiquement par GitHub Actions (numéro du build).
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val nomVersion = System.getenv("VERSION_NAME") ?: "0.1-dev"

android {
    namespace = "fr.projo.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.projo.launcher"
        minSdk = 24
        targetSdk = 35
        versionCode = buildNumber
        versionName = nomVersion
    }

    signingConfigs {
        // Clé de TEST (publique, dans le dépôt) : permet d'installer les versions de test
        // les unes par-dessus les autres. La vraie clé de publication arrive à l'étape 6.
        create("test") {
            storeFile = rootProject.file("signature/debug-test.keystore")
            storePassword = "android"
            keyAlias = "test"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("test")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("test")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette)
}
