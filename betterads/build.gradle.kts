plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.betterads"

    defaultConfig {
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.toolingpreview)
    debugImplementation(libs.compose.uitooling)

    implementation(libs.coil.compose)
    // Not always present in host catalogs — keep explicit coordinates.
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(libs.coroutines.android)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
