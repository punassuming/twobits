# Sherpa-ONNX JNI bindings — native methods accessed by name at runtime
-keep class com.k2fsa.sherpa.** { *; }

# Retain annotation metadata required by Hilt's runtime component verification
-keepattributes *Annotation*

# Suppress warnings for optional OkHttp TLS provider integrations
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# MediaPipe LLM inference (tasks-genai) references protobuf annotation types that
# are not present in the protobuf-lite runtime shipped with the library. These are
# compile-time annotations only — no runtime behaviour depends on them.
-dontwarn com.google.protobuf.Internal$ProtoMethodMayReturnNull
-dontwarn com.google.protobuf.Internal$ProtoNonnullApi
-dontwarn com.google.protobuf.ProtoField
-dontwarn com.google.protobuf.ProtoPresenceBits
-dontwarn com.google.protobuf.ProtoPresenceCheckedField
