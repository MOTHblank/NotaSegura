# PaddleOCR's Android SDK uses ONNX Runtime and OpenCV through JNI/reflection boundaries.
-keep class com.paddle.ocr.** { *; }
-keep class ai.onnxruntime.** { *; }
