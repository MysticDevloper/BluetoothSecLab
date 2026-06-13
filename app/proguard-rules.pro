# Bluetooth Security Lab - ProGuard Rules
-keepattributes Signature
-keepattributes *Annotation*

# Keep model classes
-keep class com.bluetoothseclab.models.** { *; }
