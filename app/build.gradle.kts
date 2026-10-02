plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "dev.djabari.uniremote"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.djabari.uniremote"
        minSdk = 28              // BluetoothHidDevice is API 28+. Hard floor.
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signing is configured only when the credentials are present, so a checkout without
    // the keystore (CI, or a fresh clone) still builds an unsigned release instead of failing.
    val releaseStoreFile = providers.gradleProperty("UNIREMOTE_STORE_FILE").orNull?.let(::file)
    signingConfigs {
        if (releaseStoreFile?.exists() == true) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = providers.gradleProperty("UNIREMOTE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("UNIREMOTE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("UNIREMOTE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }

    testOptions {
        unitTests {
            // Robolectric + Roborazzi screenshot tests need the merged resources/manifest.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:model"))
    implementation(project(":data:session"))
    implementation(project(":feature:pairing"))
    implementation(project(":feature:remote"))
    implementation(project(":feature:touchpad"))
    implementation(project(":feature:keyboard"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.compose.ui.test.manifest)

    // Screenshot-test scaffolding constructs the PairingViewModel dependencies directly.
    testImplementation(project(":transport:network"))

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(project(":transport:network"))
}
