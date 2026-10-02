# TryVerse release build rules.
# Firebase, ML Kit and CameraX ship their own rules, so nothing extra is needed for them.
# Remove debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
