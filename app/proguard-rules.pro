# FuelGo ProGuard rules (release build is unminified by default; rules kept
# minimal for when minification is enabled later).
-keepattributes Signature, *Annotation*

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.fuelgo.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
