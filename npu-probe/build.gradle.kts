plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.vanfieldguide.npuprobe"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.vanfieldguide.npuprobe"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-hardware-probe"
        ndk { abiFilters += "arm64-v8a" }
    }
    packaging { jniLibs {
        useLegacyPackaging = true
        // Tensor model is already compiled. Do not attempt restricted on-device compilation.
        excludes += "**/libLiteRtCompilerPlugin_google_tensor.so"
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencyLocking { lockAllConfigurations() }
dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    implementation("com.google.ai.edge.litert:litert-npu-runtime-google-tensor:2.3.0")
}
