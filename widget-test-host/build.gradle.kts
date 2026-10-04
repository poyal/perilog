plugins { id("com.android.application") }
android {
    namespace = "com.poyal.perilog.widgettest"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.poyal.perilog.widgettest"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
