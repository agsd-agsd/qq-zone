plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.qzone.android"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.qzone.android"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

configurations.configureEach {
    resolutionStrategy.force("androidx.emoji2:emoji2:1.3.0")
    resolutionStrategy.force("androidx.activity:activity:1.7.2")
    resolutionStrategy.force("androidx.activity:activity-ktx:1.7.2")
    resolutionStrategy.force("androidx.activity:activity-compose:1.7.2")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.emoji2:emoji2:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    val gomobileAar = file("libs/qqzone-mobile.aar")
    if (gomobileAar.exists()) {
        implementation(files(gomobileAar))
    } else {
        implementation(project(":mobilebridge-stub"))
    }

    debugImplementation("androidx.compose.ui:ui-tooling")
}
