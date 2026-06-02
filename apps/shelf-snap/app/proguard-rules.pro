# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep data classes used by Gson
-keepclassmembers class com.shelfsnap.app.** {
    <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
