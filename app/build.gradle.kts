plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Numéro de version : incrémenté automatiquement par GitHub Actions (numéro du build).
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val nomVersion = System.getenv("VERSION_NAME")?.removePrefix("v") ?: "0.1-dev"

android {
    namespace = "fr.projo.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.projo.launcher"
        minSdk = 22
        targetSdk = 35
        versionCode = buildNumber
        versionName = nomVersion

        // Télécommande smartphone (WebRTC natif) : seulement ARM. Projecteur : armeabi-v7a ou
        // arm64-v8a ; Fire TV Stick Lite (AFTSS) : Fire OS 32 bits, armeabi-v7a. Sans x86 ni
        // x86_64 (émulateurs), ~25 Mo de bibliothèques natives en moins.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        // Clé TMDB du design « Studio » (suggestions) : secret GitHub TMDB_KEY, jamais dans le
        // dépôt ; chaîne vide si absente (la clé peut aussi être saisie dans Réglages)
        val cleTmdb = (System.getenv("TMDB_KEY") ?: "").trim().filter { it.isLetterOrDigit() || it in "._-" }
        buildConfigField("String", "TMDB_KEY", "\"" + cleTmdb + "\"")
        // Règles R8 de la télécommande (fichier séparé : pas de conflit avec les autres branches)
        proguardFile("proguard-telecommande.pro")
    }

    signingConfigs {
        // Clé de PUBLICATION : fournie par GitHub Actions (secrets), jamais dans le dépôt.
        val ks = System.getenv("KEYSTORE_PATH")
        if (ks != null) {
            create("publication") {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Clé de publication si disponible (GitHub Actions), sinon clé de test (essais locaux)
            signingConfig = signingConfigs.findByName("publication") ?: signingConfigs.getByName("test")
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
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Tests d'interface sur la JVM (Robolectric) : ressources Android disponibles
            isIncludeAndroidResources = true
            all {
                // Roborazzi : enregistre les captures d'écran (pas de comparaison)
                it.systemProperty("roborazzi.test.record", "true")
                it.maxHeapSize = "3g"
                it.testLogging {
                    events("passed", "skipped", "failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }
}

dependencies {
    // Télécommande smartphone (voir docs/telecommande.md)
    implementation(libs.stream.webrtc)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
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
    // ADB local (Fire TV) : activation de l'ouverture automatique sans ordinateur
    implementation("dev.mobile:dadb:1.2.10")

    // Tests d'interface (navigation à la télécommande, captures des designs)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
