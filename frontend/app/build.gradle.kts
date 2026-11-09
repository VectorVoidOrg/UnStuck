plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ufo.fraudobserver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ufo.fraudobserver"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    flavorDimensions += "version"
    productFlavors {
        create("prod") {
            dimension = "version"
            manifestPlaceholders["appLabel"] = "UFO"
            buildConfigField("boolean", "IS_DEMO", "false")
            buildConfigField("String", "DEFAULT_BASE_URL", "\"https://temporary-snappy-maple-v2pe7o5-n4c5fhzfe-anon-delta-flame.vercel.app/\"")
            buildConfigField("String", "GATEWAY_BASE_URL", "\"https://temporary-snappy-maple-v2pe7o5-n4c5fhzfe-anon-delta-flame.vercel.app/\"")
            buildConfigField("String", "UNSTUCK_BASE_URL", "\"https://temporary-rapid-walnut-8b8urq6-erhjg3k0f-anon-mu-indol.vercel.app/\"")
        }
        create("demo") {
            dimension = "version"
            applicationIdSuffix = ".demo"
            manifestPlaceholders["appLabel"] = "UFO Demo"
            buildConfigField("boolean", "IS_DEMO", "true")
            buildConfigField("String", "DEFAULT_BASE_URL", "\"https://temporary-snappy-maple-v2pe7o5-n4c5fhzfe-anon-delta-flame.vercel.app/\"")
            buildConfigField("String", "GATEWAY_BASE_URL", "\"https://temporary-snappy-maple-v2pe7o5-n4c5fhzfe-anon-delta-flame.vercel.app/\"")
            buildConfigField("String", "UNSTUCK_BASE_URL", "\"https://temporary-rapid-walnut-8b8urq6-erhjg3k0f-anon-mu-indol.vercel.app/\"")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Credentials & Google Auth
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Play Billing
    implementation(libs.billing.ktx)

    // DataStore & Tink Security
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.ui.tooling)
}

