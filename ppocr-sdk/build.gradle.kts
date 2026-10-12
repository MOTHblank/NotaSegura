import java.io.File
import java.net.URI
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.paddle.ocr"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        release {
            consumerProguardFiles("proguard-rules.pro")
        }
    }

}

kotlin {
    jvmToolchain(21)
}


/**
 * Download the pinned OCR assets without capturing the enclosing Gradle script.
 * Typed task properties are compatible with the configuration cache and allow
 * Android's variant API to track the generated assets as task outputs.
 */
abstract class PreparePpOcrModelsTask : DefaultTask() {
    @get:Input
    abstract val modelRevision: Property<String>

    @get:Internal
    abstract val modelCacheDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepareModels() {
        data class ModelAsset(
            val relativePath: String,
            val url: String,
            val bytes: Long,
            val sha256: String
        )

        val assets = listOf(
            ModelAsset(
                relativePath = "det/inference.onnx",
                url = "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx/resolve/28fe5895c24fd108c19eb3e8479f4ab385fbfc62/inference.onnx",
                bytes = 9_880_512L,
                sha256 = "d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e"
            ),
            ModelAsset(
                relativePath = "rec/inference.onnx",
                url = "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec_onnx/resolve/b8f84f0b80c529de40b4fbb3544b84fa7233a513/inference.onnx",
                bytes = 21_159_378L,
                sha256 = "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"
            ),
            ModelAsset(
                relativePath = "rec/inference.yml",
                url = "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec_onnx/resolve/b8f84f0b80c529de40b4fbb3544b84fa7233a513/inference.yml",
                bytes = 150_579L,
                sha256 = "ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"
            )
        )

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }

        fun isValid(file: File, asset: ModelAsset): Boolean =
            file.isFile &&
                file.length() == asset.bytes &&
                sha256(file).equals(asset.sha256, ignoreCase = true)

        fun download(asset: ModelAsset, destination: File) {
            if (isValid(destination, asset)) return

            destination.parentFile.mkdirs()
            val partial = File(destination.parentFile, destination.name + ".part")
            partial.delete()

            logger.lifecycle("Downloading PP-OCR model asset: ${asset.relativePath}")
            val connection = URI(asset.url).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 180_000
                setRequestProperty("User-Agent", "NotaSegura-build/1")
            }

            try {
                connection.getInputStream().buffered().use { input ->
                    partial.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }

                if (!isValid(partial, asset)) {
                    throw GradleException(
                        "Integrity check failed for ${asset.relativePath}; refusing to package the model."
                    )
                }

                destination.delete()
                if (!partial.renameTo(destination)) {
                    partial.copyTo(destination, overwrite = true)
                    partial.delete()
                }
            } catch (error: Throwable) {
                partial.delete()
                throw error
            }
        }

        val cacheRoot = modelCacheDirectory.get().asFile
        val packagedRoot = outputDirectory.get().asFile.resolve("models")
        packagedRoot.deleteRecursively()

        assets.forEach { asset ->
            val cached = cacheRoot.resolve(asset.relativePath)
            download(asset, cached)

            val packaged = packagedRoot.resolve(asset.relativePath)
            packaged.parentFile.mkdirs()
            cached.copyTo(packaged, overwrite = true)
        }
    }
}

val preparePpOcrModels = tasks.register<PreparePpOcrModelsTask>("preparePpOcrModels") {
    group = "build setup"
    description = "Downloads and verifies the pinned PP-OCRv6_small ONNX models for packaging."

    modelRevision.set("2026-06-pinned")
    modelCacheDirectory.set(
        rootProject.layout.projectDirectory.dir(".gradle/ppocr-models/ppocrv6-small")
    )
    outputDirectory.set(layout.buildDirectory.dir("generated/ppocrAssets"))
}

// Register generated assets as task outputs rather than a static source folder.
// AGP will run preparePpOcrModels before packaging assets for each variant.
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            preparePpOcrModels,
            PreparePpOcrModelsTask::outputDirectory
        )
    }
}

dependencies {
    implementation(libs.onnxruntime.android)
    implementation(libs.opencv.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}
