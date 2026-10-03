# Third-party licenses

NotaSegura itself is licensed under **GPL-3.0-only**. Third-party software, models, libraries, and other components retain their respective licenses and are not relicensed by this repository.

Notable components include:

- **PaddleOCR / PP-OCRv6_small** — Apache License 2.0.
  Vendored Android SDK source, model provenance, and license information are documented under `ppocr-sdk/`.
- **ONNX Runtime** — MIT License.
  The corresponding license text is included at `ppocr-sdk/LICENSE-ONNXRuntime.txt`.
- **OpenCV** — Apache License 2.0.
  The corresponding license text is included at `ppocr-sdk/LICENSE-OpenCV.txt`.
- **AndroidX, Jetpack Compose, Room, WorkManager, Coil, Kotlin coroutines, and other Gradle dependencies** retain the licenses published by their respective copyright holders.

For the PP-OCR integration specifically, see:

- `ppocr-sdk/THIRD_PARTY_NOTICES.md`
- `ppocr-sdk/LICENSE-PaddleOCR.txt`
- `ppocr-sdk/LICENSE-ONNXRuntime.txt`
- `ppocr-sdk/LICENSE-OpenCV.txt`

When redistributing NotaSegura or a derivative, preserve all license and attribution notices required by the applicable third-party licenses.
