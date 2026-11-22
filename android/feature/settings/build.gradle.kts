import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Discord OAuth is configuration, never a secret. The client id is the public
// application id; the redirect URI is derived from the Ei Void web service.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val discordClientId: String =
    localProperties.getProperty("discord.clientId")
        ?: System.getenv("DISCORD_CLIENT_ID")
        ?: ""
val eiVoidApiUrl: String =
    localProperties.getProperty("ei-void.apiUrl")
        ?: localProperties.getProperty("void.backendUrl")
        ?: System.getenv("EI_VOID_API_URL")
        ?: ""

android {
    namespace = "com.eivoid.feature.settings"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
        buildConfigField("String", "DISCORD_CLIENT_ID", "\"$discordClientId\"")
        buildConfigField("String", "EI_VOID_API_URL", "\"$eiVoidApiUrl\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // The experimental opt-in detector intermittently crashes the lint
        // worker with a metaspace OOM on this module. The compiler still
        // enforces opt-in annotations; lint keeps every other check.
        disable += setOf("UnsafeOptInUsageError", "UnsafeOptInUsageWarning")
    }
}

dependencies {
    implementation(project(":core:ai"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    implementation(project(":core:workspace"))
    implementation(project(":core:reminders"))
    implementation(project(":designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.coroutines.android)
    // The settings API exposes DataStore types.
    api(libs.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
