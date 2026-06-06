# Sherpa-ONNX JNI bindings — native methods accessed by name at runtime
-keep class com.k2fsa.sherpa.** { *; }

# Retain annotation metadata required by Hilt's runtime component verification
-keepattributes *Annotation*

# Suppress warnings for optional OkHttp TLS provider integrations
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
