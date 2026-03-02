import java.io.FileInputStream
import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.google.devtools.ksp")
}

android {
  namespace = "com.vipo.recorder"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.vipo.recorder"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "1.0.0"

    // Used for instrumentation tests if you expand later
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val keystorePropertiesFile = rootProject.file("keystore.properties")
  val keystoreProperties = Properties()
  val releaseSigningConfig = if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { fis ->
      keystoreProperties.load(fis)
    }
    signingConfigs.create("release") {
      storeFile = file(keystoreProperties.getProperty("storeFile"))
      storePassword = keystoreProperties.getProperty("storePassword")
      keyAlias = keystoreProperties.getProperty("keyAlias")
      keyPassword = keystoreProperties.getProperty("keyPassword")
    }
  } else {
    null
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      if (releaseSigningConfig != null) {
        signingConfig = releaseSigningConfig
      }
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
    debug {
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

  buildFeatures {
    viewBinding = true
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("com.google.android.material:material:1.12.0")

  // Lifecycle
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

  // Room
  implementation("androidx.room:room-runtime:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")
  ksp("androidx.room:room-compiler:2.6.1")

  // Media3 ExoPlayer (playlist playback for segments)
  implementation("androidx.media3:media3-exoplayer:1.4.1")
  implementation("androidx.media3:media3-ui:1.4.1")

  // Coroutines
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
