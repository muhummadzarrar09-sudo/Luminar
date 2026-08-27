plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.recto.reader"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.recto.reader"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-phase0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 full mode: shrink code and resources. Phase 0's debug APK is
            // ~28 MB, almost all of it Compose tooling and unstripped classes;
            // the release build should land near a third of that.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Keep only the languages we actually ship strings for, and split by
    // density where it helps. Mostly this trims androidx's bundled translations.
    androidResources {
        localeFilters += listOf("en")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Export Room schemas from day one and keep them in version control. The
// previous project had schema 1, 2 and 9 with the middle versions missing,
// which is how you end up wiping a user's library on upgrade.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// With AGP 9 built-in Kotlin, android.kotlinOptions {} no longer exists.
// Compiler options move to a top-level kotlin {} block.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)

    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)

    // MediaStyle notification for read-aloud. androidx.media, NOT media3:
    // media3 is an ExoPlayer stack we have no use for - there is no media
    // file here, just a TTS engine - and it is far larger.
    implementation(libs.androidx.media)
}
