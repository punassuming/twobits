# Retain annotation and generic-signature metadata required at runtime by Hilt
# (component injection) and Gson (generic type token resolution).
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Keep all app data classes so Gson field-based (de)serialisation survives R8.
# We keep both the class names and the fields to ensure Reflection-based
# deserialization works as expected for OpenAI responses and local persistence.
-keep class com.shelfsnap.app.data.model.** { *; }

# Also keep any models in shared modules if they are used for JSON
-keep class com.twobits.core.** { *; }

# OkHttp / Okio optional integrations not bundled with the app.
-dontwarn okhttp3.**
-dontwarn okio.**
