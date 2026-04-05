plugins {
    id("com.android.library")
}

android {
    namespace = "com.qzone.mobile.bridge"
    compileSdk = 33

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
