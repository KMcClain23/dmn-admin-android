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

/**
 * RELEASE SIGNING, read from the same git-ignored local.properties the Supabase
 * keys come from.
 *
 * Absent is NOT fatal, following the precedent above: a fresh clone still
 * configures, debug still builds, and `assembleRelease` still produces an
 * artifact — an UNSIGNED one, which is the right failure. What must not happen
 * is that an unsigned artifact gets published, so the publish tasks below refuse
 * outright and say why. A build that dies at configuration time tells whoever
 * cloned it nothing about what to do next.
 */
val keystorePath: String = secret("KEYSTORE_FILE")
val keystore: File? = keystorePath.takeIf { it.isNotBlank() }?.let { path ->
    // Absolute or relative to the repo root; both are reasonable things to write
    // in local.properties and neither should silently mean the other.
    File(path).takeIf { it.isAbsolute } ?: rootProject.file(path)
}
val missingSigningBits: List<String> = buildList {
    if (keystore == null) add("KEYSTORE_FILE")
    else if (!keystore.exists()) add("KEYSTORE_FILE (points at ${keystore.path}, which does not exist)")
    if (secret("KEYSTORE_PASSWORD").isBlank()) add("KEYSTORE_PASSWORD")
    if (secret("KEY_ALIAS").isBlank()) add("KEY_ALIAS")
    if (secret("KEY_PASSWORD").isBlank()) add("KEY_PASSWORD")
}
val canSignRelease: Boolean = missingSigningBits.isEmpty()

/**
 * versionCode, derived from the commit count, and NOT TRUSTED ON ITS OWN.
 *
 * Play rejects a duplicate or LOWER versionCode permanently — a burned number is
 * burned for the life of the app — and `git rev-list --count` is monotonic only
 * while history is append-only. A rebase or a squash reduces it. So the derived
 * number is checked against a floor recorded in the checked-in version.properties
 * before anything may be published.
 *
 * When git cannot answer (a source zip, a build container with no .git) the
 * build still works, using the floor plus one so the artifact is installable.
 * But the publish tasks REFUSE in that state: a number that cannot be derived
 * reproducibly is not one to burn.
 */
val versionProps = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val lastPublishedVersionCode: Int =
    versionProps.getProperty("lastPublishedVersionCode")?.trim()?.toIntOrNull() ?: 0

// providers.exec, not ProcessBuilder: the configuration cache forbids starting a
// process at configuration time, and rejects the build outright rather than
// quietly disabling itself. runCatching still wraps it, because a machine with
// no git at all throws rather than returning a non-zero exit.
val gitCommitCount: Int? = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        workingDir = rootProject.projectDir
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toIntOrNull()
}.getOrNull()

val derivedVersionCode: Int = gitCommitCount ?: (lastPublishedVersionCode + 1)

