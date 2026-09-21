# Retain annotation and generic-signature metadata required at runtime by Hilt
# (component injection) and Gson (generic type token resolution).
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Keep all app data classes so Gson field-based (de)serialisation survives R8.
# We keep both the class names and the fields to ensure Reflection-based
# deserialization works as expected for OpenAI responses and local persistence.
#
# This named `com.shelfsnap.app.data.model.**` until now — a copy-paste from Shelf Snap's
# proguard-rules.pro, which this app's is otherwise identical to. It kept nothing here: this app
# is com.twobits.pricedrop, and its Gson-parsed DTOs live under data.remote.dto, not data.model.
# Kept broad (all of data.**, not just data.model) rather than guessing the exact set, on the same
# principle as the shared-module line below: over-keeping costs APK size, under-keeping is a
# silent runtime crash nobody sees until a release build actually runs.
-keep class com.twobits.pricedrop.data.** { *; }

# Also keep any models in shared modules if they are used for JSON
-keep class com.twobits.core.** { *; }

# LiteRT-LM JNI bindings (com.google.ai.edge.litertlm, pulled in via :core:local-ai). The native
# side of `nativeCreateConversation` calls back into Kotlin via `GetMethodID`/`CallIntMethodV` on
# getters such as `SamplerConfig.getTopK()` rather than receiving them as plain arguments. R8
# strips or renames those getters, `GetMethodID` returns null, and native code has no way to
# detect that before using it:
#   JNI DETECTED ERROR IN APPLICATION: mid == null in call to CallIntMethodV
#     from long com.google.ai.edge.litertlm.LiteRtLmJni.nativeCreateConversation(...)
# Caught via a Scrybe device Debug Log export — same dependency, same missing rule, same crash
# shape, and `isMinifyEnabled` is only set for `release` here too, so this app's own debug builds
# cannot have shown it either.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** { *; }

# OkHttp / Okio optional integrations not bundled with the app.
-dontwarn okhttp3.**
-dontwarn okio.**
