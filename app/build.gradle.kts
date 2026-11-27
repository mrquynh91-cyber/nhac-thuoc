import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

if (file("google-services.json").exists()) {
  apply(plugin = "com.google.gms.google-services")
}

val localProps = Properties().apply {
  val localFile = rootProject.file("local.properties")
  if (localFile.exists()) {
    localFile.inputStream().use { load(it) }
  }
}

fun readConfig(name: String, defaultValue: String = ""): String {
  return (localProps.getProperty(name) ?: System.getenv(name) ?: defaultValue)
    .trim()
    .replace("\"", "\\\"")
}

val appVersionName = readConfig("APP_VERSION_NAME", "1.0.0")
val appVersionCode = readConfig("APP_VERSION_CODE", "1").toIntOrNull() ?: 1

android {
  namespace = "com.medlens.ai"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.medlens.ai"
    minSdk = 26
    targetSdk = 35
    versionCode = appVersionCode
    versionName = appVersionName

    buildConfigField("String", "GEMINI_API_KEY", "\"${readConfig("GEMINI_API_KEY")}\"")
    buildConfigField("String", "GITHUB_OTA_REPO", "\"${readConfig("GITHUB_OTA_REPO", "mrquynh91-cyber/nhac-thuoc")}\"")
    buildConfigField("String", "GITHUB_OTA_TOKEN", "\"${readConfig("GITHUB_OTA_TOKEN")}\"")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose Material 3
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Image loading
  implementation(libs.coil.compose)

  // Networking & Serialization
  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.play.services)

  // Firebase (Auth + Firestore + Storage)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.storage)

  // Unit tests
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
