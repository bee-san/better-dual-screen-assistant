// Vendored copy of SleepManager (https://github.com/Baggio94/SleepManager), GPL-3.0. See LICENSE and UPSTREAM_COMMIT.
// Built as a library module inside the bee-san fork of Better Dual Screen Assistant.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.med.sleepmanager"
    compileSdk = 35

    defaultConfig {
        minSdk = 33
        buildConfigField("String", "VERSION_NAME", "\"0.8.1-bdsa\"")
        buildConfigField("int", "VERSION_CODE", "571")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Upstream targets Compose 1.11 where these are stable; BDSA pins an older BOM.
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20250517")
}
