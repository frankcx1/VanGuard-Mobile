plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.vanfieldguide"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.vanfieldguide"
        minSdk = 35
        targetSdk = 36
        versionCode = 9
        versionName = "0.6.1-van-guard"
        testInstrumentationRunner = "com.vanfieldguide.PhotoStoreDeviceTest"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildFeatures { compose = true }
    packaging { jniLibs {
        useLegacyPackaging = true
        excludes += "**/libLiteRtCompilerPlugin_google_tensor.so"
    } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencyLocking { lockAllConfigurations() }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    implementation("com.google.ai.edge.litert:litert-npu-runtime-google-tensor:2.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
}
