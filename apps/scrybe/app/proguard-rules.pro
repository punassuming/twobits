# Sherpa-ONNX JNI bindings — native methods accessed by name at runtime
-keep class com.k2fsa.sherpa.** { *; }

# Retain annotation and generic-signature metadata required at runtime by Hilt,
# Retrofit (generic return-type resolution), and kotlinx-serialization.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

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

# kotlinx-serialization: keep generated $$serializer companions so R8 does not
# strip them when they are only reached via the serialization runtime's reflection.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** serializer(...);
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    INSTANCE;
}
-dontnote kotlinx.serialization.**

# commons-compress uses ServiceLoader to discover compressor/archiver factories.
# Keep the SPI implementations so model-file decompression works at runtime.
-keep class org.apache.commons.compress.compressors.** { *; }
-keep class org.apache.commons.compress.archivers.** { *; }

# commons-compress optional codec back-ends — not bundled with the app.
# XZ/LZMA, Zstandard, and Brotli support is only pulled in when those formats
# are actually used; the missing classes cause R8 to fail when they are absent.
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
