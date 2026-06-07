# Retain annotation and generic-signature metadata required at runtime by Hilt
# (component injection) and Gson (generic type token resolution).
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Keep all app data classes so Gson field-based (de)serialisation survives R8.
-keepclassmembers class com.shelfsnap.app.** {
    <fields>;
}

# OkHttp / Okio optional integrations not bundled with the app.
-dontwarn okhttp3.**
-dontwarn okio.**
