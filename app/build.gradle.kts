import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version numbers live in version.properties (single source of truth, also read by the GitHub workflow).
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

// Signing: on GitHub the values come from encrypted secrets (environment variables);
// on this PC from keystore.properties, which is git-ignored.
val localSigning = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, key: String): String? = System.getenv(env) ?: localSigning.getProperty(key)

android {
    namespace = "com.tzipdvir.haklatot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tzipdvir.haklatot"
        minSdk = 29
        targetSdk = 35
        versionCode = versionProps.getProperty("VERSION_CODE").trim().toInt()
        versionName = versionProps.getProperty("VERSION_NAME").trim()
        ndk {
            // Samsung Galaxy A26 and all current phones are 64-bit ARM
            abiFilters += "arm64-v8a"
        }
    }

    // LAME MP3 encoder, compiled from source (app/src/main/cpp)
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    val storePath = signingValue("HAKLATOT_KEYSTORE", "storeFile")
    signingConfigs {
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = signingValue("HAKLATOT_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("HAKLATOT_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("HAKLATOT_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
