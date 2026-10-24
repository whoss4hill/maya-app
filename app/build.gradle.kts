import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Non-secret app configuration.
// Resolution order: Gradle property (incl. ORG_GRADLE_PROJECT_* env, used by CI),
// then local.properties (developer machine, NOT committed), then plain env var.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun config(key: String): String =
    (findProperty(key) as? String)
        ?: localProps.getProperty(key)
        ?: System.getenv(key)
        ?: ""

// Fail fast: an APK/bundle without a valid public anon key and backend URL can never sign anyone in, so refuse to build it.
// Prints only what is wrong, never the key itself.
if (gradle.startParameter.taskNames.any { t -> listOf("assemble", "bundle", "install").any { t.contains(it, ignoreCase = true) } }) {
    val key = config("MAYA_SUPABASE_ANON_KEY")
    val sbUrl = config("MAYA_SUPABASE_URL")
    val backend = config("MAYA_BACKEND_URL")
    require(backend.startsWith("https://")) { "MAYA_BACKEND_URL must be set to an https URL" }
    require(sbUrl.startsWith("https://") && sbUrl.endsWith(".supabase.co")) { "MAYA_SUPABASE_URL must be set (https://<ref>.supabase.co)" }
    val parts = key.split(".")
    require(parts.size == 3 && key.startsWith("eyJ")) { "MAYA_SUPABASE_ANON_KEY is missing or not a JWT. Set the public anon key (Actions variable, local.properties or ORG_GRADLE_PROJECT_MAYA_SUPABASE_ANON_KEY)." }
    val payload = String(Base64.getUrlDecoder().decode(parts[1].padEnd((parts[1].length + 3) / 4 * 4, '=')))
    val ref = sbUrl.removePrefix("https://").substringBefore(".")
    require(payload.contains("\"role\":\"anon\"")) { "MAYA_SUPABASE_ANON_KEY is not an anon-role key (never use a service-role key in the app)" }
    require(payload.contains("\"ref\":\"$ref\"")) { "MAYA_SUPABASE_ANON_KEY belongs to a different Supabase project than MAYA_SUPABASE_URL" }
}

android {
    namespace = "com.maya.agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.maya.agent"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // MAYA_SUPABASE_ANON_KEY: public anon key only - NEVER the service-role key.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") } // phones only; drops x86 Vosk/JNA libs
        buildConfigField("String", "BACKEND_URL", "\"${config("MAYA_BACKEND_URL")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${config("MAYA_SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${config("MAYA_SUPABASE_ANON_KEY")}\"")
    }

    signingConfigs {
        create("maya") {
            // Permanent signing identity (keystore lives outside the repo;
            // CI gets it via secrets). When absent, falls back to default
            // debug signing so contributors can still build.
            val ksPath = config("MAYA_KEYSTORE_PATH")
            if (ksPath.isNotBlank()) {
                storeFile = file(ksPath)
                storePassword = config("MAYA_KEYSTORE_PASSWORD")
                keyAlias = config("MAYA_KEY_ALIAS").ifBlank { "maya" }
                keyPassword = config("MAYA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        val mayaSigning = signingConfigs.getByName("maya")
        debug {
            if (config("MAYA_KEYSTORE_PATH").isNotBlank()) signingConfig = mayaSigning
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (config("MAYA_KEYSTORE_PATH").isNotBlank()) signingConfig = mayaSigning
        }
    }

    lint { abortOnError = false }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Offline wake word (Apache-2.0). Model is downloaded only after in-app consent.
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// UI interaction tests (Robolectric + Compose test). Test-only; nothing here ships in the APK.
android { testOptions { unitTests { isIncludeAndroidResources = true } } }
dependencies {
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
