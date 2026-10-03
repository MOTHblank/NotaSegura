# PP-OCRv6_small integration

NotaSegura vendors the Android PP-OCR SDK source from PaddlePaddle/PaddleOCR and bundles the official
PP-OCRv6_small detection and recognition ONNX models at build time.

- PaddleOCR source: https://github.com/PaddlePaddle/PaddleOCR
- Android integration: `deploy/ppocr-android/ppocr-sdk`
- Detection model: `PaddlePaddle/PP-OCRv6_small_det_onnx`, pinned revision `28fe5895c24fd108c19eb3e8479f4ab385fbfc62`
- Recognition model: `PaddlePaddle/PP-OCRv6_small_rec_onnx`, pinned revision `b8f84f0b80c529de40b4fbb3544b84fa7233a513`
- License: Apache License 2.0

The app performs OCR locally. Model downloads occur only on the developer/build machine; model files are
verified by byte count and SHA-256 and then packaged into the APK/AAB. There is no runtime model download.


## Runtime dependencies

- ONNX Runtime Android 1.27.0 (MIT): selected for Android 16 KB page-size compatibility.
  NotaSegura removes INTERNET and ACCESS_NETWORK_STATE from the merged manifest and disables
  ONNX Runtime telemetry when its environment is created.
- OpenCV Android 4.13.0 (Apache-2.0): official OpenCV.org Maven Central AAR.
