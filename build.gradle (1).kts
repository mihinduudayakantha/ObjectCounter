import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.objectcounter"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.objectcounter"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
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
    // Keep the .tflite model uncompressed so it can be memory-mapped
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.1")

    val cameraX = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // On-device object detection (MediaPipe Tasks, TensorFlow Lite under the hood)
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
}

// Downloads the pretrained EfficientDet-Lite0 model (COCO, 80 object classes)
// into src/main/assets the first time you build.
val modelUrl =
    "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite"

tasks.register("downloadModel") {
    val dest = file("src/main/assets/efficientdet_lite0.tflite")
    outputs.file(dest)
    doLast {
        if (!dest.exists() || dest.length() == 0L) {
            dest.parentFile.mkdirs()
            URL(modelUrl).openStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            println("Downloaded model to $dest")
        }
    }
}

tasks.named("preBuild") {
    dependsOn("downloadModel")
}
