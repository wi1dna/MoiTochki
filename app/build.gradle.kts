plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.example.moitochki"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.moitochki"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "2.0"

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
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Room Database
    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    // Для работы с XML (парсинг KMZ/KML/GPX)
    implementation("org.simpleframework:simple-xml:2.7.1")
    // Для скачивания файлов
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // OSM Maps
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    // Permissions
    implementation("com.guolindev.permissionx:permissionx:1.8.1")
    // Lifecycle
    implementation(libs.androidx.activity.ktx)
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    // UI
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    
    // Testing
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.robolectric:robolectric:4.14")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}