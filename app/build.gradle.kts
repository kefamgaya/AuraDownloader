@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.FilterConfiguration
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.room)
    alias(libs.plugins.ktfmt.gradle)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")

// Disable ABI splits for single AAB build (Play Store handles optimization automatically)
val splitApks = false

val baseVersionName = currentVersion.name

android {
    compileSdk = 36

    if (keystorePropertiesFile.exists()) {
        val keystoreProperties = Properties()
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
        signingConfigs {
            create("githubPublish") {
                keyAlias = keystoreProperties["keyAlias"].toString()
                keyPassword = keystoreProperties["keyPassword"].toString()
                storeFile = file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"].toString()
            }
        }
    }

    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "gain.aura"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Include all ABIs for universal builds
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    // Bundle configuration for Play Store optimization
    bundle {
        language {
            enableSplit = true
                }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true  // Split by ABI - Play Store will deliver only device's ABI
        }
    }

    room { schemaDirectory("$projectDir/schemas") }
    ksp { arg("room.incremental", "true") }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("githubPublish")
            }
        }
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("githubPublish")
            }
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Aura Downloader Debug")
        }
    }

    flavorDimensions += "publishChannel"

    productFlavors {
        create("generic") {
            dimension = "publishChannel"
            isDefault = true
        }

        create("githubPreview") {
            dimension = "publishChannel"
            applicationIdSuffix = ".preview"
            resValue("string", "app_name", "Aura Downloader Preview")
        }

        create("fdroid") {
            dimension = "publishChannel"
            versionName = "$baseVersionName-(F-Droid)"
        }
    }

    lint { disable.addAll(listOf("MissingTranslation", "ExtraTranslation", "MissingQuantity")) }

    // Output naming - Customize APK file names
    // Note: AAB files use default naming (app-{flavor}-{buildType}.aab)
    // You can rename AAB files manually after build if needed
    applicationVariants.all {
        val variant = this
        val buildTypeName = variant.buildType.name
        val flavorName = variant.flavorName
        
        // Rename APK files
        variant.outputs.all {
            if (this is com.android.build.gradle.internal.api.ApkVariantOutputImpl) {
                if (flavorName == "generic" && buildTypeName == "release") {
                    outputFileName = "aura-${defaultConfig.versionName}-release.apk"
                } else {
                    outputFileName = "aura-${defaultConfig.versionName}-${flavorName}-${buildTypeName}.apk"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions { 
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs.useLegacyPackaging = true
    }
    androidResources { generateLocaleConfig = true }

    namespace = "gain.aura"
}

ktfmt { kotlinLangStyle() }

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":color"))

    implementation(libs.bundles.core)

    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.androidxCompose)
    implementation(libs.bundles.accompanist)

    implementation(libs.coil.kt.compose)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)

    implementation(libs.bundles.youtubedlAndroid)

    implementation(libs.mmkv)

    // AdMob
    implementation("com.google.android.gms:play-services-ads:23.5.0")
    
    // Play Billing Library (latest version with latest APIs)
    implementation("com.android.billingclient:billing-ktx:8.2.1")
    
    // Play Core for In-App Updates (native Google Play update dialog)
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.compose.ui.tooling)
}

// --- 16KB memory page size support (Android 15+) ---
// Google Play checks native ELF PT_LOAD alignment (p_align). Many third-party prebuilt .so ship with 4KB (0x1000)
// alignment and fail the 16KB page-size requirement. As a mitigation, we patch merged native libraries *inside*
// the AGP merge task, so downstream tasks (strip/package/bundle) observe the patched outputs.
tasks.matching { it.name == "mergeGenericReleaseNativeLibs" }.configureEach {
    doLast {
        val libDir =
            layout.buildDirectory
                .dir(
                    "intermediates/merged_native_libs/genericRelease/mergeGenericReleaseNativeLibs/out/lib"
                )
                .get()
                .asFile

        if (!libDir.exists()) return@doLast

        fileTree(libDir).matching { include("**/*.so") }.files.forEach { so ->
            // Some dependencies ship helper files with a ".so" suffix (e.g. "*.zip.so") that are NOT ELF.
            // Only patch real ELF binaries.
            val header = runCatching { so.inputStream().use { it.readNBytes(4) } }.getOrNull() ?: return@forEach
            val isElf =
                header.size == 4 &&
                    header[0] == 0x7F.toByte() &&
                    header[1] == 'E'.code.toByte() &&
                    header[2] == 'L'.code.toByte() &&
                    header[3] == 'F'.code.toByte()
            if (!isElf) return@forEach

            runCatching {
                    project.exec {
                        commandLine("patchelf", "--page-size", "16384", so.absolutePath)
                    }
                }
                .onFailure { e ->
                    throw GradleException("patchelf failed for ${so.absolutePath}", e)
                }
        }
    }
}
