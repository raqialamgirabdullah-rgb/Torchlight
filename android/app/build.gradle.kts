plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rahcare.torchlight"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rahcare.torchlight"
        minSdk = 26
        targetSdk = 34
        versionCode = (findProperty("vc") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0"
    }

    // একই কী দিয়ে সাইন করা হয়, তাই নতুন সংস্করণ আগেরটার ওপরেই ইনস্টল হয় (ডেটা না হারিয়ে)
    signingConfigs {
        create("fixed") {
            storeFile = file("torchlight.keystore")
            storePassword = "torchlight"
            keyAlias = "torchlight"
            keyPassword = "torchlight"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
