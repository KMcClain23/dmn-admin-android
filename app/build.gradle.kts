import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// AGP 9 has built-in Kotlin support and applies KGP itself, so
// org.jetbrains.kotlin.android is deliberately absent — applying it is now a
// hard error. The Compose and serialization compiler plugins still apply
// normally; they hook the compilation AGP owns.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Supabase credentials, read from the git-ignored local.properties.
 *
 * Empty rather than fatal when the file is absent, so a fresh clone still
 * builds and the failure surfaces once, with a readable message, in the DI
 * module that actually needs them (see SupabaseModule). A build that dies at
 * configuration time tells whoever cloned it nothing about what to do next.
 */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String = localProps.getProperty(key).orEmpty()

android {
    namespace = "com.dmnarration.admin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dmnarration.admin"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
    }

    buildTypes {
        release {
            // Left off for Stage 1. Hilt, Ktor and kotlinx-serialization all need
            // keep rules, and getting those wrong produces runtime failures that
            // look like data bugs. Turning R8 on is its own task, not a footnote
            // to a read-only board.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    testOptions {
        unitTests {
            // android.util.Log throws by default off-device, which killed the
            // coroutine inside BoardViewModel's failure branch before it could
            // set the error — so the refusal tests saw an unchanged state and
            // failed on their preconditions rather than on their subject.
            // Returning defaults lets the logging line be what it is: a log
            // line, not a control-flow hazard.
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// CredentialDestructionGuardTest reads main sources from disk at run time, which
// Gradle cannot see as an input — so a change that breaks the guard left the test
// task up to date and the guard silently did not run. Declaring the directory
// makes a source change re-run the tests that police it.
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/java")
        .withPropertyName("guardedSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
