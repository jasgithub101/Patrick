import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.patrick.faceid"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.patrick.faceid"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-phase1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ONNX Runtime ships four ABIs (~135 MB of native libs). Keep only the emulator
        // (x86_64) and modern phones (arm64-v8a).
        ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    // Models are loaded from assets; storing them uncompressed avoids an inflate at load time.
    androidResources { noCompress += "onnx" }

    testOptions { unitTests.isReturnDefaultValues = true }

    // Real-face instrumented tests read the LFW eval set produced by tools/prepare_dataset.py.
    // It lives in the gitignored .cache/ and is never committed (public repo).
    sourceSets["androidTest"].assets.srcDir(rootProject.file(".cache/lfw/eval_small"))
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Export the Room schema so the database structure is reviewable and future migrations have a
// baseline. Committed under app/schemas/.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.onnxruntime.android)
    implementation(libs.mlkit.face.detection)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// ---------------------------------------------------------------------------------------------
// Model assets
//
// The recognition models are not committed to git. They are downloaded from the pinned
// InsightFace v0.7 release, checksum-verified, and exposed to the build as a generated assets
// directory, so they land in the APK at assets/models/<file>.
//
// Licence: InsightFace pretrained models are for NON-COMMERCIAL RESEARCH ONLY.
// ---------------------------------------------------------------------------------------------

abstract class FetchModelsTask : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val zipSha256: Property<String>
    /** Entry name inside the zip -> expected SHA-256 of the extracted file. */
    @get:Input abstract val expectedFiles: MapProperty<String, String>
    /** Download cache outside build/ so `clean` does not force a re-download. */
    @get:Internal abstract val cacheFile: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val zip = cacheFile.get().asFile
        if (!zip.isFile || sha256(zip) != zipSha256.get()) {
            zip.parentFile.mkdirs()
            val part = File(zip.path + ".part")
            logger.lifecycle("Downloading ${url.get()}")
            URI(url.get()).toURL().openStream().use { input ->
                part.outputStream().use { input.copyTo(it) }
            }
            val actual = sha256(part)
            if (actual != zipSha256.get()) {
                part.delete()
                throw GradleException("Model zip checksum mismatch: expected ${zipSha256.get()}, got $actual")
            }
            Files.move(part.toPath(), zip.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        val modelsDir = outputDir.get().asFile.resolve("models")
        modelsDir.deleteRecursively()
        modelsDir.mkdirs()
        ZipFile(zip).use { z ->
            for ((name, expected) in expectedFiles.get()) {
                val entry = z.getEntry(name) ?: throw GradleException("$name missing from ${zip.name}")
                val out = modelsDir.resolve(name)
                z.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                val actual = sha256(out)
                if (actual != expected) {
                    throw GradleException("$name checksum mismatch: expected $expected, got $actual")
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

val modelZipCache = rootProject.layout.projectDirectory.file(".cache/models/buffalo_sc.zip")

androidComponents {
    onVariants { variant ->
        val fetch = tasks.register<FetchModelsTask>(
            "fetch${variant.name.replaceFirstChar { it.uppercase() }}Models"
        ) {
            url.set("https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_sc.zip")
            zipSha256.set("57d31b56b6ffa911c8a73cfc1707c73cab76efe7f13b675a05223bf42de47c72")
            expectedFiles.set(
                mapOf(
                    "det_500m.onnx" to "5e4447f50245bbd7966bd6c0fa52938c61474a04ec7def48753668a9d8b4ea3a",
                    "w600k_mbf.onnx" to "9cc6e4a75f0e2bf0b1aed94578f144d15175f357bdc05e815e5c4a02b319eb4f",
                )
            )
            cacheFile.set(modelZipCache)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(fetch, FetchModelsTask::outputDir)
    }
}
