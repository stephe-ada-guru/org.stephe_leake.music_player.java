// libs.* are defined in ../gradle/libs.versions.toml
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp) 
}

android {
    namespace = "org.stephe_leake.music_player_2"
    compileSdk = 34 // Use the latest STABLE SDK as of Oct 2025. 

    defaultConfig {
        applicationId = "org.stephe_leake.music_player_2"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_9
        targetCompatibility = JavaVersion.VERSION_1_9
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_9.toString()
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
   // libs.* are defined in ../gradle/libs.versions.toml
   
    implementation(platform(libs.androidx.compose.bom))

    // misc
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.android.material)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    // AndroidX Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.appcompat) 
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx) 
    implementation(libs.androidx.preference.ktx)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation(libs.androidx.viewpager2)

    // Room Database - Using KSP
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // Media3
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)// session and controller    
    implementation(libs.androidx.media3.ui)

    // more misc
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.jetbrains.kotlinx.coroutines.android)
    implementation(libs.jetbrains.kotlinx.coroutines.core)

    // For listenableFutures.await
    implementation(libs.guava)
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.jetbrains.kotlinx.coroutines.guava)
     
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Non-google
    // https://commons.apache.org/io/
    implementation("commons-io:commons-io:2.20.0")

    //https://square.github.io/okhttp/#releases
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")

    implementation ("com.github.bumptech.glide:glide:4.16.0") // loads images into viewpager2
}
