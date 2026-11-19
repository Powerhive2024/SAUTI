# LiteRT-LM uses JNI + reflection (Gson) for messages; keep its classes.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
