import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")              // reads app/google-services.json (Firebase)
}

// ---- AWS team credentials: read from local.properties, which is NEVER uploaded to GitHub ----
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun localProp(name: String): String = localProperties.getProperty(name, "").trim()

android {
    namespace = "com.example.sufraseller"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.sufraseller"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AWS S3 settings -> usable in Kotlin as BuildConfig.AWS_ACCESS_KEY, etc.
        buildConfigField("String", "AWS_ACCESS_KEY", "\"${localProp("aws.accessKey")}\"")
        buildConfigField("String", "AWS_SECRET_KEY", "\"${localProp("aws.secretKey")}\"")
        buildConfigField("String", "AWS_SESSION_TOKEN", "\"${localProp("aws.sessionToken")}\"")
        buildConfigField("String", "AWS_REGION", "\"${localProp("aws.region")}\"")
        buildConfigField("String", "AWS_BUCKET", "\"${localProp("aws.bucket")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true      // ActivityLoginBinding etc.
        buildConfig = true      // BuildConfig.AWS_... fields above
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Firebase (Milestone 1)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")

    // AWS S3 for profile photos (Milestone 2)
    implementation("com.amazonaws:aws-android-sdk-s3:2.81.0")

    // Coroutines: run uploads/downloads in the background (Milestone 2)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
