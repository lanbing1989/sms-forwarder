plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lanbing.smsforwarder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lanbing.smsforwarder"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    // 与下面的 Compose 版本保持一致
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

dependencies {
    // 明确版本，避免 BOM 注入失败引起的问题
    val composeUiVersion = "1.5.4"
    val activityComposeVersion = "1.8.2"
    val material3Version = "1.1.0"
    val lifecycleRuntime = "2.6.2"
    val okhttpVersion = "4.12.0"

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleRuntime")
    implementation("androidx.activity:activity-compose:$activityComposeVersion")

    // Compose UI / graphics / tooling (明确 artifact 名称和版本)
    implementation("androidx.compose.ui:ui:$composeUiVersion")
    implementation("androidx.compose.ui:ui-graphics:$composeUiVersion")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeUiVersion")
    debugImplementation("androidx.compose.ui:ui-tooling:$composeUiVersion")

    implementation("androidx.compose.material3:material3:$material3Version")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
}