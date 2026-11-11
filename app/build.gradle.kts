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

// The OAuth App client id is public, but it is configuration, not source:
// it comes from local.properties (gitignored) or the environment.
val githubClientId: String =
    localProperties.getProperty("github.clientId")
        ?: System.getenv("GITHUB_CLIENT_ID")
        ?: ""

android {
    namespace = "com.voidapp.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.voidapp"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "com.voidapp.app.HiltTestRunner"

        // Set in local.properties as github.clientId=... (or in the environment).
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$githubClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:ai"))
    implementation(project(":core:agent"))
    implementation(project(":core:security"))
    implementation(project(":core:database"))
    implementation(project(":core:git"))
    implementation(project(":core:workspace"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:repos"))
    implementation(project(":feature:workspace"))
    implementation(project(":feature:settings"))

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
