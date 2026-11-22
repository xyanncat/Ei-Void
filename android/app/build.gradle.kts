import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// `java` is shadowed by the Android plugin's `java` extension inside this
// script, so the class is imported explicitly above.
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { stream -> localProperties.load(stream) }
}

// Clerk is the only sign-in. The Worker in server/ exchanges the Clerk
// session for the GitHub token that JGit needs. Both values are configuration,
// never secrets.
val clerkPublishableKey: String =
    localProperties.getProperty("clerk.publishableKey")
        ?: System.getenv("CLERK_PUBLISHABLE_KEY")
        ?: ""
val eiVoidApiUrl: String =
    localProperties.getProperty("ei-void.apiUrl")
        ?: localProperties.getProperty("void.backendUrl")
        ?: System.getenv("EI_VOID_API_URL")
        ?: System.getenv("VOID_BACKEND_URL")
        ?: ""
// Clerk Account Portal, where a user links GitHub to their Clerk account.
val clerkAccountPortalUrl: String =
    localProperties.getProperty("clerk.accountPortalUrl")
        ?: System.getenv("CLERK_ACCOUNT_PORTAL_URL")
        ?: ""

// Release signing. The keystore lives outside the repository; point at it from
// local.properties, from android/keystore.properties (what the release
// workflow writes), or from the environment. Never commit any of these.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseKeystorePath: String? =
    localProperties.getProperty("release.keystorePath")
        ?: keystoreProperties.getProperty("storeFile")
        ?: System.getenv("VOID_KEYSTORE_PATH")
val releaseStorePassword: String? =
    localProperties.getProperty("release.keystorePassword")
        ?: keystoreProperties.getProperty("storePassword")
        ?: System.getenv("VOID_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? =
    localProperties.getProperty("release.keyAlias")
        ?: keystoreProperties.getProperty("keyAlias")
        ?: System.getenv("VOID_KEY_ALIAS")
val releaseKeyPassword: String? =
    localProperties.getProperty("release.keyPassword")
        ?: keystoreProperties.getProperty("keyPassword")
        ?: System.getenv("VOID_KEY_PASSWORD")

val hasReleaseSigning =
    releaseKeystorePath != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null &&
        file(releaseKeystorePath).exists()

android {
    namespace = "com.eivoid.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.eivoid.app"
        // Android 13. The app uses nothing newer than API 26 at runtime
        // (java.time, java.nio.file), so 33 covers the test device without
        // desugaring; raising it later only narrows support.
        minSdk = 33
        targetSdk = 35
        versionCode = 5
        versionName = "0.3.2"

        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "com.eivoid.app.HiltTestRunner"

        buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"$clerkPublishableKey\"")
        buildConfigField("String", "EI_VOID_API_URL", "\"$eiVoidApiUrl\"")
        buildConfigField("String", "CLERK_ACCOUNT_PORTAL_URL", "\"$clerkAccountPortalUrl\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Without a keystore the release build still assembles, unsigned,
            // so CI can check that R8 succeeds.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":designsystem"))
    implementation(libs.clerk.android.api)
    implementation(project(":core:common"))
    implementation(project(":core:ai"))
    implementation(project(":core:agent"))
    implementation(project(":core:security"))
    implementation(project(":core:database"))
    implementation(project(":core:git"))
    implementation(project(":core:workspace"))
    implementation(project(":core:reminders"))
    implementation(project(":feature:assistant"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:repos"))
    implementation(project(":feature:workspace"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:reminders"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // The root Clerk gate hosts AuthView directly when signed out.
    implementation(libs.clerk.android.ui)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.coroutines.test)
    // JGit is an implementation detail of :core:git; the test builds a local remote.
    androidTestImplementation(libs.jgit)
    kspAndroidTest(libs.hilt.compiler)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
