plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.wifipriority"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wifipriority"
        minSdk = 33
        targetSdk = 36
        versionCode = 5
        versionName = "0.3.2"
    }

    flavorDimensions += "stack"
    productFlavors {
        create("mock") {
            dimension = "stack"
            isDefault = true
            buildConfigField("boolean", "USE_MOCK_WIFI", "true")
        }
        create("real") {
            dimension = "stack"
            buildConfigField("boolean", "USE_MOCK_WIFI", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Flavors split the variant test tasks. Keep the stage-1 task name working:
// `testDebugUnitTest` runs both the mock and real debug unit tests.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs mockDebug and realDebug unit tests."
    dependsOn("testMockDebugUnitTest", "testRealDebugUnitTest")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
