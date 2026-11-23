plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val appVersionName = providers.gradleProperty("versionName").getOrElse("0.0.0-dev")
val appVersionCode = providers.gradleProperty("versionCode").getOrElse("1").toInt()

val envKeystorePath = System.getenv("KEYSTORE_PATH")
val envStorePassword = System.getenv("KEYSTORE_PASSWORD")
val envKeyAlias = System.getenv("KEY_ALIAS")
val envKeyPassword = System.getenv("KEY_PASSWORD")
val hasReleaseSigning = listOf(envKeystorePath, envStorePassword, envKeyAlias, envKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "io.github.vsh147.ifsee"
    compileSdk = 35

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(envKeystorePath!!)
                storePassword = envStorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.vsh147.ifsee"
        minSdk = 30
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    lint {
        // The manifest removes a library provider via tools:node="remove" on purpose,
        // so its class is not on the classpath; Lint reports a false MissingClass error.
        disable += "MissingClass"
    }

    buildFeatures {
        viewBinding = true
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
