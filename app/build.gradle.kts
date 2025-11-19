plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.calmiapp"

    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.calmiapp"
        minSdk = 24
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // -------- Android Core ----------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // -------- Compose BOM ----------
    implementation(platform(libs.androidx.compose.bom))

    // -------- Compose UI ----------
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // -------- Material 3 ----------
    implementation(libs.androidx.compose.material3)

    // -------- Material2 Icons (REQUIRED) ----------
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // -------- Navigation Compose ----------
    implementation("androidx.navigation:navigation-compose:2.7.3")

    // -------- ViewModel Compose ----------
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // -------- Accompanist Permissions ----------
    implementation("com.google.accompanist:accompanist-permissions:0.30.1")

    // -------- Networking ----------
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")

    // -------- Image Loading ----------
    implementation("io.coil-kt:coil-compose:2.4.0")
    implementation(libs.androidx.compose.foundation)

    // -------- Testing ----------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

