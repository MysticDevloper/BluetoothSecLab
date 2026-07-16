# Bluetooth Security Lab - ProGuard Rules

# Keep model classes (used in serialization)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.bluetoothseclab.models.** { *; }

# Keep attack module results (returned via callbacks)
-keep class com.bluetoothseclab.attacks.** { *; }

# Suppress warnings for reflection-based Bluetooth API access
-dontwarn android.bluetooth.**
-dontwarn java.lang.reflect.Method

# Keep Bluetooth device methods accessed via reflection
-keepclassmembers class android.bluetooth.BluetoothDevice {
    private <methods>;
}

# Remove debug logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
