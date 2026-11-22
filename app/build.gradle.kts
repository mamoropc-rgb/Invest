plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

// Resolve the pinned Robolectric SDKs through Gradle's normal dependency cache.
// Tests then use local jars, avoiding a second network resolver inside the test JVM.
val robolectricSdk14 by configurations.creating
val robolectricSdk15 by configurations.creating
val prepareRobolectricSdk by tasks.registering(Sync::class) {
    from(robolectricSdk14,robolectricSdk15)
    into(layout.buildDirectory.dir("robolectric-sdk"))
}

android {
    namespace = "com.mamoro.investments"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mamoro.investments"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.0.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val storePath = providers.environmentVariable("INVESTMENTS_KEYSTORE_FILE").orNull
    val storePass = providers.environmentVariable("INVESTMENTS_STORE_PASSWORD").orNull
    val keyAliasValue = providers.environmentVariable("INVESTMENTS_KEY_ALIAS").orNull
    val keyPass = providers.environmentVariable("INVESTMENTS_KEY_PASSWORD").orNull
    val hasSigning = listOf(storePath, storePass, keyAliasValue, keyPass).all { !it.isNullOrBlank() }

    signingConfigs {
        create("privateRelease") {
            if (hasSigning) {
                storeFile = file(requireNotNull(storePath))
                storePassword = storePass
                keyAlias = keyAliasValue
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasSigning) signingConfig = signingConfigs.getByName("privateRelease")
        }
        getByName("debug") { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            val preparedSdk = providers.environmentVariable("ROBOLECTRIC_SDK_DIR").orNull
            if (preparedSdk == null) it.dependsOn(prepareRobolectricSdk)
            it.systemProperty("robolectric.dependency.dir", preparedSdk ?: layout.buildDirectory.dir("robolectric-sdk").get().asFile.absolutePath)
            it.testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kapt { correctErrorTypes = true }

dependencies {
    add(robolectricSdk14.name,"org.robolectric:android-all-instrumented:14-robolectric-10818077-i7") { isTransitive = false }
    add(robolectricSdk15.name,"org.robolectric:android-all-instrumented:15-robolectric-12650502-i7") { isTransitive = false }
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation(composeBom)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
