import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
  if (keystorePropsFile.isFile) {
    keystorePropsFile.inputStream().use(::load)
  }
}

fun signingProp(propKey: String, envKey: String): String? =
  keystoreProps.getProperty(propKey)?.takeIf { it.isNotBlank() }
    ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }

val releaseKeystorePath = signingProp("storeFile", "TIMEEDIT_KEYSTORE_PATH")
val releaseKeystoreFile = releaseKeystorePath?.let { rootProject.file(it).takeIf { f -> f.isFile } ?: file(it).takeIf { f -> f.isFile } }
val releaseStorePassword = signingProp("storePassword", "TIMEEDIT_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingProp("keyAlias", "TIMEEDIT_KEY_ALIAS")
val releaseKeyPassword = signingProp("keyPassword", "TIMEEDIT_KEY_PASSWORD")
val hasReleaseSigning = releaseKeystoreFile != null &&
  !releaseStorePassword.isNullOrBlank() &&
  !releaseKeyAlias.isNullOrBlank() &&
  !releaseKeyPassword.isNullOrBlank()

android {
  namespace = "com.timeedit.camera"
  compileSdk = 36
  defaultConfig {
    applicationId = "com.timeedit.camera"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "1.0.0"
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = releaseKeystoreFile
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose & Icons
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)

  // CameraX + Compose Viewfinder + Video + Effects
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.compose)
  implementation(libs.androidx.camera.video)
  implementation(libs.androidx.camera.effects)

  // Metadata, Persistence & Serialization
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.exifinterface)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.osmdroid.android)

  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Local Unit Tests (JUnit4 + Google Truth + Coroutines)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