android {
    namespace = "com.dmnarration.admin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dmnarration.admin"
        minSdk = 26
        targetSdk = 36
        versionCode = derivedVersionCode
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
    }

    signingConfigs {
        // Created only when every part is present. A half-configured signingConfig
        // fails at task execution with a message about a null password, which
        // reads as a Gradle bug rather than as "you have not set up signing".
        if (canSignRelease) {
            create("release") {
                storeFile = keystore
                storePassword = secret("KEYSTORE_PASSWORD")
                keyAlias = secret("KEY_ALIAS")
                keyPassword = secret("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Null when signing is not configured — deliberately, so the build
            // still produces an artifact and the refusal happens at publish time
            // with an explanation.
            signingConfig = signingConfigs.findByName("release")

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

    // ReleaseSecretsGuardTest asks git what is TRACKED, which is not a file
    // Gradle can see either — and the failure mode is identical: staging a
    // keystore changed nothing Gradle watched, so the task stayed up to date and
    // the guard silently did not run. Verified, not assumed: with a fake
    // keystore and a KEYSTORE_PASSWORD= line both staged, the run reported
    // 3 tests / 0 failures until --rerun-tasks forced it, and then correctly
    // reported 2 failures naming both files.
    //
    // The index is what changes when something is staged or committed, so it is
    // the right input for a guard whose subject is "what is in git".
    val gitIndex = rootProject.file(".git/index")
    if (gitIndex.exists()) {
        inputs.file(gitIndex)
            .withPropertyName("gitIndex")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}


// ── Release gates and publish tasks ─────────────────────────────────────────
//
// Nothing here changes what `assembleRelease` does. It still builds, still
// produces an artifact, and produces an UNSIGNED one when signing is not set up.
// These tasks are the difference between building a release and PUBLISHING one.

/**
 * Refuses to publish an unsigned artifact, and says exactly what is missing.
 *
 * "Unsigned" is the right outcome for a fresh clone. It is the wrong outcome for
 * an upload, and the two are told apart here rather than by whoever is holding
 * the phone.
 */
val requireReleaseSigning = tasks.register("requireReleaseSigning") {
    group = "publishing"
    description = "Fails unless release signing is fully configured in local.properties."
    // Hoisted into locals: a doLast that reads a script-level val captures the
    // script object, which the configuration cache cannot serialise.
    val signed = canSignRelease
    val missing = missingSigningBits
    doLast {
        if (!signed) {
            throw GradleException(
                buildString {
                    appendLine("This release is UNSIGNED and cannot be published.")
                    appendLine()
                    appendLine("Missing from local.properties: ${missing.joinToString(", ")}")
                    appendLine()
                    appendLine("local.properties is git-ignored, and is where the Supabase keys already live.")
                    appendLine("Add:")
                    appendLine("  KEYSTORE_FILE=/absolute/path/to/upload-keystore.jks")
                    appendLine("  KEYSTORE_PASSWORD=...")
                    appendLine("  KEY_ALIAS=...")
                    appendLine("  KEY_PASSWORD=...")
                    appendLine()
                    appendLine("Debug builds are unaffected and a fresh clone still configures —")
                    appendLine("that is what absent-not-fatal means here. What is NOT allowed is a")
                    appendLine("release artifact that looks fine and is unsigned: Play rejects it, and")
                    appendLine("it would not be obvious why until then.")
                }
            )
        }
    }
}

/**
 * Refuses to burn a versionCode that is not provably higher than the last one
 * published.
 *
 * Play will not accept a duplicate or a lower number, ever, and there is no way
 * to release one again. The check is cheap; the mistake is permanent.
 */
val requireFreshVersionCode = tasks.register("requireFreshVersionCode") {
    group = "publishing"
    description = "Fails unless the derived versionCode exceeds the last published one."
    val fromGit = gitCommitCount
    val computed = derivedVersionCode
    val floor = lastPublishedVersionCode
    doLast {
        if (fromGit == null) {
            throw GradleException(
                buildString {
                    appendLine("versionCode could not be derived from git, so this build must not be published.")
                    appendLine()
                    appendLine("`git rev-list --count HEAD` did not answer. The build fell back to")
                    appendLine("${floor + 1}, which is fine for installing locally and NOT fine for")
                    appendLine("burning a Play version: a number that cannot be derived reproducibly")
                    appendLine("cannot be checked for monotonicity either.")
                }
            )
        }
        if (computed <= floor) {
            throw GradleException(
                buildString {
                    appendLine("versionCode $computed does not exceed the last published $floor.")
                    appendLine()
                    appendLine("The commit count went DOWN or stayed level, which a rebase or a squash")
                    appendLine("will do. Play rejects duplicate and lower versionCodes permanently.")
                    appendLine()
                    appendLine("DO NOT raise lastPublishedVersionCode in version.properties to make this")
                    appendLine("pass — that is the guard working. Add a commit, or if history really was")
                    appendLine("rewritten, decide deliberately what the next number should be.")
                }
            )
        }
        logger.lifecycle("versionCode $computed (last published $floor) — ok to publish.")
    }
}

// A RELEASE BUILD MUST NOT SILENTLY EMIT AN UNSIGNED ARTIFACT.
//
// Configuration stays absent-not-fatal — a fresh clone configures, and debug
// builds and runs with no keystore anywhere. But assembleRelease and
// bundleRelease themselves now fail when signing is missing, rather than
// producing a file that looks finished and is rejected by Play much later, by
// which time the cause is far away from the effect.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(requireReleaseSigning)
}

/** The Play artifact. Signed .aab. */
val bundleForPlay = tasks.register("bundleForPlay") {
    group = "publishing"
    description = "Signed .aab for Play internal testing."
    dependsOn(requireReleaseSigning, requireFreshVersionCode, "bundleRelease")
    val outDir = layout.buildDirectory.dir("outputs/bundle/release")
    doLast {
        logger.lifecycle("AAB: ${outDir.get().asFile.path}")
    }
}

/** The Firebase artifact. Signed .apk — App Distribution does not take an .aab. */
val assembleForFirebase = tasks.register("assembleForFirebase") {
    group = "publishing"
    description = "Signed .apk for Firebase App Distribution."
    dependsOn(requireReleaseSigning, requireFreshVersionCode, "assembleRelease")
    val outDir = layout.buildDirectory.dir("outputs/apk/release")
    doLast {
        logger.lifecycle("APK: ${outDir.get().asFile.path}")
    }
}

/**
 * Build the signed .apk and upload it. One command.
 *
 * WHY THE CLI AND NOT THE GRADLE PLUGIN. com.google.firebase.appdistribution
 * 5.1.1 fails to apply on this project: it reaches for AGP's legacy
 * `AppExtension`, which AGP 9 removed. The exact error is
 * "Extension of type 'AppExtension' does not exist". Pinning AGP back to keep a
 * distribution plugin happy would be the tail wagging the dog, and this build
 * file already carries notes about AGP 9's other removals.
 *
 * The CLI does the same job, is what the Firebase docs treat as primary, and
 * couples nothing: no plugin, no Firebase SDK, nothing in the binary.
 *
 * Needs, in local.properties:
 *   FIREBASE_APP_ID=1:1234567890:android:abcdef
 *   FIREBASE_TESTER_GROUP=dmn-admin-testers      (optional; this is the default)
 * and authentication, either:
 *   GOOGLE_APPLICATION_CREDENTIALS pointing at a service-account json, or
 *   `firebase login` already done on this machine.
 */
val publishToFirebase = tasks.register("publishToFirebase") {
    group = "publishing"
    description = "Builds the signed .apk and uploads it to Firebase App Distribution."
    dependsOn(assembleForFirebase)

    val appId = secret("FIREBASE_APP_ID")
    val testerGroup = secret("FIREBASE_TESTER_GROUP").ifBlank { "dmn-admin-testers" }
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    val notes = "versionCode $derivedVersionCode"

    doLast {
        if (appId.isBlank()) {
            throw GradleException(
                buildString {
                    appendLine("FIREBASE_APP_ID is not set in local.properties, so there is nothing to upload to.")
                    appendLine()
                    appendLine("The signed .apk WAS built and is in ${apkDir.get().asFile.path} —")
                    appendLine("it can be side-loaded or uploaded by hand in the meantime.")
                    appendLine()
                    appendLine("Add:  FIREBASE_APP_ID=1:1234567890:android:abcdef")
                }
            )
        }
        val apk = apkDir.get().asFile.listFiles { f -> f.extension == "apk" }?.minByOrNull { it.name }
            ?: throw GradleException("no .apk found in ${apkDir.get().asFile.path}")

        // firebase-tools is firebase.cmd on Windows and firebase elsewhere.
        val exe = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "firebase.cmd"
        } else {
            "firebase"
        }

        val proc = ProcessBuilder(
            exe, "appdistribution:distribute", apk.path,
            "--app", appId,
            "--groups", testerGroup,
            "--release-notes", notes,
        ).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        logger.lifecycle(output)
        if (code != 0) {
            throw GradleException(
                buildString {
                    appendLine("firebase appdistribution:distribute failed (exit $code).")
                    appendLine(output)
                    appendLine()
                    appendLine("The signed .apk is still at ${apk.path}.")
                    appendLine("Check that firebase-tools is installed (npm i -g firebase-tools) and that")
                    appendLine("either GOOGLE_APPLICATION_CREDENTIALS is set or `firebase login` has been run.")
                }
            )
        }
    }
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
