# ONNX Runtime uses JNI/reflection boundaries that R8 must preserve.
-keep class ai.onnxruntime.** { *; }
