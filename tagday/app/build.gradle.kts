plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.room)
}

// Records the current Room schema as JSON under `app/schemas`, one file per `version`.
// These are committed deliberately: they're the "before" side a real `Migration` has to be
// written and tested against, and they can't be reconstructed after the fact. Nothing here
// obliges the schema to be final or a migration to exist yet — while the database still uses
// `fallbackToDestructiveMigration`, this is a recording, not a contract. See
// `docs/DATA_MODEL.md` § Schema history & migrations.
room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "dev.krfu.tagday"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.krfu.tagday"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric needs the merged resources/manifest to inflate anything — without this,
        // Compose tests fail at startup rather than at the assertion.
        unitTests.isIncludeAndroidResources = true
    }

    // The Android Lint warning count is zero, so the gate holds it there rather than letting it
    // creep back (BACKLOG F23) — verified by reintroducing `Configuration.screenHeightDp` and
    // confirming the build then fails, rather than assuming the flag does what it says.
    //
    // Scope worth knowing: this covers **Android Lint** only, not Kotlin compiler warnings.
    // Of the two warnings just cleaned up, `Configuration.screenHeightDp` was a lint check and
    // is guarded here; the deprecated `hiltViewModel` import was a `kotlinc` warning and is
    // not. Gating those too means `allWarningsAsErrors` on the Kotlin compiler, which turns
    // every future deprecation in a dependency bump into a build break — a bigger tradeoff,
    // deliberately not taken here.
    //
    // The three disabled checks all report "a newer version of X exists". They fire on their
    // own schedule, with no code change and nothing wrong in the repo, so as errors they'd
    // break an untouched build. Dependency currency is a deliberate decision (`libs.versions.
    // toml` pins coroutines to match the transitive runtime version), not a build failure.
    lint {
        warningsAsErrors = true
        disable += setOf("OldTargetApi", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // collectAsStateWithLifecycle is used directly by every Screen composable, so it's
    // declared rather than relied on transitively.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Compose UI tests run on the JVM under Robolectric rather than on a device — see ADR-040
    // and TESTING.md. Both of these are test-only and never reach the APK.
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}