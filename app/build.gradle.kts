import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}
val signingFile = rootProject.file("signing.properties")
val signing = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use(::load) }
android {
    namespace = "com.poyal.perilog"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.poyal.perilog"
        minSdk = 31
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.5"
        testInstrumentationRunner = "com.poyal.perilog.PerilogTestRunner"
    }
    signingConfigs {
        if (signingFile.exists()) create("personal") {
            storeFile = rootProject.file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingFile.exists()) signingConfig = signingConfigs.getByName("personal")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    if (providers.gradleProperty("captureScreenshots").isPresent) {
        sourceSets.getByName("androidTest").java.srcDir("../docs/capture")
    }
    if (providers.gradleProperty("updateFixture").isPresent) {
        sourceSets.getByName("androidTest").assets.srcDir("../.tools/update-fixture")
        sourceSets.getByName("androidTest").java.srcDir("src/updateTest/java")
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.12.00"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
