# Add project specific ProGuard rules here.
# Keep models for Gson serialization/deserialization
-keep class com.macrorecorder.app.model.** { *; }
-keepclassmembers class com.macrorecorder.app.model.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
