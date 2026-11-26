plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.cornercivilization.p37f776dc"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cornercivilization.p37f776dc"
        minSdk = 35
        targetSdk = 35
        versionCode = providers.gradleProperty("cornerVersionCode").map(String::toInt).getOrElse(2)
        versionName = providers.gradleProperty("cornerVersionName").getOrElse("0.19.1")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters.add("arm64-v8a") }
    }

    val storeSigningVariables = listOf("CORNER_RELEASE_KEYSTORE", "CORNER_RELEASE_STORE_PASSWORD",
        "CORNER_RELEASE_KEY_ALIAS", "CORNER_RELEASE_KEY_PASSWORD")
    val storeSigningValues = storeSigningVariables.associateWith { providers.environmentVariable(it).orNull }
    if (storeSigningValues.values.any { !it.isNullOrBlank() }) {
        require(storeSigningValues.values.all { !it.isNullOrBlank() }) {
            "Incomplete store signing configuration: " +
                storeSigningVariables.filter { storeSigningValues[it].isNullOrBlank() }.joinToString()
        }
        signingConfigs.create("store") {
            storeFile = file(requireNotNull(storeSigningValues["CORNER_RELEASE_KEYSTORE"]))
            storePassword = storeSigningValues["CORNER_RELEASE_STORE_PASSWORD"]
            keyAlias = storeSigningValues["CORNER_RELEASE_KEY_ALIAS"]
            keyPassword = storeSigningValues["CORNER_RELEASE_KEY_PASSWORD"]
        }
    }

    buildTypes {
        // Device acceptance uses the production code and assets in an isolated
        // package, so injected-date fixtures never replace a player's save.
        create("acceptance") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".acceptance"
            matchingFallbacks += listOf("debug")
            resValue("string", "acceptance_build_name", "桌角文明验收")
        }
        create("livingAcceptance") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".living.acceptance"
            matchingFallbacks += listOf("debug")
            resValue("string", "app_name", "桌角文明 远古体验")
            resValue("string", "acceptance_build_name", "桌角文明生活验收")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("store")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Release runtime in the already isolated package; the local test key
        // is for profiling only and is never a store signing credential.
        create("livingPerformance") {
            initWith(getByName("release"))
            applicationIdSuffix = ".living.acceptance"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            resValue("string", "app_name", "桌角文明 V16体验")
        }
    }
    // connectedAndroidTest cleans up its target package. Never target the playable debug app.
    testBuildType = "livingAcceptance"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    androidResources {
        noCompress += listOf("glb", "gltf", "usdz", "bundle")
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation(platform(libs.spatial.bom))
    implementation(libs.spatial.core)
    implementation(libs.spatial.ui.platform)
    implementation(libs.spatial.ui.foundation)
    implementation(libs.spatial.ui.design)
    implementation(libs.spatial.ui.sense)
    implementation(libs.spatial.ui.tracking)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling.preview)
}

configurations.all {
    resolutionStrategy {
        exclude("androidx.compose.ui", "ui")
        exclude("androidx.compose.ui", "ui-graphics")
        exclude("androidx.compose.ui", "ui-text")
        exclude("androidx.compose.foundation", "foundation")
    }
}
