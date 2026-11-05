plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.raqam.fast"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.raqam.fast"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        val key = (project.findProperty("OCR_API_KEY") ?: "").toString()
        buildConfigField("String", "OCR_API_KEY", "\"$key\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.7.0")
}
