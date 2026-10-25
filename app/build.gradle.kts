plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.niconn.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.niconn.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.4"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        buildConfig = true
    }
    testOptions {
        // 让 android.jar 的方法在 JVM 单测中返回默认值而非抛异常
        // （AppLog 会调用 android.util.Log 镜像到 logcat）
        unitTests.isReturnDefaultValues = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    // 应用内语言切换（AppCompatDelegate.setApplicationLocales，minSdk 26 兼容）
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // 需要读取 LensModel / PhotographicSensitivity：android.media.ExifInterface 不支持这些 tag
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
