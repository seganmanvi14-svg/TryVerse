# TryVerse release build rules.
# Firebase, ML Kit and CameraX ship their own rules, so nothing extra is needed for them.
# Remove debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Razorpay checkout (rules from Razorpay's Android integration guide)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepattributes *Annotation*
-dontwarn com.razorpay.**
-keep class com.razorpay.** {*;}
-optimizations !method/inlining/*
-keepclasseswithmembers class * {
    public void onPayment*(...);
}

# osmdroid (maps)
-dontwarn org.osmdroid.**
