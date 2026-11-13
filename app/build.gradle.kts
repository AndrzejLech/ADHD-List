plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.composeCompailer)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Sharing needs your Firebase project's config; without it the app builds and sharing stays hidden.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.example.adhdlist"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.adhdlist"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.example.adhdlist.utils.CustomTestRunner"
        // Set firebaseEmulatorHost=10.0.2.2 in ~/.gradle/gradle.properties to run sharing against the local Firebase emulator.
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"${findProperty("firebaseEmulatorHost") ?: ""}\"")
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    androidTestImplementation(libs.hiltTesting)
    ksp(libs.android.room.compiler)
    ksp(libs.hilt.compiler)

    implementation(libs.coroutines)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.room.common)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.android.compose.material.icons.extended)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.gson)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.coroutines.play.services)
    implementation(libs.reorderable)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.hiltTesting)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.test.ui.kakao)
    androidTestImplementation(libs.test.ui.kakao.compose)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
// Espresso brings an old protobuf-lite that shadows Firestore's protobuf-javalite in the test APK.
configurations.named("androidTestImplementation") {
    exclude(group = "com.google.protobuf", module = "protobuf-lite")
}
