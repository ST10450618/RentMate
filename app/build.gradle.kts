import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rentmate.app"
    compileSdk = 36
    lint {
        warningsAsErrors = false
        // RM-0024: every UI string lives in strings.xml. Compose text is guarded by
        // NoHardcodedUiStringsTest, which lint cannot see.
        // RM-0025: values-af and values-xh must translate every key
        // (also checked by TranslationCompletenessTest).
        error += setOf("HardcodedText", "SetTextI18n", "MissingTranslation", "ExtraTranslation")
    }
    defaultConfig {
        applicationId = "com.rentmate.app"
        minSdk = 26
        targetSdk = 36
        // versionCode must increase with every Play Store upload; versionName is
        // what users see. 1 was the Part 2 prototype; 2 is the Final POE release.
        versionCode = 2
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // WEB_CLIENT_ID and API_BASE_URL are per-developer and per-environment,
        // so they live in the gitignored local.properties rather than in source
        // control. A default keeps the build runnable (e.g. in CI, which only
        // needs the app to compile, not to actually reach the API) even before
        // that file is created locally.
        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localProps.load(localPropsFile.inputStream())
        }
        buildConfigField(
            "String", "WEB_CLIENT_ID",
            "\"${localProps.getProperty("WEB_CLIENT_ID", "")}\""
        )
        buildConfigField(
            "String", "API_BASE_URL",
            "\"${localProps.getProperty("API_BASE_URL", "http://10.0.2.2:5000/")}\""
        )
        buildConfigField(
            "String", "SUPABASE_URL",
            "\"${localProps.getProperty("SUPABASE_URL", "")}\""
        )
        buildConfigField(
            "String", "SUPABASE_ANON_KEY",
            "\"${localProps.getProperty("SUPABASE_ANON_KEY", "")}\""
        )
    }

    signingConfigs {
        // Shared debug keystore, checked into the repo so every machine (and
        // CI) that builds this project signs debug builds with the same
        // certificate. Fixes the SHA-1 registered for Google Sign-In staying
        // valid no matter who builds it.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        // Release signing (RM-0039). The upload keystore and its passwords are
        // never committed: locally they come from the gitignored
        // keystore.properties, in CI from environment variables set from
        // GitHub secrets. Without either, release builds are left unsigned
        // instead of failing, so CI's debug/test jobs still run on forks.
        val keystoreProps = Properties().apply {
            val f = rootProject.file("keystore.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        fun signingValue(prop: String, env: String): String? =
            keystoreProps.getProperty(prop) ?: System.getenv(env)

        val releaseStoreFile = signingValue("storeFile", "RENTMATE_KEYSTORE_PATH")
        if (releaseStoreFile != null && rootProject.file(releaseStoreFile).exists()) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "RENTMATE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "RENTMATE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "RENTMATE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinks, optimises and obfuscates the release build; resource
            // shrinking drops drawables and strings nothing references.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    androidResources {
        // RM-0026: generates the locale config from the values-* folders and adds
        // android:localeConfig, so Android 13+ lists English, Afrikaans and
        // isiXhosa under the app's language in system settings. The language of
        // the default values/ folder is declared in res/resources.properties.
        generateLocaleConfig = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Lets unit tests call android.util.Log (used for logging in view models).
        unitTests.isReturnDefaultValues = true
        // Robolectric needs merged resources/manifest for the Room DAO tests.
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    // Export Room schema JSON so migrations can be verified/reviewed in git.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    // RM-0026: per-app language (AppCompatDelegate.setApplicationLocales) on every API level.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Room: local offline cache (RM-0015)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Google Sign-In via Credential Manager (US-1)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)

    // Supabase: Postgres (via PostgREST) + Auth, replacing Seth's ASP.NET Core
    // API and Azure SQL now that Azure hosting isn't available.
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)
}