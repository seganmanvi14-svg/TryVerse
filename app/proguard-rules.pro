# =====================================================================
# proguard-rules.pro = rules for SHRINKING the release app.
# In the release build, R8 (Android's shrinker) removes unused code and renames classes to short names,
# so the APK is smaller and harder to copy. These rules say what must NOT be removed or renamed.
# =====================================================================
# TryVerse release build rules.
# Firebase, ML Kit and CameraX ship their own rules, so nothing extra is needed for them.
# Remove debug logging from release builds.
# Deletes Log.v / Log.d / Log.i debug messages from the release app (they could leak details)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Razorpay checkout (rules from Razorpay's Android integration guide)
# Razorpay's payment page talks to Android through these JavaScript methods, so keep them
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepattributes *Annotation*
-dontwarn com.razorpay.**
-keep class com.razorpay.** {*;}
-optimizations !method/inlining/*
# Keep our onPaymentSuccess / onPaymentError methods, Razorpay calls them by name
-keepclasseswithmembers class * {
    public void onPayment*(...);
}
