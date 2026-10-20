import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// PLACES_API_KEY lives in local.properties (never committed). Empty by default:
// the app then runs entirely on the free OSM Overpass provider.
val placesApiKey: String = rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
    val props = Properties()
    FileInputStream(file).use { stream -> props.load(stream) }
    props.getProperty("PLACES_API_KEY") ?: ""
} ?: ""

android {
    namespace = "com.fuelgo.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fuelgo.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PLACES_API_KEY", "\"$placesApiKey\"")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    implementation(libs.work.runtime.ktx)
    implementation(libs.coroutines.android)

    // Firebase is optional at runtime: the app works fully (v1) when no
    // google-services.json is present. See ReportRepository for the guard.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
