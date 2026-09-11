plugins { id("com.android.application") }
android {
    namespace = "com.example.qrscanner"; compileSdk = 37
    defaultConfig {
        applicationId = "com.example.qrscanner"; minSdk = 24; targetSdk = 34; versionCode =
        1; versionName = "1.0"
    }
    buildFeatures { viewBinding = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17
    }
    // Configure Kotlin compiler options using the new compilerOptions DSL (sets JVM target to 17)
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
}
