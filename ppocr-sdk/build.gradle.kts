import java.io.File
import java.net.URI
import java.security.MessageDigest

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

val generatedAssetsDir = layout.buildDirectory.dir("generated/ppocrAssets")
val modelCacheDir = rootProject.layout.projectDirectory.dir(".gradle/ppocr-models/ppocrv6-small")

val preparePpOcrModels by tasks.registering {
    group = "build setup"
    description = "Downloads and verifies the pinned PP-OCRv6_small ONNX models for packaging."

    inputs.property("ppocrModelRevision", "2026-06-pinned")
    outputs.dir(generatedAssetsDir)

    doLast {
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

        val cacheRoot = modelCacheDir.asFile
        val packagedRoot = generatedAssetsDir.get().asFile.resolve("models")
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

android.sourceSets.getByName("main").assets.srcDir(generatedAssetsDir)
tasks.named("preBuild").configure {
    dependsOn(preparePpOcrModels)
}

dependencies {
    implementation(libs.onnxruntime.android)
    implementation(libs.opencv.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}
